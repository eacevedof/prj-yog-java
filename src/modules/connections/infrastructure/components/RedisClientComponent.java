package modules.connections.infrastructure.components;

import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisClientConfig;

import static modules.shared.application.Primitives.getInt;

import modules.connections.domain.entities.ConnectionEntity;

/**
 * Como se abre un cliente Redis (Jedis) para una conexion guardada: usuario (ACL, opcional), password (opcional) y el
 * indice de BD (la "Base de datos" de la conexion, 0 si va vacia).
 */
public final class RedisClientComponent {

    private static final int CONNECT_TIMEOUT_MS = 8000;
    private static final String CLIENT_NAME = "multidbs";

    private RedisClientComponent() {
    }

    public static RedisClientComponent getInstance() {
        return new RedisClientComponent();
    }

    /**
     * Con tunel SSH (feature 12) se entra por 127.0.0.1 y el puerto local del tunel. socketTimeoutMs 0 = sin limite
     * (una sesion de trabajo); en la prueba de conexion se acota.
     * @throws redis.clients.jedis.exceptions.JedisException si no conecta o no autentica
     */
    public Jedis open(ConnectionEntity connectionEntity, String host, int port, int socketTimeoutMs) {
        int databaseIndex = getDatabaseIndex(connectionEntity);
        DefaultJedisClientConfig.Builder configBuilder = DefaultJedisClientConfig.builder()
            .connectionTimeoutMillis(CONNECT_TIMEOUT_MS)
            .socketTimeoutMillis(socketTimeoutMs)
            .clientName(CLIENT_NAME)
            .database(databaseIndex);
        if (!connectionEntity.getUserName().isEmpty()) configBuilder.user(connectionEntity.getUserName());
        if (!connectionEntity.getUserPassword().isEmpty()) configBuilder.password(connectionEntity.getUserPassword());
        JedisClientConfig jedisClientConfig = configBuilder.build();
        Jedis jedis = new Jedis(new HostAndPort(host, port), jedisClientConfig);
        // Jedis conecta perezosamente: el ping fuerza la conexion y la autenticacion aqui
        jedis.ping();
        // la BD de la config la selecciona la conexion, pero Jedis.getDB() se queda en 0: con select() la sabe
        if (databaseIndex != 0) jedis.select(databaseIndex);
        return jedis;
    }

    public int getTestSocketTimeoutMs() {
        return CONNECT_TIMEOUT_MS;
    }

    /**
     * "redis_version:7.4.1" de INFO server.
     */
    public String getServerVersion(Jedis jedis) {
        for (String line : jedis.info("server").split("\r?\n")) {
            if (line.startsWith("redis_version:")) return "Redis " + line.substring("redis_version:".length()).trim();
        }
        return "Redis";
    }

    public int getDatabaseIndex(ConnectionEntity connectionEntity) {
        return getInt(connectionEntity.getDatabaseName());
    }
}
