package modules.connections.infrastructure.factories;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.jcraft.jsch.JSchException;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.exceptions.JedisException;

import static core.config.Global.__;

import modules.connections.domain.enums.ConnectionsMessageEnum;
import modules.connections.domain.entities.ConnectionEntity;
import modules.connections.infrastructure.components.RedisClientComponent;
import modules.connections.infrastructure.components.SshTunnelComponent;
import modules.connections.infrastructure.components.httptunnel.HttpTunnelClient;
import modules.connections.infrastructure.components.httptunnel.RedisHttpTunnelSession;
import modules.connections.infrastructure.repositories.ConnectionsReaderSqliteRepository;
import modules.connections.domain.exceptions.ConnectionsException;

/**
 * Las sesiones Redis abiertas, una por clave ("<pestaña de conexion>:redis-browser", ":redis-console"...), como
 * TargetSessionFactory con las JDBC: Jedis no es seguro entre hilos, asi que el navegador y la consola llevan cada uno
 * la suya. Con tunel SSH, cada sesion lleva el suyo y se cierra con ella.
 */
public final class RedisSessionFactory {

    private static final Map<String, Jedis> SESSIONS_BY_KEY = new ConcurrentHashMap<>();
    // un cerrojo por clave: dos hilos con la misma clave no abren dos sesiones (una se perdia con su tunel SSH abierto)
    private static final Map<String, Object> LOCKS_BY_KEY = new ConcurrentHashMap<>();
    private static final Map<String, SshTunnelComponent.OpenTunnel> TUNNELS_BY_KEY = new ConcurrentHashMap<>();
    private static final Map<String, RedisHttpTunnelSession> TUNNEL_SESSIONS_BY_KEY = new ConcurrentHashMap<>();

    private RedisSessionFactory() {
    }

    public static RedisSessionFactory getInstance() {
        return new RedisSessionFactory();
    }

    /**
     * La sesion de esa clave; si no existe o se ha caido, se abre de nuevo.
     * @throws ConnectionsException
     */
    public Jedis getSession(String connectionUuid, String sessionKey) {
        synchronized (LOCKS_BY_KEY.computeIfAbsent(sessionKey, key -> new Object())) {
            return getOrOpenSession(connectionUuid, sessionKey);
        }
    }

    private Jedis getOrOpenSession(String connectionUuid, String sessionKey) {
        Jedis jedis = SESSIONS_BY_KEY.get(sessionKey);
        if (jedis != null && jedis.isConnected() && !jedis.isBroken()) return jedis;
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
            Jedis newJedis = RedisClientComponent.getInstance().open(connectionEntity, host, port, 0);
            SESSIONS_BY_KEY.put(sessionKey, newJedis);
            return newJedis;
        }
        catch (JSchException e) {
            throw ConnectionsException.serviceUnavailableCustom(__(
                ConnectionsMessageEnum.CONNECTION_X_FAILED_X, connectionEntity.getConnectionName(), __(ConnectionsMessageEnum.SSH_TUNNEL_FAILED_X, e.getMessage())
            ));
        }
        catch (JedisException e) {
            closeTunnel(sessionKey);
            throw ConnectionsException.serviceUnavailableCustom(
                __(ConnectionsMessageEnum.CONNECTION_X_FAILED_X, connectionEntity.getConnectionName(), e.getMessage())
            );
        }
    }


    /**
     * La conexion va por el tunel HTTP (contrato sqlyog-tunnel-multidbs §4): sus repositorios no usan Jedis.
     * @throws ConnectionsException si no existe
     */
    public boolean isHttpTunnel(String connectionUuid) {
        return getConnectionOrFail(connectionUuid).isHttpEnabled();
    }

    /**
     * La sesion por el tunel de esa clave (recuerda el indice de BD de la consola); se crea la primera vez.
     * @throws ConnectionsException si no existe
     */
    public RedisHttpTunnelSession getTunnelSession(String connectionUuid, String sessionKey) {
        return TUNNEL_SESSIONS_BY_KEY.computeIfAbsent(sessionKey, key -> {
            ConnectionEntity connectionEntity = getConnectionOrFail(connectionUuid);
            return RedisHttpTunnelSession.getInstance(HttpTunnelClient.getInstance(connectionEntity), connectionEntity.getDatabaseName());
        });
    }

    private ConnectionEntity getConnectionOrFail(String connectionUuid) {
        ConnectionEntity connectionEntity = ConnectionsReaderSqliteRepository.getInstance().getConnectionByUuid(connectionUuid);
        if (connectionEntity == null) {
            throw ConnectionsException.notFoundCustom(__(ConnectionsMessageEnum.CONNECTION_X_NOT_FOUND, connectionUuid));
        }
        return connectionEntity;
    }

    /**
     * Cierra la sesion de esa clave y las que cuelgan de ella ("<clave>:...").
     */
    public void closeSessions(String sessionKeyPrefix) {
        List<String> sessionKeys = SESSIONS_BY_KEY.keySet().stream()
            .filter(sessionKey -> sessionKey.equals(sessionKeyPrefix) || sessionKey.startsWith(sessionKeyPrefix + TargetSessionFactory.SESSION_KEY_SEPARATOR))
            .toList();
        for (String sessionKey : sessionKeys) {
            closeQuietly(SESSIONS_BY_KEY.remove(sessionKey));
            closeTunnel(sessionKey);
        }
        TUNNEL_SESSIONS_BY_KEY.keySet().removeIf(sessionKey -> sessionKey.equals(sessionKeyPrefix) || sessionKey.startsWith(sessionKeyPrefix + TargetSessionFactory.SESSION_KEY_SEPARATOR));
    }

    public void closeAll() {
        for (Jedis jedis : SESSIONS_BY_KEY.values()) closeQuietly(jedis);
        SESSIONS_BY_KEY.clear();
        for (SshTunnelComponent.OpenTunnel openTunnel : TUNNELS_BY_KEY.values()) SshTunnelComponent.getInstance().close(openTunnel);
        TUNNELS_BY_KEY.clear();
        TUNNEL_SESSIONS_BY_KEY.clear();
    }

    private void closeTunnel(String sessionKey) {
        SshTunnelComponent.getInstance().close(TUNNELS_BY_KEY.remove(sessionKey));
    }

    /**
     * Al cerrar no hay nada que hacer con un error: la sesion se descarta igual.
     */
    private void closeQuietly(Jedis jedis) {
        if (jedis == null) return;
        try {
            jedis.close();
        }
        catch (JedisException e) {
            // se descarta: la sesion ya no se usa
        }
    }
}
