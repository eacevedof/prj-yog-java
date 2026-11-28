package modules.workspace.infrastructure.repositories;

import java.util.List;
import java.util.Map;

import modules.shared.infrastructure.repositories.AbstractSqliteRepository;

public final class QueryTabsReaderSqliteRepository extends AbstractSqliteRepository {

    private QueryTabsReaderSqliteRepository() {
    }

    public static QueryTabsReaderSqliteRepository getInstance() {
        return new QueryTabsReaderSqliteRepository();
    }

    /**
     * Las pestañas guardadas de una conexion, en su orden, con las claves de WorkspaceKeyEnum.
     */
    public List<Map<String, Object>> getQueryTabs(String connectionUuid) {
        String sql = """
            -- getQueryTabs
            SELECT tab_position, tab_title, sql_text, is_selected
            FROM `app_query_tabs`
            WHERE 1=1
            AND connection_uuid = ?
            ORDER BY tab_position, id
            """;
        return query(sql, connectionUuid);
    }
}
