package modules.workspace.infrastructure.repositories;

import java.util.List;
import java.util.Map;

import modules.shared.infrastructure.repositories.AbstractSqliteRepository;

/**
 * app_workspace_state: una fila por dato del espacio de trabajo. Lectura y escritura juntas: es una tabla clave-valor
 * de dos consultas, como app_settings.
 */
public final class WorkspaceStateSqliteRepository extends AbstractSqliteRepository {

    private WorkspaceStateSqliteRepository() {
    }

    public static WorkspaceStateSqliteRepository getInstance() {
        return new WorkspaceStateSqliteRepository();
    }

    /**
     * null si no se ha guardado nunca. El valor tal cual (sin recortar: un filtro puede llevar espacios).
     */
    public String getStateValue(String stateKey) {
        String sql = """
            -- getStateValue
            SELECT state_value
            FROM `app_workspace_state`
            WHERE 1=1
            AND state_key = ?
            """;
        List<Map<String, Object>> rows = query(sql, stateKey);
        if (rows.isEmpty() || rows.getFirst().get("state_value") == null) return null;
        return String.valueOf(rows.getFirst().get("state_value"));
    }

    public void upsertState(String stateKey, String stateValue, String updatedAt) {
        String sql = """
            -- upsertState
            INSERT INTO `app_workspace_state` (state_key, state_value, updated_at)
            VALUES (?, ?, ?)
            ON CONFLICT(state_key) DO UPDATE SET state_value = excluded.state_value, updated_at = excluded.updated_at
            """;
        execute(sql, stateKey, stateValue, updatedAt);
    }

    /**
     * Restablecer espacio de trabajo: fuera todo lo guardado. Devuelve cuantas filas se borraron.
     */
    public int deleteAllStates() {
        String sql = """
            -- deleteAllStates
            DELETE FROM `app_workspace_state`
            """;
        return execute(sql);
    }
}
