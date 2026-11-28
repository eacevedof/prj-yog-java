package modules.connections.infrastructure.repositories;

import modules.shared.infrastructure.repositories.AbstractSqliteRepository;

import modules.connections.domain.entities.ConnectionEntity;
import modules.connections.infrastructure.components.SecretCipherComponent;

/**
 * Los opcionales vacios (password, BD, color) se guardan como NULL. Los secretos, cifrados (feature 47).
 */
public final class ConnectionsWriterSqliteRepository extends AbstractSqliteRepository {

    // feature 47: los secretos se guardan cifrados (DPAPI en Windows, AES en el resto)
    private final SecretCipherComponent secretCipherComponent = SecretCipherComponent.getInstance();

    private ConnectionsWriterSqliteRepository() {
    }

    public static ConnectionsWriterSqliteRepository getInstance() {
        return new ConnectionsWriterSqliteRepository();
    }

    /**
     * En el alta updated_at = created_at.
     */
    public int insertConnection(ConnectionEntity connectionEntity) {
        String sql = """
            -- insertConnection
            INSERT INTO `app_connections`
            (connection_uuid, connection_name, db_engine, host, port, user_name, user_password, database_name,
             color_hex, is_production, ssh_enabled, ssh_host, ssh_port, ssh_user, ssh_password, ssh_private_key_path,
             ssh_passphrase, http_enabled, http_url, http_timeout_s, created_at, updated_at)
            VALUES
            (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        return insertAndGetId(sql,
            connectionEntity.getConnectionUuid(),
            connectionEntity.getConnectionName(),
            connectionEntity.getDbEngine(),
            connectionEntity.getHost(),
            connectionEntity.getPort(),
            connectionEntity.getUserName(),
            getNullIfEmpty(secretCipherComponent.encrypt(connectionEntity.getUserPassword())),
            getNullIfEmpty(connectionEntity.getDatabaseName()),
            getNullIfEmpty(connectionEntity.getColorHex()),
            connectionEntity.isProduction() ? 1 : 0,
            connectionEntity.isSshEnabled() ? 1 : 0,
            getNullIfEmpty(connectionEntity.getSshHost()),
            connectionEntity.getSshPort(),
            getNullIfEmpty(connectionEntity.getSshUser()),
            getNullIfEmpty(secretCipherComponent.encrypt(connectionEntity.getSshPassword())),
            getNullIfEmpty(connectionEntity.getSshPrivateKeyPath()),
            getNullIfEmpty(secretCipherComponent.encrypt(connectionEntity.getSshPassphrase())),
            connectionEntity.isHttpEnabled() ? 1 : 0,
            getNullIfEmpty(secretCipherComponent.encrypt(connectionEntity.getHttpUrl())),
            connectionEntity.getHttpTimeoutS(),
            connectionEntity.getCreatedAt(),
            connectionEntity.getCreatedAt()
        );
    }

    /**
     * Todo menos los secretos (y el uuid y created_at, que no cambian). Los secretos van aparte y solo si se tocaron: uno
     * que no se pudo descifrar llega vacio en la entidad y no debe pisar el cifrado que hay en la tabla.
     */
    public int updateConnectionById(ConnectionEntity connectionEntity) {
        String sql = """
            -- updateConnectionById
            UPDATE `app_connections`
            SET
                connection_name = ?,
                db_engine = ?,
                host = ?,
                port = ?,
                user_name = ?,
                database_name = ?,
                color_hex = ?,
                is_production = ?,
                ssh_enabled = ?,
                ssh_host = ?,
                ssh_port = ?,
                ssh_user = ?,
                ssh_private_key_path = ?,
                http_enabled = ?,
                http_timeout_s = ?,
                updated_at = ?
            WHERE 1=1
            AND id = ?
            """;
        return execute(sql,
            connectionEntity.getConnectionName(),
            connectionEntity.getDbEngine(),
            connectionEntity.getHost(),
            connectionEntity.getPort(),
            connectionEntity.getUserName(),
            getNullIfEmpty(connectionEntity.getDatabaseName()),
            getNullIfEmpty(connectionEntity.getColorHex()),
            connectionEntity.isProduction() ? 1 : 0,
            connectionEntity.isSshEnabled() ? 1 : 0,
            getNullIfEmpty(connectionEntity.getSshHost()),
            connectionEntity.getSshPort(),
            getNullIfEmpty(connectionEntity.getSshUser()),
            getNullIfEmpty(connectionEntity.getSshPrivateKeyPath()),
            connectionEntity.isHttpEnabled() ? 1 : 0,
            connectionEntity.getHttpTimeoutS(),
            connectionEntity.getUpdatedAt(),
            connectionEntity.getId()
        );
    }

    /**
     * Editar una conexion: los campos y los secretos que se tocaron en el dialogo, en una sola transaccion (si falla un
     * UPDATE no queda la conexion a medias). Un secreto que no se toco no se reescribe: uno guardado que no se pudo
     * descifrar llega vacio y debe seguir cifrado como estaba.
     */
    public void updateConnectionAndChangedSecretsById(
        ConnectionEntity connectionEntity, boolean isPasswordChanged, boolean isSshSecretsChanged, boolean isHttpUrlChanged
    ) {
        executeInTransaction(() -> {
            updateConnectionById(connectionEntity);
            if (isPasswordChanged) updateUserPasswordById(connectionEntity);
            if (isSshSecretsChanged) updateSshSecretsById(connectionEntity);
            if (isHttpUrlChanged) updateHttpUrlById(connectionEntity);
        });
    }

    private int updateUserPasswordById(ConnectionEntity connectionEntity) {
        String sql = """
            -- updateUserPasswordById
            UPDATE `app_connections`
            SET
                user_password = ?
            WHERE 1=1
            AND id = ?
            """;
        return execute(sql, getNullIfEmpty(secretCipherComponent.encrypt(connectionEntity.getUserPassword())), connectionEntity.getId());
    }

    private int updateSshSecretsById(ConnectionEntity connectionEntity) {
        String sql = """
            -- updateSshSecretsById
            UPDATE `app_connections`
            SET
                ssh_password = ?,
                ssh_passphrase = ?
            WHERE 1=1
            AND id = ?
            """;
        return execute(sql,
            getNullIfEmpty(secretCipherComponent.encrypt(connectionEntity.getSshPassword())),
            getNullIfEmpty(secretCipherComponent.encrypt(connectionEntity.getSshPassphrase())),
            connectionEntity.getId()
        );
    }

    private int updateHttpUrlById(ConnectionEntity connectionEntity) {
        String sql = """
            -- updateHttpUrlById
            UPDATE `app_connections`
            SET
                http_url = ?
            WHERE 1=1
            AND id = ?
            """;
        return execute(sql, getNullIfEmpty(secretCipherComponent.encrypt(connectionEntity.getHttpUrl())), connectionEntity.getId());
    }

    /**
     * Solo los secretos, cifrados: lo usa el arranque para cifrar los que se guardaron en claro antes de la 0.13.0.
     */
    public int updateSecretsById(ConnectionEntity connectionEntity) {
        String sql = """
            -- updateSecretsById
            UPDATE `app_connections`
            SET
                user_password = ?,
                ssh_password = ?,
                ssh_passphrase = ?,
                http_url = ?
            WHERE 1=1
            AND id = ?
            """;
        return execute(sql,
            getNullIfEmpty(secretCipherComponent.encrypt(connectionEntity.getUserPassword())),
            getNullIfEmpty(secretCipherComponent.encrypt(connectionEntity.getSshPassword())),
            getNullIfEmpty(secretCipherComponent.encrypt(connectionEntity.getSshPassphrase())),
            getNullIfEmpty(secretCipherComponent.encrypt(connectionEntity.getHttpUrl())),
            connectionEntity.getId()
        );
    }

    public int deleteConnectionById(ConnectionEntity connectionEntity) {
        String sql = """
            -- deleteConnectionById
            DELETE FROM `app_connections`
            WHERE 1=1
            AND id = ?
            """;
        return execute(sql, connectionEntity.getId());
    }

    private String getNullIfEmpty(String value) {
        return value.isEmpty() ? null : value;
    }
}
