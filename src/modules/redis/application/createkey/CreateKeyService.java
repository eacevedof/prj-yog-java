package modules.redis.application.createkey;

import redis.clients.jedis.exceptions.JedisException;

import modules.shared.application.Primitives;

import static core.config.Global.__;

import modules.redis.domain.enums.RedisKeyEnum;
import modules.redis.domain.enums.RedisMessageEnum;
import modules.redis.infrastructure.factories.RedisRepositoryFactory;
import modules.redis.infrastructure.repositories.AbstractRedisWriterRepository;
import modules.redis.domain.exceptions.RedisException;

/**
 * "Nueva clave" del navegador de Redis: crea una clave de un tipo con su primer elemento y, si se pide, su caducidad.
 * Nunca pisa una clave que ya exista (para cambiarla esta el panel del valor).
 */
public final class CreateKeyService {

    private CreateKeyService() {
    }

    public static CreateKeyService getInstance() {
        return new CreateKeyService();
    }

    /**
     * @throws RedisException
     * @throws modules.connections.domain.exceptions.ConnectionsException
     */
    public CreateKeyResultDto invoke(CreateKeyDto createKeyDto) {
        failIfWrongInput(createKeyDto);
        double score = getScore(createKeyDto);
        long ttlSeconds = getTtlSeconds(createKeyDto);

        String key = createKeyDto.getKey();
        try {
            // de una vez (EVAL): mirar si existe y escribir por separado dejaba que otro cliente la creara en medio y se pisaba
            AbstractRedisWriterRepository redisWriterRepository = RedisRepositoryFactory.getInstance().getWriterRepository(
                createKeyDto.getConnectionUuid(), createKeyDto.getSessionKey()
            );
            boolean isKeyCreated = redisWriterRepository.createKeyIfAbsent(
                createKeyDto.getType(), key, createKeyDto.getField(), createKeyDto.getValue(), score, ttlSeconds
            );
            if (!isKeyCreated) throw RedisException.badRequestCustom(__(RedisMessageEnum.KEY_X_ALREADY_EXISTS, key));
        }
        catch (JedisException e) {
            throw RedisException.badRequestCustom(__(RedisMessageEnum.COULD_NOT_CHANGE_X_X, key, e.getMessage()));
        }
        return CreateKeyResultDto.fromPrimitives(Primitives.of("key", key));
    }

    /**
     * @throws RedisException
     */
    private void failIfWrongInput(CreateKeyDto createKeyDto) {
        if (createKeyDto.getConnectionUuid().isEmpty()) {
            throw RedisException.badRequestCustom(__(RedisMessageEnum.FIELD_IS_REQUIRED_X, "connectionUuid"));
        }
        if (createKeyDto.getSessionKey().isEmpty()) {
            throw RedisException.badRequestCustom(__(RedisMessageEnum.FIELD_IS_REQUIRED_X, "sessionKey"));
        }
        if (createKeyDto.getKey().isEmpty()) {
            throw RedisException.badRequestCustom(__(RedisMessageEnum.FIELD_IS_REQUIRED_X, "key"));
        }
        if (!RedisKeyEnum.CREATABLE_TYPES.contains(createKeyDto.getType())) {
            throw RedisException.badRequestCustom(__(RedisMessageEnum.TYPE_X_NOT_CREATABLE, createKeyDto.getType()));
        }
        if (RedisKeyEnum.TYPE_HASH.equals(createKeyDto.getType()) && createKeyDto.getField().isEmpty()) {
            throw RedisException.badRequestCustom(__(RedisMessageEnum.FIELD_IS_REQUIRED_X, "field"));
        }
    }

    /**
     * @throws RedisException
     */
    private double getScore(CreateKeyDto createKeyDto) {
        if (!RedisKeyEnum.TYPE_ZSET.equals(createKeyDto.getType())) return 0;
        try {
            return Double.parseDouble(createKeyDto.getScore());
        }
        catch (NumberFormatException e) {
            throw RedisException.badRequestCustom(__(RedisMessageEnum.INVALID_SCORE_X, createKeyDto.getScore()));
        }
    }

    /**
     * @throws RedisException
     */
    private long getTtlSeconds(CreateKeyDto createKeyDto) {
        if (createKeyDto.getTtlSeconds().isEmpty()) return 0;
        try {
            long ttlSeconds = Long.parseLong(createKeyDto.getTtlSeconds());
            if (ttlSeconds > 0) return ttlSeconds;
        }
        catch (NumberFormatException e) {
            // cae al error de abajo
        }
        throw RedisException.badRequestCustom(__(RedisMessageEnum.INVALID_TTL_X, createKeyDto.getTtlSeconds()));
    }
}
