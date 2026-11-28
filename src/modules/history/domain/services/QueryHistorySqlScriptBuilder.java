package modules.history.domain.services;

import java.util.List;
import java.util.Map;

import modules.shared.domain.services.SqlDialect;
import modules.shared.domain.services.SqlIdentifierQuoter;
import static modules.shared.application.Primitives.getBool;
import static modules.shared.application.Primitives.getTrimmedString;

import modules.history.domain.enums.QueryHistoryConfigEnum;
import modules.history.domain.enums.QueryHistoryKeyEnum;

/**
 * El historial como script SQL para volver a lanzarlo y llegar a la misma foto: lo mas antiguo primero, cada sentencia
 * que se ejecuto bien tal cual y cerrada con ";", con su cabecera (id, fecha, conexion, BD, ms, filas) en un comentario
 * /* *&#47;. Todo lo que no es una sentencia ejecutable va comentado: las ejecuciones que fallaron (no cambiaron nada) y
 * las operaciones que no se relanzan como SQL (un fichero SQL, copiar o renombrar una BD). Si cambia la BD entre una
 * sentencia y la siguiente, un USE (MySQL y SQL Server) o, en los motores sin USE, un comentario.
 */
public final class QueryHistorySqlScriptBuilder {

    private static final String NEW_LINE = "\n";
    private static final String STATEMENT_END = ";";
    private static final String LINE_COMMENT = "--";
    private static final String HASH_COMMENT = "#";
    private static final String COMMENT_START = "/*";
    private static final String COMMENT_END = "*/";
    // un "*/" dentro del texto comentado cerraria el comentario antes de tiempo
    private static final String SAFE_COMMENT_END = "* /";

    private final SqlDialect sqlDialect;
    private final SqlIdentifierQuoter sqlIdentifierQuoter;

    private QueryHistorySqlScriptBuilder(String dbEngine) {
        this.sqlDialect = SqlDialect.getInstance(dbEngine);
        this.sqlIdentifierQuoter = SqlIdentifierQuoter.getInstance(dbEngine);
    }

    /**
     * dbEngine: el de la conexion donde se va a lanzar (decide como se cambia de BD).
     */
    public static QueryHistorySqlScriptBuilder getInstance(String dbEngine) {
        return new QueryHistorySqlScriptBuilder(dbEngine);
    }

    /**
     * historyRows: filas con las claves de QueryHistoryKeyEnum, ya en orden de ejecucion (id ascendente).
     */
    public String getScript(List<Map<String, Object>> historyRows, String exportedAt) {
        StringBuilder script = new StringBuilder(QueryHistoryConfigEnum.SCRIPT_HEADER_X_X.formatted(exportedAt, historyRows.size()));
        String currentDatabase = "";
        for (Map<String, Object> historyRow : historyRows) {
            String sql = getTrimmedString(historyRow.get(QueryHistoryKeyEnum.SQL_QUERY));
            String entryLabel = getEntryLabel(historyRow);
            script.append(NEW_LINE);
            if (getBool(historyRow.get(QueryHistoryKeyEnum.HAS_ERROR))) {
                script.append(getCommentedEntry(entryLabel + QueryHistoryConfigEnum.NOT_REPLAYED_ERROR, sql));
                continue;
            }
            if (!isReplayable(sql)) {
                script.append(getCommentedEntry(entryLabel + QueryHistoryConfigEnum.NOT_REPLAYED_OPERATION, sql));
                continue;
            }
            String databaseName = getTrimmedString(historyRow.get(QueryHistoryKeyEnum.DATABASE_NAME));
            if (!databaseName.isEmpty() && !databaseName.equals(currentDatabase)) {
                script.append(getDatabaseChange(databaseName)).append(NEW_LINE);
                currentDatabase = databaseName;
            }
            script.append(COMMENT_START).append(" ").append(getSafeCommentText(entryLabel)).append(" ").append(COMMENT_END).append(NEW_LINE)
                .append(getWithBlockComments(sql)).append(getStatementEnd(sql)).append(NEW_LINE);
        }
        return script.toString();
    }

    private String getEntryLabel(Map<String, Object> historyRow) {
        return QueryHistoryConfigEnum.ENTRY_X_X_X_X_X_X.formatted(
            getTrimmedString(historyRow.get(QueryHistoryKeyEnum.ID)),
            getTrimmedString(historyRow.get(QueryHistoryKeyEnum.EXECUTED_AT)),
            getTrimmedString(historyRow.get(QueryHistoryKeyEnum.CONNECTION_NAME)),
            getTrimmedString(historyRow.get(QueryHistoryKeyEnum.DATABASE_NAME)),
            getTrimmedString(historyRow.get(QueryHistoryKeyEnum.ELAPSED_MS)),
            getTrimmedString(historyRow.get(QueryHistoryKeyEnum.ROWS_COUNT))
        );
    }

    private String getCommentedEntry(String label, String sql) {
        return COMMENT_START + " " + getSafeCommentText(label) + NEW_LINE + getSafeCommentText(sql) + NEW_LINE + COMMENT_END + NEW_LINE;
    }

    private boolean isReplayable(String sql) {
        if (sql.isEmpty()) return false;
        for (String notReplayablePrefix : QueryHistoryConfigEnum.NOT_REPLAYABLE_PREFIXES) {
            if (sql.startsWith(notReplayablePrefix)) return false;
        }
        return true;
    }

    private String getDatabaseChange(String databaseName) {
        if (sqlDialect.isMysql() || sqlDialect.isMssql()) {
            return "USE " + sqlIdentifierQuoter.getQuoted(databaseName) + STATEMENT_END;
        }
        return QueryHistoryConfigEnum.DATABASE_CHANGE_X.formatted(getSafeCommentText(databaseName));
    }

    /**
     * Las lineas enteras de comentario "-- ..." pasan a /* ... *&#47;: en el fichero solo queda SQL o comentarios de bloque.
     */
    private String getWithBlockComments(String sql) {
        StringBuilder converted = new StringBuilder(sql.length());
        String[] lines = sql.split(NEW_LINE, -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String strippedLine = line.strip();
            if (i > 0) converted.append(NEW_LINE);
            if (strippedLine.startsWith(LINE_COMMENT)) {
                converted.append(COMMENT_START).append(" ").append(getSafeCommentText(strippedLine.substring(LINE_COMMENT.length()).strip()))
                    .append(" ").append(COMMENT_END);
                continue;
            }
            converted.append(line);
        }
        return converted.toString();
    }

    /**
     * ";" si la sentencia no lo trae; en su propia linea si la ultima linea lleva un comentario de linea (se lo comeria).
     */
    private String getStatementEnd(String sql) {
        if (sql.endsWith(STATEMENT_END)) return "";
        String lastLine = sql.substring(sql.lastIndexOf(NEW_LINE) + 1);
        boolean hasLineComment = lastLine.contains(LINE_COMMENT) || (sqlDialect.isHashComment() && lastLine.contains(HASH_COMMENT));
        return hasLineComment ? NEW_LINE + STATEMENT_END : STATEMENT_END;
    }

    private String getSafeCommentText(String text) {
        return text.replace(COMMENT_END, SAFE_COMMENT_END);
    }
}
