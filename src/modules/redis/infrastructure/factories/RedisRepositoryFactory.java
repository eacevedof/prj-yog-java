package modules.redis.infrastructure.factories;

import modules.connections.infrastructure.factories.RedisSessionFactory;

import modules.redis.infrastructure.repositories.AbstractRedisReaderRepository;
import modules.redis.infrastructure.repositories.AbstractRedisWriterRepository;
import modules.redis.infrastructure.repositories.RedisReaderHttpTunnelRepository;
import modules.redis.infrastructure.repositories.RedisReaderRepository;
import modules.redis.infrastructure.repositories.RedisWriterHttpTunnelRepository;
import modules.redis.infrastructure.repositories.RedisWriterRepository;

/**
 * Los repositorios de Redis de una conexion, sobre la sesion de esa clave: por Jedis (directo o SSH) o, si la conexion
 * va por el tunel HTTP, por el tunel. Los servicios piden aqui y no saben por donde va.
 */
public final class RedisRepositoryFactory {

    private RedisRepositoryFactory() {
    }

    public static RedisRepositoryFactory getInstance() {
        return new RedisRepositoryFactory();
    }

    /**
     * @throws modules.connections.domain.exceptions.ConnectionsException
     */
    public AbstractRedisReaderRepository getReaderRepository(String connectionUuid, String sessionKey) {
        RedisSessionFactory redisSessionFactory = RedisSessionFactory.getInstance();
        if (redisSessionFactory.isHttpTunnel(connectionUuid)) {
            return RedisReaderHttpTunnelRepository.getInstance(redisSessionFactory.getTunnelSession(connectionUuid, sessionKey));
        }
        return RedisReaderRepository.getInstance(redisSessionFactory.getSession(connectionUuid, sessionKey));
    }

    /**
     * @throws modules.connections.domain.exceptions.ConnectionsException
     */
    public AbstractRedisWriterRepository getWriterRepository(String connectionUuid, String sessionKey) {
        RedisSessionFactory redisSessionFactory = RedisSessionFactory.getInstance();
        if (redisSessionFactory.isHttpTunnel(connectionUuid)) {
            return RedisWriterHttpTunnelRepository.getInstance(redisSessionFactory.getTunnelSession(connectionUuid, sessionKey));
        }
        return RedisWriterRepository.getInstance(redisSessionFactory.getSession(connectionUuid, sessionKey));
    }
}
