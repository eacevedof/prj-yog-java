package modules.redis.application.scankeys;

import redis.clients.jedis.exceptions.JedisDataException;
import redis.clients.jedis.exceptions.JedisException;

import static core.config.Global.__;

import modules.redis.domain.enums.RedisMessageEnum;
import modules.redis.infrastructure.factories.RedisRepositoryFactory;
import modules.redis.infrastructure.repositories.AbstractRedisReaderRepository;
import modules.redis.domain.exceptions.RedisException;

/**
 * Una pagina de claves (SCAN con patron) para el navegador de Redis. Hace red: la UI lo llama fuera del hilo de JavaFX.
 */
public final class ScanKeysService {

    private ScanKeysService() {
    }

    public static ScanKeysService getInstance() {
        return new ScanKeysService();
    }

    /**
     * @throws RedisException
     * @throws modules.connections.domain.exceptions.ConnectionsException
     */
    public ScanKeysResultDto invoke(ScanKeysDto scanKeysDto) {
        failIfWrongInput(scanKeysDto);
        try {
            AbstractRedisReaderRepository redisReaderRepository = RedisRepositoryFactory.getInstance().getReaderRepository(
                scanKeysDto.getConnectionUuid(), scanKeysDto.getSessionKey()
            );
            return ScanKeysResultDto.fromPrimitives(
                redisReaderRepository.scanKeys(scanKeysDto.getPattern(), scanKeysDto.getCursor(), scanKeysDto.getCount())
            );
        }
        // el servidor contesto con un error (WRONGTYPE, sintaxis...): es de la peticion (400), no un servidor caido (503)
        catch (JedisDataException e) {
            throw RedisException.badRequestCustom(__(RedisMessageEnum.COULD_NOT_READ_X_X, scanKeysDto.getPattern(), e.getMessage()));
        }
        catch (JedisException e) {
            throw RedisException.serviceUnavailableCustom(__(RedisMessageEnum.COULD_NOT_READ_X_X, scanKeysDto.getPattern(), e.getMessage()));
        }
    }

    /**
     * @throws RedisException
     */
    private void failIfWrongInput(ScanKeysDto scanKeysDto) {
        if (scanKeysDto.getConnectionUuid().isEmpty()) {
            throw RedisException.badRequestCustom(__(RedisMessageEnum.FIELD_IS_REQUIRED_X, "connectionUuid"));
        }
        if (scanKeysDto.getSessionKey().isEmpty()) {
            throw RedisException.badRequestCustom(__(RedisMessageEnum.FIELD_IS_REQUIRED_X, "sessionKey"));
        }
    }
}
