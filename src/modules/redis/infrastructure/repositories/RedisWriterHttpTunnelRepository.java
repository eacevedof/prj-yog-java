package modules.redis.infrastructure.repositories;

import java.nio.charset.StandardCharsets;
import java.util.List;

import modules.connections.infrastructure.components.httptunnel.RedisHttpTunnelSession;

import modules.redis.domain.enums.RedisKeyEnum;

/**
 * Los cambios del navegador de Redis y la consola por el tunel HTTP: lo mismo que RedisWriterRepository, con comandos
 * de redis-cli. El tunel no guarda la sesion: un SELECT n de la consola cambia el &lt;db&gt; que llevan las siguientes
 * peticiones de esta sesion (se comprueba antes con un PING en esa BD).
 */
public final class RedisWriterHttpTunnelRepository extends AbstractRedisWriterRepository {

    private final RedisHttpTunnelCommands redisHttpTunnelCommands;

    private RedisWriterHttpTunnelRepository(RedisHttpTunnelSession redisHttpTunnelSession) {
        this.redisHttpTunnelCommands = RedisHttpTunnelCommands.getInstance(redisHttpTunnelSession);
    }

    public static RedisWriterHttpTunnelRepository getInstance(RedisHttpTunnelSession redisHttpTunnelSession) {
        return new RedisWriterHttpTunnelRepository(redisHttpTunnelSession);
    }

    @Override
    public long applyChange(String operation, String key, String field, String value, double score, long ttlSeconds, String newKey, long index) {
        Object reply = switch (operation) {
            case RedisKeyEnum.OP_DELETE -> redisHttpTunnelCommands.getReply("DEL", key);
            case RedisKeyEnum.OP_RENAME -> redisHttpTunnelCommands.getReply("RENAME", key, newKey);
            case RedisKeyEnum.OP_EXPIRE -> redisHttpTunnelCommands.getReply("EXPIRE", key, String.valueOf(ttlSeconds));
            case RedisKeyEnum.OP_PERSIST -> redisHttpTunnelCommands.getReply("PERSIST", key);
            case RedisKeyEnum.OP_STRING_SET -> redisHttpTunnelCommands.getReply("SET", key, value);
            case RedisKeyEnum.OP_HASH_SET -> {
                redisHttpTunnelCommands.getReply("HSET", key, field, value);
                yield 1L;
            }
            case RedisKeyEnum.OP_HASH_DELETE -> redisHttpTunnelCommands.getReply("HDEL", key, field);
            case RedisKeyEnum.OP_LIST_PUSH -> redisHttpTunnelCommands.getReply("RPUSH", key, value);
            case RedisKeyEnum.OP_LIST_SET -> redisHttpTunnelCommands.getReply("LSET", key, String.valueOf(index), value);
            case RedisKeyEnum.OP_LIST_REMOVE -> redisHttpTunnelCommands.getReply("LREM", key, "1", value);
            case RedisKeyEnum.OP_SET_ADD -> redisHttpTunnelCommands.getReply("SADD", key, value);
            case RedisKeyEnum.OP_SET_REMOVE -> redisHttpTunnelCommands.getReply("SREM", key, value);
            case RedisKeyEnum.OP_ZSET_ADD -> {
                redisHttpTunnelCommands.getReply("ZADD", key, getScoreText(score), value);
                yield 1L;
            }
            case RedisKeyEnum.OP_ZSET_REMOVE -> redisHttpTunnelCommands.getReply("ZREM", key, value);
            default -> throw new IllegalArgumentException(operation);
        };
        return redisHttpTunnelCommands.getLong(reply);
    }

    /**
     * SELECT n no va al servidor como tal (no duraria): se prueba la BD con un PING en ella y, si vale, queda como la
     * de la sesion.
     */
    @Override
    public Object executeCommand(List<byte[]> arguments) {
        String commandName = new String(arguments.getFirst(), StandardCharsets.UTF_8);
        if (arguments.size() == 2 && "SELECT".equalsIgnoreCase(commandName)) {
            String databaseIndex = new String(arguments.get(1), StandardCharsets.UTF_8).trim();
            if (databaseIndex.matches("[0-9]+")) {
                RedisHttpTunnelSession redisHttpTunnelSession = redisHttpTunnelCommands.getSession();
                String previousDatabase = redisHttpTunnelSession.getCurrentDatabase();
                redisHttpTunnelSession.setCurrentDatabase(databaseIndex);
                try {
                    redisHttpTunnelCommands.getReply("PING");
                }
                catch (RuntimeException e) {
                    redisHttpTunnelSession.setCurrentDatabase(previousDatabase);
                    throw e;
                }
                return "OK".getBytes(StandardCharsets.UTF_8);
            }
        }
        return redisHttpTunnelCommands.getRawReply(arguments);
    }

    @Override
    public long getCurrentDatabase() {
        String currentDatabase = redisHttpTunnelCommands.getSession().getCurrentDatabase();
        return currentDatabase.matches("[0-9]+") ? Long.parseLong(currentDatabase) : 0;
    }
}
