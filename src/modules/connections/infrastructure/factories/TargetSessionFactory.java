package modules.connections.infrastructure.factories;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.jcraft.jsch.JSchException;

import modules.shared.domain.enums.SessionKeyEnum;

import static core.config.Global.__;

import modules.connections.domain.enums.ConnectionsMessageEnum;
import modules.connections.domain.enums.DbEngineEnum;
import modules.connections.domain.entities.ConnectionEntity;
import modules.connections.infrastructure.components.SshTunnelComponent;
import modules.connections.infrastructure.components.TargetJdbcComponent;
import modules.connections.infrastructure.components.httptunnel.HttpTunnelClient;
import modules.connections.infrastructure.components.httptunnel.HttpTunnelSession;
import modules.connections.infrastructure.repositories.ConnectionsReaderSqliteRepository;
import modules.connections.domain.exceptions.ConnectionsException;

/**
 * Las sesiones abiertas contra el MySQL destino: una conexion JDBC por clave de sesion. La clave la pone la UI:
 * "<pestaña de conexion>:explorer" para el arbol y "<pestaña de conexion>:<pestaña de query>" para cada editor, asi
 * una query larga no bloquea el arbol y cada pestaña tiene su BD activa. Al cerrar una pestaña se cierran sus sesiones.
 */
public final class TargetSessionFactory {

    private static final int IS_VALID_TIMEOUT_S = 2;

    private static final Map<String, Connection> SESSIONS_BY_KEY = new ConcurrentHashMap<>();
    // un cerrojo por clave: dos hilos con la misma clave no abren dos sesiones (una se perdia con su tunel SSH abierto)
    private static final Map<String, Object> LOCKS_BY_KEY = new ConcurrentHashMap<>();
    // feature 12: el tunel SSH de cada sesion que lo lleva
    private static final Map<String, SshTunnelComponent.OpenTunnel> TUNNELS_BY_KEY = new ConcurrentHashMap<>();

    private TargetSessionFactory() {
    }

    public static TargetSessionFactory getInstance() {
        return new TargetSessionFactory();
    }

    /**
     * La sesion de esa clave; si no existe o se ha caido (timeout del servidor, red), se abre de nuevo.
     * @throws ConnectionsException
     */
    public Connection getSession(String connectionUuid, String sessionKey) {
        synchronized (LOCKS_BY_KEY.computeIfAbsent(sessionKey, key -> new Object())) {
            return getOrOpenSession(connectionUuid, sessionKey);
        }
    }

    private Connection getOrOpenSession(String connectionUuid, String sessionKey) {
        Connection connection = SESSIONS_BY_KEY.get(sessionKey);
        if (isUsable(connection)) {
            return connection;
        }
        closeQuietly(connection);
        closeTunnel(sessionKey);

        ConnectionEntity connectionEntity = ConnectionsReaderSqliteRepository.getInstance().getConnectionByUuid(connectionUuid);
        if (connectionEntity == null) {
            throw ConnectionsException.notFoundCustom(__(ConnectionsMessageEnum.CONNECTION_X_NOT_FOUND, connectionUuid));
        }

        // feature 13: por el tunel HTTP no hay JDBC; la sesion habla el protocolo de SQLyog
        if (connectionEntity.isHttpEnabled()) {
            Connection httpTunnelConnection = HttpTunnelSession.getInstance(
                HttpTunnelClient.getInstance(connectionEntity), connectionEntity.getDbEngine(), connectionEntity.getDatabaseName()
            ).getConnection();
            SESSIONS_BY_KEY.put(sessionKey, httpTunnelConnection);
            return httpTunnelConnection;
        }

        TargetJdbcComponent targetJdbcComponent = TargetJdbcComponent.getInstance();
        try {
            String host = connectionEntity.getHost();
            int port = connectionEntity.getPort();
            if (connectionEntity.isSshEnabled()) {
                SshTunnelComponent.OpenTunnel openTunnel = SshTunnelComponent.getInstance().open(connectionEntity);
                TUNNELS_BY_KEY.put(sessionKey, openTunnel);
                host = SshTunnelComponent.LOCAL_HOST;
                port = openTunnel.localPort();
            }
            Connection newConnection = targetJdbcComponent.openConnection(connectionEntity, host, port, 0);
            SESSIONS_BY_KEY.put(sessionKey, newConnection);
            return newConnection;
        }
        catch (JSchException e) {
            throw ConnectionsException.serviceUnavailableCustom(__(
                ConnectionsMessageEnum.CONNECTION_X_FAILED_X, connectionEntity.getConnectionName(), __(ConnectionsMessageEnum.SSH_TUNNEL_FAILED_X, e.getMessage())
            ));
        }
        catch (SQLException e) {
            closeTunnel(sessionKey);
            throw ConnectionsException.serviceUnavailableCustom(
                __(ConnectionsMessageEnum.CONNECTION_X_FAILED_X, connectionEntity.getConnectionName(), e.getMessage())
            );
        }
    }

