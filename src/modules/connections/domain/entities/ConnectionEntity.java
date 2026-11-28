package modules.connections.domain.entities;

import java.util.Map;

import modules.shared.application.Primitives;
import static modules.shared.application.Primitives.getBool;
import static modules.shared.application.Primitives.getInt;
import static modules.shared.application.Primitives.getTrimmedString;

import modules.connections.domain.enums.ConnectionsConfigEnum;
import modules.connections.domain.enums.ConnectionsKeyEnum;

/**
 * Una conexion guardada. La password NO se recorta (un espacio puede ser parte de ella). Los opcionales NULL en BD
 * son "" aqui (neutro de string).
 */
public final class ConnectionEntity {

    private final Integer id;
    private final String connectionUuid;
    private final String connectionName;
    private final String dbEngine;
    private final String host;
    private final int port;
    private final String userName;
    private final String userPassword;
    private final String databaseName;
    private final String colorHex;
    private final boolean isProduction;
    private final boolean isSshEnabled;
    private final String sshHost;
    private final int sshPort;
    private final String sshUser;
    private final String sshPassword;
    private final String sshPrivateKeyPath;
    private final String sshPassphrase;
    private final boolean isHttpEnabled;
    private final String httpUrl;
    private final int httpTimeoutS;
    private final String createdAt;
    private final String updatedAt;

    private ConnectionEntity(Map<String, Object> primitives) {
        this.id = primitives.get(ConnectionsKeyEnum.ID) != null ? getInt(primitives.get(ConnectionsKeyEnum.ID)) : null;
        this.connectionUuid = getTrimmedString(primitives.get(ConnectionsKeyEnum.CONNECTION_UUID));
        this.connectionName = getTrimmedString(primitives.get(ConnectionsKeyEnum.CONNECTION_NAME));
        this.dbEngine = getTrimmedString(primitives.get(ConnectionsKeyEnum.DB_ENGINE));
        this.host = getTrimmedString(primitives.get(ConnectionsKeyEnum.HOST));
        this.port = getInt(primitives.get(ConnectionsKeyEnum.PORT));
        this.userName = getTrimmedString(primitives.get(ConnectionsKeyEnum.USER_NAME));
        Object password = primitives.get(ConnectionsKeyEnum.USER_PASSWORD);
        this.userPassword = password == null ? "" : String.valueOf(password);
        this.databaseName = getTrimmedString(primitives.get(ConnectionsKeyEnum.DATABASE_NAME));
        this.colorHex = getTrimmedString(primitives.get(ConnectionsKeyEnum.COLOR_HEX));
        this.isProduction = getBool(primitives.get(ConnectionsKeyEnum.IS_PRODUCTION));
        this.isSshEnabled = getBool(primitives.get(ConnectionsKeyEnum.SSH_ENABLED));
        this.sshHost = getTrimmedString(primitives.get(ConnectionsKeyEnum.SSH_HOST));
        int savedSshPort = getInt(primitives.get(ConnectionsKeyEnum.SSH_PORT));
        this.sshPort = savedSshPort == 0 ? ConnectionsConfigEnum.DEFAULT_SSH_PORT : savedSshPort;
        this.sshUser = getTrimmedString(primitives.get(ConnectionsKeyEnum.SSH_USER));
        Object savedSshPassword = primitives.get(ConnectionsKeyEnum.SSH_PASSWORD);
        this.sshPassword = savedSshPassword == null ? "" : String.valueOf(savedSshPassword);
        this.sshPrivateKeyPath = getTrimmedString(primitives.get(ConnectionsKeyEnum.SSH_PRIVATE_KEY_PATH));
        Object savedSshPassphrase = primitives.get(ConnectionsKeyEnum.SSH_PASSPHRASE);
        this.sshPassphrase = savedSshPassphrase == null ? "" : String.valueOf(savedSshPassphrase);
        this.isHttpEnabled = getBool(primitives.get(ConnectionsKeyEnum.HTTP_ENABLED));
        this.httpUrl = getTrimmedString(primitives.get(ConnectionsKeyEnum.HTTP_URL));
        int savedHttpTimeoutS = getInt(primitives.get(ConnectionsKeyEnum.HTTP_TIMEOUT_S));
        this.httpTimeoutS = savedHttpTimeoutS == 0 ? ConnectionsConfigEnum.DEFAULT_HTTP_TIMEOUT_S : savedHttpTimeoutS;
        this.createdAt = getTrimmedString(primitives.get(ConnectionsKeyEnum.CREATED_AT));
        this.updatedAt = getTrimmedString(primitives.get(ConnectionsKeyEnum.UPDATED_AT));
    }

