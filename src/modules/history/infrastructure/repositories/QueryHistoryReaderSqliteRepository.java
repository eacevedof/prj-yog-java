package modules.history.infrastructure.repositories;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import modules.shared.infrastructure.repositories.AbstractSqliteRepository;

public final class QueryHistoryReaderSqliteRepository extends AbstractSqliteRepository {

    private QueryHistoryReaderSqliteRepository() {
    }

    public static QueryHistoryReaderSqliteRepository getInstance() {
        return new QueryHistoryReaderSqliteRepository();
    }

    /**
     * Lo mas reciente primero. connectionUuid vacio = todas las conexiones; searchText vacio = sin filtrar (si no,
     * contiene en el SQL, sin distinguir mayusculas).
     */
    public List<Map<String, Object>> getHistory(String connectionUuid, String searchText, int limit) {
        StringBuilder sql = new StringBuilder("""
            -- getHistory
            SELECT id, connection_uuid, connection_name, database_name, sql_query, executed_at, elapsed_ms,
                rows_count, has_error
            FROM `app_query_history`
            WHERE 1=1
            """);
        List<Object> params = new ArrayList<>();
        if (!connectionUuid.isEmpty()) {
            sql.append("AND connection_uuid = ?\n");
            params.add(connectionUuid);
        }
        if (!searchText.isEmpty()) {
            sql.append("AND sql_query LIKE ? ESCAPE '\\'\n");
            params.add("%" + searchText.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%");
        }
        sql.append("ORDER BY id DESC\nLIMIT ?");
        params.add(limit);
        return query(sql.toString(), params.toArray());
    }
}