    /**
     * El motor SQL de la conexion (mysql, pgsql...): cada servicio elige con el su repositorio y su dialecto. "Ficheros"
     * es SQLite: sus sesiones van al SQLite de trabajo con los ficheros cargados.
     * @throws ConnectionsException si la conexion ya no existe
     */
    public String getDbEngine(String connectionUuid) {
        ConnectionEntity connectionEntity = ConnectionsReaderSqliteRepository.getInstance().getConnectionByUuid(connectionUuid);
        if (connectionEntity == null) {
            throw ConnectionsException.notFoundCustom(__(ConnectionsMessageEnum.CONNECTION_X_NOT_FOUND, connectionUuid));
        }
        return DbEngineEnum.FILES.equals(connectionEntity.getDbEngine()) ? DbEngineEnum.SQLITE : connectionEntity.getDbEngine();
    }

    /**
     * Cierra la sesion de esa clave y las que cuelgan de ella ("<clave>:..."): cerrar una pestaña de conexion cierra
     * su arbol y todos sus editores.
     */
    public void closeSessions(String sessionKeyPrefix) {
        List<String> sessionKeys = SESSIONS_BY_KEY.keySet().stream()
            .filter(sessionKey -> sessionKey.equals(sessionKeyPrefix) || sessionKey.startsWith(sessionKeyPrefix + SessionKeyEnum.SEPARATOR))
            .toList();
        for (String sessionKey : sessionKeys) {
            closeQuietly(SESSIONS_BY_KEY.remove(sessionKey));
            closeTunnel(sessionKey);
        }
    }

    public void closeAll() {
        for (Connection connection : SESSIONS_BY_KEY.values()) {
            closeQuietly(connection);
        }
        SESSIONS_BY_KEY.clear();
        for (SshTunnelComponent.OpenTunnel openTunnel : TUNNELS_BY_KEY.values()) {
            SshTunnelComponent.getInstance().close(openTunnel);
        }
        TUNNELS_BY_KEY.clear();
    }

    /**
     * El tunel de una sesion se cierra con ella (y antes de abrir uno nuevo si la sesion se cayo).
     */
    private void closeTunnel(String sessionKey) {
        SshTunnelComponent.getInstance().close(TUNNELS_BY_KEY.remove(sessionKey));
    }

    private boolean isUsable(Connection connection) {
        if (connection == null) return false;
        try {
            return !connection.isClosed() && connection.isValid(IS_VALID_TIMEOUT_S);
        }
        catch (SQLException e) {
            return false;
        }
    }

    /**
     * Al cerrar no hay nada que hacer con un error: la conexion se descarta igual (servidor caido, red cortada).
     */
    private void closeQuietly(Connection connection) {
        if (connection == null) return;
        try {
            connection.close();
        }
        catch (SQLException e) {
            // se descarta: la sesion ya no se usa
        }
    }
}
