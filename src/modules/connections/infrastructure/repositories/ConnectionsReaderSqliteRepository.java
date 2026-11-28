package modules.connections.infrastructure.repositories;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import modules.shared.application.Primitives;
import modules.shared.infrastructure.components.secrets.SecretCipherComponent;
import modules.shared.infrastructure.repositories.AbstractSqliteRepository;

import modules.connections.domain.enums.ConnectionsKeyEnum;
import modules.connections.domain.entities.ConnectionEntity;

public final class ConnectionsReaderSqliteRepository extends AbstractSqliteRepository {

    private static final List<String> SECRET_COLUMNS = List.of(
        ConnectionsKeyEnum.USER_PASSWORD, ConnectionsKeyEnum.SSH_PASSWORD, ConnectionsKeyEnum.SSH_PASSPHRASE,
        ConnectionsKeyEnum.HTTP_URL
    );

    private static final String SELECT_FIELDS = """
            SELECT
                id, connection_uuid, connection_name, db_engine, host, port, user_name, user_password,
                database_name, color_hex, is_production, ssh_enabled, ssh_host, ssh_port, ssh_user, ssh_password,
                ssh_private_key_path, ssh_passphrase, http_enabled, http_url, http_timeout_s, created_at, updated_at
            FROM `app_connections`
            """;

    private ConnectionsReaderSqliteRepository() {
    }

    public static ConnectionsReaderSqliteRepository getInstance() {
        return new ConnectionsReaderSqliteRepository();
    }

    /**
     * Por nombre, sin distinguir mayusculas: es el orden del arbol de conexiones.
     */
    public List<ConnectionEntity> getAllConnections() {
        String sql = """
            -- getAllConnections
            %s
            ORDER BY connection_name COLLATE NOCASE
            """.formatted(SELECT_FIELDS);
        List<ConnectionEntity> connectionEntities = new ArrayList<>();
        for (Map<String, Object> row : query(sql)) {
            connectionEntities.add(getEntity(row));
        }
        return connectionEntities;
    }

    public ConnectionEntity getConnectionByUuid(String connectionUuid) {
        String sql = """
            -- getConnectionByUuid
            %s
            WHERE 1=1
            AND connection_uuid = ?
            """.formatted(SELECT_FIELDS);
        return getFirstOrNull(query(sql, connectionUuid));
    }

    public ConnectionEntity getConnectionById(int connectionId) {
        String sql = """
            -- getConnectionById
            %s
            WHERE 1=1
            AND id = ?
            """.formatted(SELECT_FIELDS);
        return getFirstOrNull(query(sql, connectionId));
    }

    /**
     * Para el nombre unico: otra conexion con ese nombre (sin distinguir mayusculas), sin contar la que se edita.
     */
    public boolean doesConnectionNameExist(String connectionName, String excludedConnectionUuid) {
        String sql = """
            -- doesConnectionNameExist
            SELECT COUNT(*) AS n_rows
            FROM `app_connections`
            WHERE 1=1
            AND connection_name = ? COLLATE NOCASE
            AND connection_uuid != ?
            """;
        List<Map<String, Object>> rows = query(sql, connectionName, excludedConnectionUuid);
        return Primitives.getInt(rows.getFirst().get("n_rows")) > 0;
    }

    /**
     * Feature 47: conexiones con algun secreto aun en claro (de antes de la 0.13.0), para que el arranque las cifre.
     */
    public List<Integer> getConnectionIdsWithPlainSecrets() {
        String sql = """
            -- getConnectionIdsWithPlainSecrets
            SELECT id, user_password, ssh_password, ssh_passphrase, http_url
            FROM `app_connections`
            """;
        SecretCipherComponent secretCipherComponent = SecretCipherComponent.getInstance();
        List<Integer> connectionIds = new ArrayList<>();
        for (Map<String, Object> row : query(sql)) {
            for (String secretColumn : SECRET_COLUMNS) {
                String storedValue = (String) row.get(secretColumn);
                if (storedValue != null && !storedValue.isEmpty() && !secretCipherComponent.isEncrypted(storedValue)) {
                    connectionIds.add(Primitives.getInt(row.get(ConnectionsKeyEnum.ID)));
                    break;
                }
            }
        }
        return connectionIds;
    }

    /**
     * Los secretos llegan cifrados y se descifran aqui. Si uno no se puede descifrar (la BD viene de otro usuario u
     * otro PC) queda vacio: la conexion se sigue viendo y basta con volver a escribir la password.
     */
    private ConnectionEntity getEntity(Map<String, Object> row) {
        SecretCipherComponent secretCipherComponent = SecretCipherComponent.getInstance();
        Map<String, Object> decryptedRow = new LinkedHashMap<>(row);
        for (String secretColumn : SECRET_COLUMNS) {
            try {
                decryptedRow.put(secretColumn, secretCipherComponent.decrypt((String) row.get(secretColumn)));
            }
            catch (IllegalStateException e) {
                decryptedRow.put(secretColumn, "");
            }
        }
        return ConnectionEntity.fromPrimitives(decryptedRow);
    }

    private ConnectionEntity getFirstOrNull(List<Map<String, Object>> rows) {
        return rows.isEmpty() ? null : getEntity(rows.getFirst());
    }
}
