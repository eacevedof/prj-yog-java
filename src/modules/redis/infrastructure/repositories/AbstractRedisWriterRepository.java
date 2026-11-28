package modules.redis.infrastructure.repositories;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import modules.redis.domain.enums.RedisKeyEnum;

/**
 * Los cambios del navegador de Redis y la consola, por Jedis (RedisWriterRepository) o por el tunel HTTP
 * (RedisWriterHttpTunnelRepository). Lo elige RedisRepositoryFactory segun la conexion.
 */
public abstract class AbstractRedisWriterRepository {

    /**
     * Comprobar que no existe, primera escritura y caducidad en un solo EVAL: Redis lo ejecuta de una vez, sin que otro
     * cliente cree la clave entre la comprobacion y la escritura. ARGV: caducidad en segundos (0 = sin), comando y sus
     * argumentos detras de la clave.
     */
    private static final String CREATE_KEY_IF_ABSENT_SCRIPT = """
        if redis.call('EXISTS', KEYS[1]) == 1 then return 0 end
        redis.call(ARGV[2], KEYS[1], unpack(ARGV, 3))
        if tonumber(ARGV[1]) > 0 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end
        return 1
        """;

    /**
     * Lo que devuelve Redis (claves borradas, elementos añadidos...); "OK" cuenta como 1.
     * @throws IllegalArgumentException con una operacion desconocida
     */
    public abstract long applyChange(String operation, String key, String field, String value, double score, long ttlSeconds, String newKey, long index);

    /**
     * Un comando cualquiera (el primer argumento es el comando): la respuesta como la da Jedis (byte[], Long, List o
     * null). Un error de Redis sube como JedisDataException.
     */
    public abstract Object executeCommand(List<byte[]> arguments);

    /**
     * La BD (indice) en la que queda la sesion.
     */
    public abstract long getCurrentDatabase();

    /**
     * Crea la clave de ese tipo con su primer elemento (y su caducidad si ttlSeconds &gt; 0) solo si no existe, de forma
     * atomica. Devuelve si la creo (false: ya existia y no se toco).
     * @throws IllegalArgumentException con un tipo que no se puede crear
     */
    public boolean createKeyIfAbsent(String type, String key, String field, String value, double score, long ttlSeconds) {
        List<String> scriptArguments = new ArrayList<>(List.of("EVAL", CREATE_KEY_IF_ABSENT_SCRIPT, "1", key, String.valueOf(ttlSeconds)));
        scriptArguments.addAll(switch (type) {
            case RedisKeyEnum.TYPE_STRING -> List.of("SET", value);
            case RedisKeyEnum.TYPE_HASH -> List.of("HSET", field, value);
            case RedisKeyEnum.TYPE_LIST -> List.of("RPUSH", value);
            case RedisKeyEnum.TYPE_SET -> List.of("SADD", value);
            case RedisKeyEnum.TYPE_ZSET -> List.of("ZADD", getScoreText(score), value);
            default -> throw new IllegalArgumentException(type);
        });
        List<byte[]> arguments = new ArrayList<>();
        for (String scriptArgument : scriptArguments) arguments.add(scriptArgument.getBytes(StandardCharsets.UTF_8));
        return executeCommand(arguments) instanceof Number created && created.longValue() == 1;
    }

    /**
     * Un score entero sin ".0" (Redis lo acepta igual; asi se ve como se escribio).
     */
    protected String getScoreText(double score) {
        return score == Math.rint(score) && !Double.isInfinite(score) ? String.valueOf((long) score) : String.valueOf(score);
    }
}
