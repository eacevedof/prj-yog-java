package modules.workspace.infrastructure.repositories;

import java.util.List;
import java.util.Map;

import modules.shared.infrastructure.repositories.AbstractSqliteRepository;
import static modules.shared.application.Primitives.getBool;
import static modules.shared.application.Primitives.getTrimmedString;

import modules.workspace.domain.enums.WorkspaceKeyEnum;

public final class QueryTabsWriterSqliteRepository extends AbstractSqliteRepository {

    private QueryTabsWriterSqliteRepository() {
    }

    public static QueryTabsWriterSqliteRepository getInstance() {
        return new QueryTabsWriterSqliteRepository();
    }

    /**
     * Cambia las pestañas guardadas de la conexion por estas (claves de WorkspaceKeyEnum), en una transaccion: o
     * quedan todas las nuevas o las de antes.
     */
    public void replaceQueryTabs(String connectionUuid, List<Map<String, Object>> queryTabs, String updatedAt) {
        String deleteSql = """
            -- replaceQueryTabs: delete
            DELETE FROM `app_query_tabs`
            WHERE 1=1
            AND connection_uuid = ?
            """;
        String insertSql = """
            -- replaceQueryTabs: insert
            INSERT INTO `app_query_tabs`
            (connection_uuid, tab_position, tab_title, sql_text, is_selected, updated_at)
            VALUES
            (?, ?, ?, ?, ?, ?)
            """;
        executeInTransaction(() -> {
            execute(deleteSql, connectionUuid);
            int tabPosition = 0;
            for (Map<String, Object> queryTab : queryTabs) {
                Object sqlText = queryTab.get(WorkspaceKeyEnum.SQL_TEXT);
                execute(insertSql,
                    connectionUuid,
                    tabPosition++,
                    getTrimmedString(queryTab.get(WorkspaceKeyEnum.TAB_TITLE)),
                    sqlText == null ? "" : String.valueOf(sqlText),
                    getBool(queryTab.get(WorkspaceKeyEnum.IS_SELECTED)) ? 1 : 0,
                    updatedAt
                );
            }
        });
    }

    /**
     * Restablecer espacio de trabajo: fuera las pestañas guardadas de todas las conexiones. Devuelve cuantas se borraron.
     */
    public int deleteAllQueryTabs() {
        String sql = """
            -- deleteAllQueryTabs
            DELETE FROM `app_query_tabs`
            """;
        return execute(sql);
    }
}
