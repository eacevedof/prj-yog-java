package modules.redis.infrastructure.repositories;

import java.util.List;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.commands.ProtocolCommand;
import redis.clients.jedis.util.SafeEncoder;

import modules.redis.domain.enums.RedisKeyEnum;

/**
 * Los cambios del navegador de Redis sobre una clave (borrar, renombrar, TTL) o su valor (segun el tipo), y los
 * comandos de la consola tal cual.
 */
public final class RedisWriterRepository extends AbstractRedisWriterRepository {

    private final Jedis jedis;

    private RedisWriterRepository(Jedis jedis) {
        this.jedis = jedis;
    }

    public static RedisWriterRepository getInstance(Jedis jedis) {
        return new RedisWriterRepository(jedis);
    }

    /**
     * Devuelve lo que devuelve Redis (claves borradas, elementos añadidos...): 0 suele querer decir "no estaba".
     * @throws IllegalArgumentException con una operacion desconocida
     */
    @Override
    public long applyChange(String operation, String key, String field, String value, double score, long ttlSeconds, String newKey, long index) {
        return switch (operation) {
            case RedisKeyEnum.OP_DELETE -> jedis.del(key);
            case RedisKeyEnum.OP_RENAME -> "OK".equals(jedis.rename(key, newKey)) ? 1 : 0;
            case RedisKeyEnum.OP_EXPIRE -> jedis.expire(key, ttlSeconds);
            case RedisKeyEnum.OP_PERSIST -> jedis.persist(key);
            case RedisKeyEnum.OP_STRING_SET -> "OK".equals(jedis.set(key, value)) ? 1 : 0;
            case RedisKeyEnum.OP_HASH_SET -> {
                jedis.hset(key, field, value);
                yield 1;
            }
            case RedisKeyEnum.OP_HASH_DELETE -> jedis.hdel(key, field);
            case RedisKeyEnum.OP_LIST_PUSH -> jedis.rpush(key, value);
            case RedisKeyEnum.OP_LIST_SET -> "OK".equals(jedis.lset(key, index, value)) ? 1 : 0;
            case RedisKeyEnum.OP_LIST_REMOVE -> jedis.lrem(key, 1, value);
            case RedisKeyEnum.OP_SET_ADD -> jedis.sadd(key, value);
            case RedisKeyEnum.OP_SET_REMOVE -> jedis.srem(key, value);
            case RedisKeyEnum.OP_ZSET_ADD -> {
                jedis.zadd(key, score, value);
                yield 1;
            }
            case RedisKeyEnum.OP_ZSET_REMOVE -> jedis.zrem(key, value);
            default -> throw new IllegalArgumentException(operation);
        };
    }

    /**
     * Un comando cualquiera (el primer argumento es el comando). Un error de Redis sube como JedisDataException.
     */
    @Override
    public Object executeCommand(List<byte[]> arguments) {
        String commandName = SafeEncoder.encode(arguments.getFirst());
        // SELECT por su metodo: asi Jedis sabe en que BD queda la sesion (getDB)
        if (arguments.size() == 2 && "SELECT".equalsIgnoreCase(commandName)) {
            String databaseIndex = SafeEncoder.encode(arguments.get(1));
            if (databaseIndex.matches("[0-9]+")) return jedis.select(Integer.parseInt(databaseIndex));
        }
        byte[] rawCommandName = arguments.getFirst();
        ProtocolCommand protocolCommand = () -> rawCommandName;
        return jedis.sendCommand(protocolCommand, arguments.subList(1, arguments.size()).toArray(byte[][]::new));
    }

    /**
     * La BD de la sesion, sin ir al servidor: SELECT n en la consola va por jedis.select() (executeCommand) y la BD con
     * la que se abrio la conexion tambien (RedisClientComponent.open), asi que Jedis la sabe.
     */
    @Override
    public long getCurrentDatabase() {
        return jedis.getDB();
    }
}
