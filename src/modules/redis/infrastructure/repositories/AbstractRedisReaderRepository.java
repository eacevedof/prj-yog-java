package modules.redis.infrastructure.repositories;

import java.util.Map;

/**
 * Lo que lee el navegador de Redis, venga por Jedis (directo o SSH: RedisReaderRepository) o por el tunel HTTP
 * (RedisReaderHttpTunnelRepository). Lo elige RedisRepositoryFactory segun la conexion; los servicios no lo saben.
 * Un error sube como JedisException en los dos (JedisDataException del servidor, JedisConnectionException de la red).
 */
public abstract class AbstractRedisReaderRepository {

    /**
     * Una vuelta de SCAN: [keys => [[key, type, ttl_ms]...], nextCursor]; "0" = no hay mas.
     */
    public abstract Map<String, Object> scanKeys(String pattern, String cursor, int count);

    public abstract boolean hasKey(String key);

    /**
     * Tipo, TTL, tamaño y valor (string o filas de la coleccion, hasta limit).
     */
    public abstract Map<String, Object> getKeyValue(String key, int limit);
}
