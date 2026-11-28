package modules.query.domain.services;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import modules.shared.application.Primitives;

import modules.query.domain.entities.SelectSourceTableEntity;

/**
 * Como SQLyog: el resultado de un SELECT se puede editar si sale de una sola tabla y cada columna es una columna de la
 * tabla. Se acepta "SELECT * | t.* | col, t.col, ... FROM [db.]tabla [[AS] alias] [WHERE ...] [ORDER BY ...]
 * [LIMIT ...]"; cualquier otra cosa (JOIN, varias tablas, subconsulta en el FROM, DISTINCT, GROUP BY, HAVING, UNION,
 * expresiones o alias de columna, INTO) es de solo lectura. Los nombres pueden ir con ``, "" o [].
 * La clave primaria y los tipos de las columnas los comprueba despues quien edita, contra la tabla.
 */
public final class EditableSelectDetector {

    private static final String IDENTIFIER = "(?:`[^`]+`|\"[^\"]+\"|\\[[^\\]]+\\]|[A-Za-z_][A-Za-z0-9_$]*)";
    private static final String COLUMN = IDENTIFIER + "(?:\\s*\\.\\s*" + IDENTIFIER + "){0,2}";
    private static final String SELECT_LIST = "\\*|" + IDENTIFIER + "\\s*\\.\\s*\\*|" + COLUMN + "(?:\\s*,\\s*" + COLUMN + ")*";
    private static final Pattern SELECT_PATTERN = Pattern.compile(
        "^SELECT\\s+(?:" + SELECT_LIST + ")\\s+FROM\\s+"
            + "(?<first>" + IDENTIFIER + ")(?:\\s*\\.\\s*(?<second>" + IDENTIFIER + "))?"
            + "(?:\\s+(?:AS\\s+)?(?<alias>" + IDENTIFIER + "))?"
            + "(?<tail>\\s+(?:WHERE|ORDER\\s+BY|LIMIT|OFFSET|FOR\\s+UPDATE)\\b.*)?$",
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    private static final Pattern FORBIDDEN_TAIL_PATTERN = Pattern.compile(
        "\\b(?:UNION|GROUP\\s+BY|HAVING|INTO|INTERSECT|EXCEPT)\\b", Pattern.CASE_INSENSITIVE
    );
    private static final Pattern KEYWORD_ALIAS_PATTERN = Pattern.compile(
        "WHERE|ORDER|LIMIT|OFFSET|FOR|GROUP|HAVING|UNION|JOIN|INNER|LEFT|RIGHT|FULL|CROSS|NATURAL|STRAIGHT_JOIN|WINDOW|INTO",
        Pattern.CASE_INSENSITIVE
    );

    private EditableSelectDetector() {
    }

    public static EditableSelectDetector getInstance() {
        return new EditableSelectDetector();
    }

    /**
     * null si el SELECT no es editable.
     */
    public SelectSourceTableEntity getSourceTable(String sql) {
        if (sql == null) return null;
        String maskedSql = getMaskedSql(sql).strip();
        while (maskedSql.endsWith(";")) maskedSql = maskedSql.substring(0, maskedSql.length() - 1).stripTrailing();

        Matcher matcher = SELECT_PATTERN.matcher(maskedSql);
        if (!matcher.matches()) return null;
        String alias = matcher.group("alias");
        if (alias != null && KEYWORD_ALIAS_PATTERN.matcher(alias).matches()) return null;
        String tail = matcher.group("tail");
        if (tail != null && FORBIDDEN_TAIL_PATTERN.matcher(tail).find()) return null;

        String second = matcher.group("second");
        String databaseName = second == null ? "" : unquote(matcher.group("first"));
        String tableName = unquote(second == null ? matcher.group("first") : second);
        return SelectSourceTableEntity.fromPrimitives(Primitives.of(
            "databaseName", databaseName,
            "tableName", tableName
        ));
    }

    /**
     * Sin comentarios y con cada literal '...' cambiado por '' (su texto no cuenta: un WHERE con 'group by' no es un
     * GROUP BY). Los nombres entre ``, "" o [] se quedan.
     */
    private String getMaskedSql(String sql) {
        StringBuilder maskedSqlBuilder = new StringBuilder(sql.length());
        int length = sql.length();
        int i = 0;
        while (i < length) {
            char current = sql.charAt(i);
            char next = i + 1 < length ? sql.charAt(i + 1) : '\0';
            if ((current == '-' && next == '-') || current == '#') {
                while (i < length && sql.charAt(i) != '\n') i++;
                maskedSqlBuilder.append(' ');
                continue;
            }
            if (current == '/' && next == '*') {
                int end = sql.indexOf("*/", i + 2);
                i = end < 0 ? length : end + 2;
                maskedSqlBuilder.append(' ');
                continue;
            }
            if (current == '\'') {
                i = getStringEnd(sql, i);
                maskedSqlBuilder.append("''");
                continue;
            }
            if (current == '`' || current == '"' || current == '[') {
                char closing = current == '[' ? ']' : current;
                int end = sql.indexOf(closing, i + 1);
                int stop = end < 0 ? length : end + 1;
                maskedSqlBuilder.append(sql, i, stop);
                i = stop;
                continue;
            }
            maskedSqlBuilder.append(current);
            i++;
        }
        return maskedSqlBuilder.toString();
    }

    /**
     * La posicion justo despues de la comilla que cierra el literal que abre en start ('' y \' no cierran).
     */
    private int getStringEnd(String sql, int start) {
        int length = sql.length();
        int i = start + 1;
        while (i < length) {
            char stringChar = sql.charAt(i);
            if (stringChar == '\\') {
                i += 2;
                continue;
            }
            if (stringChar == '\'') {
                if (i + 1 < length && sql.charAt(i + 1) == '\'') {
                    i += 2;
                    continue;
                }
                return i + 1;
            }
            i++;
        }
        return length;
    }

    private String unquote(String identifier) {
        if (identifier.length() >= 2) {
            char first = identifier.charAt(0);
            char last = identifier.charAt(identifier.length() - 1);
            if ((first == '`' && last == '`') || (first == '"' && last == '"') || (first == '[' && last == ']')) {
                return identifier.substring(1, identifier.length() - 1);
            }
        }
        return identifier;
    }
}
