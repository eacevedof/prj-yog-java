package modules.query.domain.services;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import modules.shared.application.Primitives;

import modules.query.domain.enums.SqlKeywordsEnum;
import modules.query.domain.entities.SqlCompletionContextEntity;

/**
 * Feature 41: entiende la sentencia que se escribe para autocompletar con sentido. Saca los alias de FROM, JOIN,
 * UPDATE e INTO (tambien las listas "FROM a x, b y") aunque el alias se defina despues del cursor
 * ("SELECT c.| FROM customers c"). Sin comentarios ni cadenas: un "from" dentro de un literal no cuenta.
 */
public final class SqlCompletionAnalyzer {

    private static final Pattern COMMENTS_AND_LITERALS_PATTERN = Pattern.compile(
        "'(?:[^'\\\\]|\\\\.|'')*'?|\"(?:[^\"\\\\]|\\\\.|\"\")*\"?|--[ \\t][^\\n]*|--$|#[^\\n]*|/\\*(?s:.*?)(?:\\*/|\\z)",
        Pattern.MULTILINE
    );
    private static final Pattern TOKEN_PATTERN = Pattern.compile("`(?:[^`]|``)*`|[\\w$]+|[.,()]");
    private static final Set<String> TABLE_KEYWORDS = Set.of("from", "join", "update", "into", "table", "straight_join");
    // tras FROM, una coma sigue la lista de tablas hasta la siguiente clausula
    private static final Set<String> FROM_LIST_ENDS = Set.of("where", "group", "order", "having", "limit", "union", "join", "on", "using", "set", "values", "select", "for", "lock", "window");

    private SqlCompletionAnalyzer() {
    }

    public static SqlCompletionAnalyzer getInstance() {
        return new SqlCompletionAnalyzer();
    }

    /**
     * statementSql: la sentencia entera donde esta el cursor; caretOffset: la posicion del cursor dentro de ella.
     */
    public SqlCompletionContextEntity getContext(String statementSql, int caretOffset) {
        int caret = Math.max(0, Math.min(caretOffset, statementSql.length()));
        String beforeCaret = statementSql.substring(0, caret);

        String prefix = getTrailingWord(beforeCaret);
        String beforePrefix = beforeCaret.substring(0, beforeCaret.length() - prefix.length());
        String qualifier = "";
        if (beforePrefix.endsWith(".")) {
            qualifier = getTrailingWord(beforePrefix.substring(0, beforePrefix.length() - 1));
        }

        List<String> tokensBeforeCaret = getTokens(getWithoutCommentsAndLiterals(beforePrefix));
        boolean isTableExpected = qualifier.isEmpty() && isAfterTableKeyword(tokensBeforeCaret);

        return SqlCompletionContextEntity.fromPrimitives(Primitives.of(
            "prefix", getUnquoted(prefix),
            "rawPrefixLength", prefix.length(),
            "qualifier", getUnquoted(qualifier),
            "tablesByAlias", getTablesByAlias(getTokens(getWithoutCommentsAndLiterals(statementSql))),
            "isTableExpected", isTableExpected
        ));
    }

    /**
     * La palabra que acaba justo en el cursor (letras, numeros, _ y $; o un `identificador` sin cerrar).
     */
    private String getTrailingWord(String text) {
        long backtickCount = text.chars().filter(character -> character == '`').count();
        // `identificador sin cerrar: lo que se esta escribiendo empieza en la ultima comilla invertida
        if (backtickCount % 2 == 1) {
            return text.substring(text.lastIndexOf('`'));
        }
        // `identificador` cerrado justo antes (el qualifier de `mi bd`.tabla)
        if (text.endsWith("`") && text.length() > 1) {
            int open = text.lastIndexOf('`', text.length() - 2);
            if (open != -1) return text.substring(open);
        }
        int start = text.length();
        while (start > 0 && isWordChar(text.charAt(start - 1))) {
            start--;
        }
        return text.substring(start);
    }

    private boolean isWordChar(char character) {
        return Character.isLetterOrDigit(character) || character == '_' || character == '$';
    }

    private boolean isAfterTableKeyword(List<String> tokens) {
        if (tokens.isEmpty()) return false;
        String last = tokens.getLast().toLowerCase(Locale.ROOT);
        if (TABLE_KEYWORDS.contains(last)) return true;
        if (!last.equals(",")) return false;
        // una coma dentro de la lista del FROM
        for (int i = tokens.size() - 2; i >= 0; i--) {
            String token = tokens.get(i).toLowerCase(Locale.ROOT);
            if (token.equals("from")) return true;
            if (FROM_LIST_ENDS.contains(token) || token.equals("(") || token.equals(")")) return false;
        }
        return false;
    }

    /**
     * tabla [AS] alias tras FROM / JOIN / UPDATE / INTO / coma de la lista del FROM. La tabla puede ir como bd.tabla.
     */
    private Map<String, String[]> getTablesByAlias(List<String> tokens) {
        Map<String, String[]> tablesByAlias = new LinkedHashMap<>();
        boolean isInFromList = false;
        for (int i = 0; i < tokens.size(); i++) {
            String token = tokens.get(i).toLowerCase(Locale.ROOT);
            boolean isTableStart = TABLE_KEYWORDS.contains(token) || (token.equals(",") && isInFromList);
            if (token.equals("from")) isInFromList = true;
            else if (FROM_LIST_ENDS.contains(token)) isInFromList = false;
            if (!isTableStart || i + 1 >= tokens.size()) continue;

            int next = i + 1;
            String first = tokens.get(next);
            if (!isIdentifier(first)) continue;
            String databaseName = "";
            String tableName = getUnquoted(first);
            if (next + 2 < tokens.size() && tokens.get(next + 1).equals(".") && isIdentifier(tokens.get(next + 2))) {
                databaseName = tableName;
                tableName = getUnquoted(tokens.get(next + 2));
                next += 2;
            }
            String[] table = {databaseName, tableName};
            tablesByAlias.putIfAbsent(tableName.toLowerCase(Locale.ROOT), table);

            int aliasIndex = next + 1;
            if (aliasIndex < tokens.size() && tokens.get(aliasIndex).equalsIgnoreCase("as")) aliasIndex++;
            if (aliasIndex < tokens.size() && isIdentifier(tokens.get(aliasIndex))
                && !SqlKeywordsEnum.CLAUSE_KEYWORDS.contains(tokens.get(aliasIndex).toLowerCase(Locale.ROOT))) {
                tablesByAlias.put(getUnquoted(tokens.get(aliasIndex)).toLowerCase(Locale.ROOT), table);
            }
        }
        return tablesByAlias;
    }

    private boolean isIdentifier(String token) {
        return token.startsWith("`") || (!token.isEmpty() && isWordChar(token.charAt(0)) && !Character.isDigit(token.charAt(0)));
    }

    private List<String> getTokens(String sql) {
        List<String> tokens = new ArrayList<>();
        Matcher matcher = TOKEN_PATTERN.matcher(sql);
        while (matcher.find()) {
            tokens.add(matcher.group());
        }
        return tokens;
    }

    private String getWithoutCommentsAndLiterals(String sql) {
        return COMMENTS_AND_LITERALS_PATTERN.matcher(sql).replaceAll(matchResult -> {
            char first = matchResult.group().charAt(0);
            return first == '\'' || first == '"' ? " 0 " : " ";
        });
    }

    private String getUnquoted(String identifier) {
        if (identifier.startsWith("`")) {
            String inner = identifier.substring(1);
            if (inner.endsWith("`")) inner = inner.substring(0, inner.length() - 1);
            return inner.replace("``", "`");
        }
        return identifier;
    }
}
