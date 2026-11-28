package modules.query.domain.services;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import modules.shared.domain.services.SqlDialect;
import modules.shared.application.Primitives;

import modules.query.domain.entities.SqlStatementEntity;

/**
 * Trocea el texto del editor en sentencias, como el cliente mysql: el delimitador (";" por defecto) solo corta fuera de
 * comillas ('', "", ``) y de comentarios (-- , #, /* *&#47;), y una linea "DELIMITER $$" lo cambia (rutinas y
 * triggers). Una sentencia que solo tiene comentarios no cuenta: MySQL la rechazaria con "Query was empty".
 * <p>
 * Con el dialecto de la conexion: en PostgreSQL y SQLite no hay DELIMITER, # no es comentario y la barra no escapa;
 * en PostgreSQL los cuerpos $$ ... $$ (o $tag$ ... $tag$) no se cortan; en SQLite un CREATE TRIGGER llega hasta su
 * "END;". En SQL Server, como en SSMS, solo corta una linea "GO" (el ; no) y los [nombres] van entre comillas.
 */
public final class SqlStatementSplitter {

    private static final String DEFAULT_DELIMITER = ";";
    private static final String DELIMITER_COMMAND = "delimiter";
    private static final Pattern GO_LINE_PATTERN = Pattern.compile("GO(?:[ \\t]+\\d+)?[ \\t]*(?:\\r?\\n|$)", Pattern.CASE_INSENSITIVE);
    private static final Pattern DOLLAR_TAG_PATTERN = Pattern.compile("\\$([A-Za-z_][A-Za-z0-9_]*)?\\$");
    private static final Pattern TRIGGER_START_PATTERN = Pattern.compile(
        "^(?:\\s|--[^\\n]*\\n|/\\*.*?\\*/)*CREATE\\s+(?:TEMP\\s+|TEMPORARY\\s+)?TRIGGER\\b",
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    private static final Pattern ENDS_WITH_END_PATTERN = Pattern.compile("(?:^|\\W)END\\s*$", Pattern.CASE_INSENSITIVE);

    private final SqlDialect sqlDialect;

    private SqlStatementSplitter(SqlDialect sqlDialect) {
        this.sqlDialect = sqlDialect;
    }

    /**
     * MySQL.
     */
    public static SqlStatementSplitter getInstance() {
        return new SqlStatementSplitter(SqlDialect.getInstance(SqlDialect.MYSQL));
    }

    public static SqlStatementSplitter getInstance(String dbEngine) {
        return new SqlStatementSplitter(SqlDialect.getInstance(dbEngine));
    }

    public List<SqlStatementEntity> getStatements(String text) {
        return getStatements(text, DEFAULT_DELIMITER);
    }

    /**
     * Empezando con un delimitador dado: para un fichero que se lee por trozos, el que dejo el trozo anterior (un
     * DELIMITER $$ sigue valiendo en el siguiente). Cada sentencia lleva el delimitador vigente al cerrarse.
     */
    public List<SqlStatementEntity> getStatements(String text, String startDelimiter) {
        List<SqlStatementEntity> statements = new ArrayList<>();
        String delimiter = startDelimiter == null || startDelimiter.isEmpty() ? DEFAULT_DELIMITER : startDelimiter;
        int statementStart = 0;
        boolean hasCode = false;
        int i = 0;
        int length = text.length();

        while (i < length) {
            char current = text.charAt(i);

            if (sqlDialect.isDelimiterCommand() && !hasCode && isLineStart(text, i, statementStart)) {
                int delimiterLineEnd = getDelimiterCommandEnd(text, i);
                if (delimiterLineEnd != -1) {
                    delimiter = getDelimiterValue(text, i, delimiterLineEnd);
                    i = delimiterLineEnd;
                    statementStart = i;
                    continue;
                }
            }

            if (sqlDialect.isGoBatchSeparator() && isLineStart(text, i, statementStart)) {
                Matcher goMatcher = GO_LINE_PATTERN.matcher(text);
                goMatcher.region(i, length);
                if (goMatcher.lookingAt()) {
                    addStatement(statements, text, statementStart, i, goMatcher.end(), hasCode, delimiter);
                    i = goMatcher.end();
                    statementStart = i;
                    hasCode = false;
                    continue;
                }
            }
            if (current == '[' && sqlDialect.isMssql()) {
                i = getQuotedEnd(text, i, ']');
                hasCode = true;
                continue;
            }
            if (current == '\'' || current == '"' || (current == '`' && sqlDialect.isMysql())) {
                i = getQuotedEnd(text, i, current);
                hasCode = true;
                continue;
            }
            if (current == '$' && sqlDialect.isDollarQuoting()) {
                int dollarQuotedEnd = getDollarQuotedEnd(text, i);
                if (dollarQuotedEnd != -1) {
                    i = dollarQuotedEnd;
                    hasCode = true;
                    continue;
                }
            }
            if (isLineComment(text, i)) {
                i = getLineEnd(text, i);
                continue;
            }
            if (current == '/' && i + 1 < length && text.charAt(i + 1) == '*') {
                int commentEnd = text.indexOf("*/", i + 2);
                i = commentEnd == -1 ? length : commentEnd + 2;
                continue;
            }
            if (!sqlDialect.isGoBatchSeparator() && text.startsWith(delimiter, i) && !isInsideTriggerBlock(text, statementStart, i)) {
                addStatement(statements, text, statementStart, i, i + delimiter.length(), hasCode, delimiter);
                i += delimiter.length();
                statementStart = i;
                hasCode = false;
                continue;
            }
            if (!Character.isWhitespace(current)) {
                hasCode = true;
            }
            i++;
        }
        addStatement(statements, text, statementStart, length, length, hasCode, delimiter);
        return statements;
    }

    /**
     * La sentencia en la que esta el cursor; si esta entre dos, la anterior (la que se acaba de escribir). null si no
     * hay ninguna.
     */
    public SqlStatementEntity getStatementAt(String text, int caretIndex) {
        SqlStatementEntity statementAtCaret = null;
        for (SqlStatementEntity statement : getStatements(text)) {
            if (statementAtCaret == null || statement.getStartIndex() <= caretIndex) {
                statementAtCaret = statement;
            }
            if (statement.getStartIndex() <= caretIndex && caretIndex <= statement.getEndIndex()) {
                return statement;
            }
        }
        return statementAtCaret;
    }

    private void addStatement(List<SqlStatementEntity> statements, String text, int start, int sqlEnd, int end, boolean hasCode, String delimiter) {
        if (!hasCode) return;
        String rawSql = text.substring(start, sqlEnd);
        int leadingSpaces = rawSql.length() - rawSql.stripLeading().length();
        statements.add(SqlStatementEntity.fromPrimitives(Primitives.of(
            "sql", rawSql.trim(),
            "startIndex", start + leadingSpaces,
            "endIndex", end,
            "delimiter", delimiter
        )));
    }

    /**
     * Solo hay espacios entre el inicio de la linea (o de la sentencia) y la posicion.
     */
    private boolean isLineStart(String text, int index, int statementStart) {
        for (int j = index - 1; j >= statementStart; j--) {
            char previous = text.charAt(j);
            if (previous == '\n') return true;
            if (!Character.isWhitespace(previous)) return false;
        }
        return true;
    }

    /**
     * Si en la posicion empieza "DELIMITER <x>", donde acaba esa linea; si no, -1.
     */
    private int getDelimiterCommandEnd(String text, int index) {
        int commandEnd = index + DELIMITER_COMMAND.length();
        if (commandEnd >= text.length()) return -1;
        if (!text.substring(index, commandEnd).toLowerCase(Locale.ROOT).equals(DELIMITER_COMMAND)) return -1;
        if (!Character.isWhitespace(text.charAt(commandEnd))) return -1;
        if (getDelimiterValue(text, index, getLineEnd(text, index)).isEmpty()) return -1;
        return getLineEnd(text, index);
    }

    private String getDelimiterValue(String text, int index, int lineEnd) {
        String value = text.substring(index + DELIMITER_COMMAND.length(), lineEnd).trim();
        int spaceIndex = value.indexOf(' ');
        return spaceIndex == -1 ? value : value.substring(0, spaceIndex);
    }

    /**
     * $$ o $tag$ en la posicion: donde acaba el bloque (tras el mismo tag); -1 si no empieza uno ($1 es un parametro).
     */
    private int getDollarQuotedEnd(String text, int index) {
        Matcher tagMatcher = DOLLAR_TAG_PATTERN.matcher(text);
        tagMatcher.region(index, text.length());
        if (!tagMatcher.lookingAt()) return -1;
        String tag = tagMatcher.group();
        int closingIndex = text.indexOf(tag, tagMatcher.end());
        return closingIndex == -1 ? text.length() : closingIndex + tag.length();
    }

    /**
     * SQLite: dentro de un CREATE TRIGGER, un ; solo corta si va tras el END del cuerpo.
     */
    private boolean isInsideTriggerBlock(String text, int statementStart, int delimiterIndex) {
        if (!sqlDialect.isTriggerBlock()) return false;
        String statementSoFar = text.substring(statementStart, delimiterIndex);
        if (!TRIGGER_START_PATTERN.matcher(statementSoFar).lookingAt()) return false;
        return !ENDS_WITH_END_PATTERN.matcher(statementSoFar).find();
    }

    /**
     * "-- " (con espacio o fin de linea detras, como MySQL) o "#" (solo en MySQL).
     */
    private boolean isLineComment(String text, int index) {
        char current = text.charAt(index);
        if (current == '#') return sqlDialect.isHashComment();
        if (current != '-' || index + 1 >= text.length() || text.charAt(index + 1) != '-') return false;
        return index + 2 >= text.length() || Character.isWhitespace(text.charAt(index + 2));
    }

    private int getLineEnd(String text, int index) {
        int lineEnd = text.indexOf('\n', index);
        return lineEnd == -1 ? text.length() : lineEnd;
    }

    /**
     * La posicion tras la comilla que cierra: la comilla doblada ('') sigue dentro y, solo en MySQL, \ escapa.
     */
    private int getQuotedEnd(String text, int index, char quote) {
        int i = index + 1;
        while (i < text.length()) {
            char current = text.charAt(i);
            if (current == '\\' && quote != '`' && sqlDialect.isMysql()) {
                i += 2;
                continue;
            }
            if (current == quote) {
                if (i + 1 < text.length() && text.charAt(i + 1) == quote) {
                    i += 2;
                    continue;
                }
                return i + 1;
            }
            i++;
        }
        return text.length();
    }
}
