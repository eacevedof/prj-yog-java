package modules.redis.application.getkeyvalue;

import java.util.Map;

import redis.clients.jedis.exceptions.JedisDataException;
import redis.clients.jedis.exceptions.JedisException;

import static core.config.Global.__;

import modules.redis.domain.enums.RedisKeyEnum;
import modules.redis.domain.enums.RedisMessageEnum;
import modules.redis.infrastructure.factories.RedisRepositoryFactory;
import modules.redis.domain.exceptions.RedisException;

/**
 * El valor de una clave para el navegador: tipo, TTL, tamaño y el valor (string) o los primeros elementos (colecciones).
 */
public final class GetKeyValueService {

    private GetKeyValueService() {
    }

    public static GetKeyValueService getInstance() {
        return new GetKeyValueService();
    }

    /**
     * @throws RedisException 404 si la clave ya no existe (caduco o la borraron)
     * @throws modules.connections.domain.exceptions.ConnectionsException
     */
    public GetKeyValueResultDto invoke(GetKeyValueDto getKeyValueDto) {
        failIfWrongInput(getKeyValueDto);
        Map<String, Object> keyValue;
        try {
            keyValue = RedisRepositoryFactory.getInstance().getReaderRepository(
                getKeyValueDto.getConnectionUuid(), getKeyValueDto.getSessionKey()
            ).getKeyValue(getKeyValueDto.getKey(), getKeyValueDto.getLimit());
        }
        // el servidor contesto con un error (WRONGTYPE, sintaxis...): es de la peticion (400), no un servidor caido (503)
        catch (JedisDataException e) {
            throw RedisException.badRequestCustom(__(RedisMessageEnum.COULD_NOT_READ_X_X, getKeyValueDto.getKey(), e.getMessage()));
        }
        catch (JedisException e) {
            throw RedisException.serviceUnavailableCustom(__(RedisMessageEnum.COULD_NOT_READ_X_X, getKeyValueDto.getKey(), e.getMessage()));
        }
        if (RedisKeyEnum.TYPE_NONE.equals(keyValue.get(RedisKeyEnum.TYPE))) {
            throw RedisException.notFoundCustom(__(RedisMessageEnum.KEY_X_NOT_FOUND, getKeyValueDto.getKey()));
        }
        return GetKeyValueResultDto.fromPrimitives(keyValue);
    }

    /**
     * @throws RedisException
     */
    private void failIfWrongInput(GetKeyValueDto getKeyValueDto) {
        if (getKeyValueDto.getConnectionUuid().isEmpty()) {
            throw RedisException.badRequestCustom(__(RedisMessageEnum.FIELD_IS_REQUIRED_X, "connectionUuid"));
        }
        if (getKeyValueDto.getSessionKey().isEmpty()) {
            throw RedisException.badRequestCustom(__(RedisMessageEnum.FIELD_IS_REQUIRED_X, "sessionKey"));
        }
        if (getKeyValueDto.getKey().isEmpty()) {
            throw RedisException.badRequestCustom(__(RedisMessageEnum.FIELD_IS_REQUIRED_X, "key"));
        }
    }
}