    public static ConnectionEntity fromPrimitives(Map<String, Object> primitives) {
        return new ConnectionEntity(primitives);
    }

    /**
     * Todo menos los secretos (password de la BD, password y passphrase SSH, URL del tunel HTTP: lleva el token): es lo que viaja a la UI y a los logs.
     */
    public Map<String, Object> toPrimitives() {
        return Primitives.of(
            ConnectionsKeyEnum.ID, id,
            ConnectionsKeyEnum.CONNECTION_UUID, connectionUuid,
            ConnectionsKeyEnum.CONNECTION_NAME, connectionName,
            ConnectionsKeyEnum.DB_ENGINE, dbEngine,
            ConnectionsKeyEnum.HOST, host,
            ConnectionsKeyEnum.PORT, port,
            ConnectionsKeyEnum.USER_NAME, userName,
            ConnectionsKeyEnum.DATABASE_NAME, databaseName,
            ConnectionsKeyEnum.COLOR_HEX, colorHex,
            ConnectionsKeyEnum.IS_PRODUCTION, isProduction,
            ConnectionsKeyEnum.SSH_ENABLED, isSshEnabled,
            ConnectionsKeyEnum.SSH_HOST, sshHost,
            ConnectionsKeyEnum.SSH_PORT, sshPort,
            ConnectionsKeyEnum.SSH_USER, sshUser,
            ConnectionsKeyEnum.SSH_PRIVATE_KEY_PATH, sshPrivateKeyPath,
            ConnectionsKeyEnum.HTTP_ENABLED, isHttpEnabled,
            ConnectionsKeyEnum.HTTP_TIMEOUT_S, httpTimeoutS,
            ConnectionsKeyEnum.CREATED_AT, createdAt,
            ConnectionsKeyEnum.UPDATED_AT, updatedAt
        );
    }

    /**
     * toPrimitives() + la password: solo para copiar la entidad (editar, clonar), nunca para mostrar.
     */
    public Map<String, Object> toPrimitivesWithPassword() {
        Map<String, Object> primitives = toPrimitives();
        primitives.put(ConnectionsKeyEnum.USER_PASSWORD, userPassword);
        primitives.put(ConnectionsKeyEnum.SSH_PASSWORD, sshPassword);
        primitives.put(ConnectionsKeyEnum.SSH_PASSPHRASE, sshPassphrase);
        primitives.put(ConnectionsKeyEnum.HTTP_URL, httpUrl);
        return primitives;
    }

    public Integer getId() {
        return id;
    }

    public String getConnectionUuid() {
        return connectionUuid;
    }

    public String getConnectionName() {
        return connectionName;
    }

    public String getDbEngine() {
        return dbEngine;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getUserName() {
        return userName;
    }

    public String getUserPassword() {
        return userPassword;
    }

    public String getDatabaseName() {
        return databaseName;
    }

    public String getColorHex() {
        return colorHex;
    }

    public boolean isProduction() {
        return isProduction;
    }

    /**
     * Feature 12: la BD se alcanza por un tunel SSH (host y puerto de la conexion, vistos desde el servidor SSH).
     */
    public boolean isSshEnabled() {
        return isSshEnabled;
    }

    public String getSshHost() {
        return sshHost;
    }

    public int getSshPort() {
        return sshPort;
    }

    public String getSshUser() {
        return sshUser;
    }

    public String getSshPassword() {
        return sshPassword;
    }

    public String getSshPrivateKeyPath() {
        return sshPrivateKeyPath;
    }

    public String getSshPassphrase() {
        return sshPassphrase;
    }

    /**
     * Feature 13: la BD se alcanza por el tunel HTTP de SQLyog (el PHP de prj-yog). Host y puerto de la conexion son los
     * de la BD vista desde ese servidor.
     */
    public boolean isHttpEnabled() {
        return isHttpEnabled;
    }

    public String getHttpUrl() {
        return httpUrl;
    }

    public int getHttpTimeoutS() {
        return httpTimeoutS;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }
}
