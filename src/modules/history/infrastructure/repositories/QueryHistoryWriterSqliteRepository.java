package modules.history.infrastructure.repositories;

import java.util.Map;

import modules.shared.infrastructure.repositories.AbstractSqliteRepository;
import static modules.shared.application.Primitives.getBool;
import static modules.shared.application.Primitives.getInt;
import static modules.shared.application.Primitives.getTrimmedString;

import modules.history.domain.enums.QueryHistoryKeyEnum;

public final class QueryHistoryWriterSqliteRepository extends AbstractSqliteRepository {

    private QueryHistoryWriterSqliteRepository() {
    }

    public static QueryHistoryWriterSqliteRepository getInstance() {
        return new QueryHistoryWriterSqliteRepository();
    }

    /**
     * Una ejecucion, con las claves de QueryHistoryKeyEnum. BD vacia = NULL.
     */
    public void insertHistory(Map<String, Object> history) {
        String sql = """
            -- insertHistory
            INSERT INTO `app_query_history`
            (connection_uuid, connection_name, database_name, sql_query, executed_at, elapsed_ms, rows_count, has_error)
            VALUES
            (?, ?, ?, ?, ?, ?, ?, ?)
            """;
        String databaseName = getTrimmedString(history.get(QueryHistoryKeyEnum.DATABASE_NAME));
        Object elapsedMs = history.get(QueryHistoryKeyEnum.ELAPSED_MS);
        execute(sql,
            getTrimmedString(history.get(QueryHistoryKeyEnum.CONNECTION_UUID)),
            getTrimmedString(history.get(QueryHistoryKeyEnum.CONNECTION_NAME)),
            databaseName.isEmpty() ? null : databaseName,
            getTrimmedString(history.get(QueryHistoryKeyEnum.SQL_QUERY)),
            getTrimmedString(history.get(QueryHistoryKeyEnum.EXECUTED_AT)),
            elapsedMs instanceof Number number ? number.longValue() : 0L,
            getInt(history.get(QueryHistoryKeyEnum.ROWS_COUNT)),
            getBool(history.get(QueryHistoryKeyEnum.HAS_ERROR)) ? 1 : 0
        );
    }

    /**
     * Borra lo que pase de las ultimas maxRows ejecuciones (de todas las conexiones). Por posicion y no por MAX(id) - n:
     * tras limpiar el historial de una conexion quedan huecos de id y se borraba de mas.
     */
    public int deleteOlderThanLast(int maxRows) {
        String sql = """
            -- deleteOlderThanLast
            DELETE FROM `app_query_history`
            WHERE 1=1
            AND id < (
                SELECT id FROM `app_query_history`
                ORDER BY id DESC
                LIMIT 1 OFFSET ? - 1
            )
            """;
        return execute(sql, maxRows);
    }

    public int deleteAllHistory() {
        String sql = """
            -- deleteAllHistory
            DELETE FROM `app_query_history`
            """;
        return execute(sql);
    }

    public int deleteConnectionHistory(String connectionUuid) {
        String sql = """
            -- deleteConnectionHistory
            DELETE FROM `app_query_history`
            WHERE 1=1
            AND connection_uuid = ?
            """;
        return execute(sql, connectionUuid);
    }
}
