package modules.shared.domain.services;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * Valores como literales de MySQL ('O\'Brien', NULL, 42, X'00FF'). Hace falta donde no hay sentencias preparadas: el
 * tunel HTTP (feature 13) manda el SQL como texto, asi que los ? se sustituyen aqui antes de enviarlo. Con
 * sql_mode NO_BACKSLASH_ESCAPES la barra no escapa: el tunel PHP fija sql_mode='' en cada sesion.
 */
public final class SqlLiteralQuoter {

    private static final char BACKSLASH = '\\';
    private static final char NUL = '\0';

    private final boolean isBackslashEscape;
    private final boolean isMssql;
    private final boolean isSqlite;

    private SqlLiteralQuoter(boolean isBackslashEscape, boolean isMssql, boolean isSqlite) {
        this.isBackslashEscape = isBackslashEscape;
        this.isMssql = isMssql;
        this.isSqlite = isSqlite;
    }

    /**
     * MySQL: la barra escapa ('it\'s').
     */
    public static SqlLiteralQuoter getInstance() {
        return new SqlLiteralQuoter(true, false, false);
    }

    /**
     * PostgreSQL usa literales estandar ('it''s', la barra es un caracter mas: standard_conforming_strings). SQL Server:
     * estandar con N delante (N'it''s', para no perder lo que no cabe en la pagina de codigos) y binarios 0x00ff.
     * SQLite (y el motor Ficheros, que es SQLite) va directo por JDBC o por el tunel PHP, que lee los literales con
     * barra como MySQL: un literal que valga en los dos no lleva barras (ver getSqliteLiteral).
     */
    public static SqlLiteralQuoter getInstance(String dbEngine) {
        boolean isMssql = SqlDialect.MSSQL.equals(dbEngine);
        boolean isSqlite = SqlDialect.getInstance(dbEngine).isSqlite();
        return new SqlLiteralQuoter(!SqlDialect.PGSQL.equals(dbEngine) && !isMssql, isMssql, isSqlite);
    }

    public String getLiteral(Object value) {
        if (value == null) return "NULL";
        if (value instanceof Boolean bool) return bool ? "1" : "0";
        if (value instanceof Integer || value instanceof Long || value instanceof Short || value instanceof Byte) {
            return String.valueOf(value);
        }
        if (value instanceof BigDecimal bigDecimal) return bigDecimal.toPlainString();
        if (value instanceof Double || value instanceof Float) {
            double number = ((Number) value).doubleValue();
            if (Double.isFinite(number)) return String.valueOf(value);
        }
        if (value instanceof byte[] bytes) {
            if (isMssql) return "0x" + HexFormat.of().formatHex(bytes);
            return bytes.length == 0 ? "''" : "X'" + HexFormat.of().formatHex(bytes) + "'";
        }
        if (isSqlite) return getSqliteLiteral(String.valueOf(value));
        return (isMssql ? "N'" : "'") + getEscaped(String.valueOf(value)) + "'";
    }

    /**
     * SQLite no escapa con barra ('x\' es un literal entero) y el tunel PHP si: un 'x\'' de estilo MySQL dejaria
     * escapar lo que sigue por JDBC directo, y un 'x\' estandar lo haria por el tunel. Por eso la comilla se dobla (vale
     * en los dos) y cada barra o NUL sale fuera de las comillas como char(92) / char(0): ('C:' || char(92) || 'tmp').
     */
    private String getSqliteLiteral(String value) {
        if (value.indexOf(BACKSLASH) == -1 && value.indexOf(NUL) == -1) return "'" + value.replace("'", "''") + "'";
        List<String> parts = new ArrayList<>();
        StringBuilder chunk = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (current != BACKSLASH && current != NUL) {
                chunk.append(current);
                continue;
            }
            if (!chunk.isEmpty()) parts.add("'" + chunk.toString().replace("'", "''") + "'");
            chunk.setLength(0);
            parts.add("char(" + (int) current + ")");
        }
        if (!chunk.isEmpty()) parts.add("'" + chunk.toString().replace("'", "''") + "'");
        return "(" + String.join(" || ", parts) + ")";
    }

    /**
     * El SQL con cada ? (fuera de literales, identificadores y comentarios) cambiado por el literal de su parametro.
     * @throws IllegalArgumentException si no coinciden el numero de ? y el de parametros
     */
    public String getInlined(String sql, List<Object> params) {
        StringBuilder inlinedSql = new StringBuilder(sql.length() + params.size() * 8);
        int paramIndex = 0;
        int length = sql.length();
        int i = 0;
        while (i < length) {
            char current = sql.charAt(i);
            if (current == '\'' || current == '"' || current == '`') {
                int end = getQuotedEnd(sql, i, current);
                inlinedSql.append(sql, i, end);
                i = end;
                continue;
            }
            if (isLineCommentStart(sql, i)) {
                int end = sql.indexOf('\n', i);
                end = end == -1 ? length : end;
                inlinedSql.append(sql, i, end);
                i = end;
                continue;
            }
            if (current == '/' && i + 1 < length && sql.charAt(i + 1) == '*') {
                int end = sql.indexOf("*/", i + 2);
                end = end == -1 ? length : end + 2;
                inlinedSql.append(sql, i, end);
                i = end;
                continue;
            }
            if (current == '?') {
                if (paramIndex >= params.size()) throw new IllegalArgumentException("more ? than params in: " + sql);
                inlinedSql.append(getLiteral(params.get(paramIndex++)));
                i++;
                continue;
            }
            inlinedSql.append(current);
            i++;
        }
        if (paramIndex != params.size()) throw new IllegalArgumentException("more params than ? in: " + sql);
        return inlinedSql.toString();
    }

    private String getEscaped(String value) {
        if (!isBackslashEscape) return value.replace("'", "''");
        StringBuilder escaped = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            switch (current) {
                case '\\' -> escaped.append("\\\\");
                case '\'' -> escaped.append("\\'");
                case '\0' -> escaped.append("\\0");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\u001a' -> escaped.append("\\Z");
                default -> escaped.append(current);
            }
        }
        return escaped.toString();
    }

    /**
     * Fin (exclusivo) de un literal o identificador entre comillas: comilla doblada o escapada con barra (no en `).
     */
    private int getQuotedEnd(String sql, int start, char quote) {
        int i = start + 1;
        while (i < sql.length()) {
            char current = sql.charAt(i);
            if (current == '\\' && quote != '`' && isBackslashEscape) {
                i += 2;
                continue;
            }
            if (current == quote) {
                if (i + 1 < sql.length() && sql.charAt(i + 1) == quote) {
                    i += 2;
                    continue;
                }
                return i + 1;
            }
            i++;
        }
        return sql.length();
    }

    private boolean isLineCommentStart(String sql, int i) {
        char current = sql.charAt(i);
        if (current == '#') return true;
        return current == '-' && i + 1 < sql.length() && sql.charAt(i + 1) == '-'
            && (i + 2 == sql.length() || Character.isWhitespace(sql.charAt(i + 2)));
    }
}
