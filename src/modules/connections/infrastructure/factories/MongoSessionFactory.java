package modules.connections.infrastructure.factories;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.jcraft.jsch.JSchException;

import com.mongodb.MongoException;
import com.mongodb.client.MongoClient;

import org.bson.Document;

import static core.config.Global.__;

import modules.connections.domain.enums.ConnectionsMessageEnum;
import modules.connections.domain.enums.DbEngineEnum;
import modules.connections.domain.entities.ConnectionEntity;
import modules.connections.infrastructure.components.MongoClientComponent;
import modules.connections.infrastructure.components.SshTunnelComponent;
import modules.connections.infrastructure.components.httptunnel.HttpTunnelClient;
import modules.connections.infrastructure.repositories.ConnectionsReaderSqliteRepository;
import modules.connections.domain.exceptions.ConnectionsException;

/**
 * Los clientes MongoDB abiertos, uno por clave ("<pestaña de conexion>:mongo-browser", ":mongo-console"...), como
 * TargetSessionFactory con las JDBC. Con tunel SSH, cada uno lleva el suyo y se cierra con el.
 */
public final class MongoSessionFactory {

    private static final Map<String, MongoClient> SESSIONS_BY_KEY = new ConcurrentHashMap<>();
    // un cerrojo por clave: dos hilos con la misma clave no abren dos clientes (uno se perdia con su tunel SSH abierto)
    private static final Map<String, Object> LOCKS_BY_KEY = new ConcurrentHashMap<>();
    private static final Map<String, SshTunnelComponent.OpenTunnel> TUNNELS_BY_KEY = new ConcurrentHashMap<>();
    private static final Map<String, HttpTunnelClient> TUNNEL_CLIENTS_BY_KEY = new ConcurrentHashMap<>();

    private MongoSessionFactory() {
    }

    public static MongoSessionFactory getInstance() {
        return new MongoSessionFactory();
    }

    /**
     * El cliente de esa clave; si no existe o ya no responde, se abre de nuevo.
     * @throws ConnectionsException
     */
    public MongoClient getSession(String connectionUuid, String sessionKey) {
        synchronized (LOCKS_BY_KEY.computeIfAbsent(sessionKey, key -> new Object())) {
            return getOrOpenSession(connectionUuid, sessionKey);
        }
    }

    private MongoClient getOrOpenSession(String connectionUuid, String sessionKey) {
        MongoClient mongoClient = SESSIONS_BY_KEY.get(sessionKey);
        if (mongoClient != null && isAlive(mongoClient)) return mongoClient;
        closeQuietly(SESSIONS_BY_KEY.remove(sessionKey));
        closeTunnel(sessionKey);

        ConnectionEntity connectionEntity = ConnectionsReaderSqliteRepository.getInstance().getConnectionByUuid(connectionUuid);
        if (connectionEntity == null) {
            throw ConnectionsException.notFoundCustom(__(ConnectionsMessageEnum.CONNECTION_X_NOT_FOUND, connectionUuid));
        }
        try {
            String host = connectionEntity.getHost();
            int port = connectionEntity.getPort();
            if (connectionEntity.isSshEnabled()) {
                SshTunnelComponent.OpenTunnel openTunnel = SshTunnelComponent.getInstance().open(connectionEntity);
                TUNNELS_BY_KEY.put(sessionKey, openTunnel);
                host = SshTunnelComponent.LOCAL_HOST;
                port = openTunnel.localPort();
            }
            MongoClient newClient = MongoClientComponent.getInstance().open(connectionEntity, host, port, 0);
            SESSIONS_BY_KEY.put(sessionKey, newClient);
            return newClient;
        }
        catch (JSchException e) {
            throw ConnectionsException.serviceUnavailableCustom(__(
                ConnectionsMessageEnum.CONNECTION_X_FAILED_X, connectionEntity.getConnectionName(), __(ConnectionsMessageEnum.SSH_TUNNEL_FAILED_X, e.getMessage())
            ));
        }
        catch (MongoException e) {
            closeTunnel(sessionKey);
            throw ConnectionsException.serviceUnavailableCustom(
                __(ConnectionsMessageEnum.CONNECTION_X_FAILED_X, connectionEntity.getConnectionName(), e.getMessage())
            );
        }
    }

    /**
     * La conexion va por el tunel HTTP (contrato sqlyog-tunnel-multidbs §5): sus repositorios no usan el driver.
     * @throws ConnectionsException si no existe
     */
    public boolean isHttpTunnel(String connectionUuid) {
        return getConnectionOrFail(connectionUuid).isHttpEnabled();
    }

    /**
     * El cliente del tunel de esa clave. El tunel no guarda estado: cada comando lleva su BD en &lt;db&gt;.
     * @throws ConnectionsException si no existe
     */
    public HttpTunnelClient getTunnelClient(String connectionUuid, String sessionKey) {
        return TUNNEL_CLIENTS_BY_KEY.computeIfAbsent(sessionKey, key -> HttpTunnelClient.getInstance(getConnectionOrFail(connectionUuid)));
    }

    public void closeSessions(String sessionKeyPrefix) {
        List<String> sessionKeys = SESSIONS_BY_KEY.keySet().stream()
            .filter(sessionKey -> sessionKey.equals(sessionKeyPrefix) || sessionKey.startsWith(sessionKeyPrefix + TargetSessionFactory.SESSION_KEY_SEPARATOR))
            .toList();
        for (String sessionKey : sessionKeys) {
            closeQuietly(SESSIONS_BY_KEY.remove(sessionKey));
            closeTunnel(sessionKey);
        }
        TUNNEL_CLIENTS_BY_KEY.keySet().removeIf(sessionKey -> sessionKey.equals(sessionKeyPrefix) || sessionKey.startsWith(sessionKeyPrefix + TargetSessionFactory.SESSION_KEY_SEPARATOR));
    }

    public void closeAll() {
        for (MongoClient mongoClient : SESSIONS_BY_KEY.values()) closeQuietly(mongoClient);
        SESSIONS_BY_KEY.clear();
        for (SshTunnelComponent.OpenTunnel openTunnel : TUNNELS_BY_KEY.values()) SshTunnelComponent.getInstance().close(openTunnel);
        TUNNELS_BY_KEY.clear();
        TUNNEL_CLIENTS_BY_KEY.clear();
    }

    private ConnectionEntity getConnectionOrFail(String connectionUuid) {
        ConnectionEntity connectionEntity = ConnectionsReaderSqliteRepository.getInstance().getConnectionByUuid(connectionUuid);
        if (connectionEntity == null) {
            throw ConnectionsException.notFoundCustom(__(ConnectionsMessageEnum.CONNECTION_X_NOT_FOUND, connectionUuid));
        }
        return connectionEntity;
    }

    /**
     * El cliente se reconecta solo, salvo que el tunel SSH se haya caido: un ping lo dice.
     */
    private boolean isAlive(MongoClient mongoClient) {
        try {
            mongoClient.getDatabase(DbEngineEnum.MONGODB_ADMIN_DATABASE).runCommand(new Document("ping", 1));
            return true;
        }
        catch (MongoException e) {
            return false;
        }
    }

    private void closeTunnel(String sessionKey) {
        SshTunnelComponent.getInstance().close(TUNNELS_BY_KEY.remove(sessionKey));
    }

    private void closeQuietly(MongoClient mongoClient) {
        if (mongoClient == null) return;
        try {
            mongoClient.close();
        }
        catch (RuntimeException e) {
            // se descarta: el cliente ya no se usa
        }
    }
}
