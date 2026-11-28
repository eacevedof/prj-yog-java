package modules.redis.application.changekey;

import java.util.regex.Pattern;

import redis.clients.jedis.exceptions.JedisException;

import modules.shared.application.Primitives;
import static modules.shared.application.Primitives.getInt;

import static core.config.Global.__;

import modules.connections.domain.entities.ConnectionEntity;
import modules.connections.infrastructure.repositories.ConnectionsReaderSqliteRepository;

import modules.redis.domain.enums.RedisKeyEnum;
import modules.redis.domain.enums.RedisMessageEnum;
import modules.redis.infrastructure.factories.RedisRepositoryFactory;
import modules.redis.domain.exceptions.RedisException;

/**
 * Los cambios del navegador de Redis: borrar, renombrar y caducidad de una clave, y editar su valor segun el tipo. En
 * una conexion de produccion, borrar y renombrar piden confirmacion (428).
 */
public final class ChangeKeyService {

    private static final Pattern EXPIRE_TTL_PATTERN = Pattern.compile("[1-9][0-9]{0,8}");
    private static final Pattern LIST_INDEX_PATTERN = Pattern.compile("-?[0-9]{1,9}");

    private ChangeKeyService() {
    }

    public static ChangeKeyService getInstance() {
        return new ChangeKeyService();
    }

    /**
     * @throws RedisException
     * @throws modules.connections.domain.exceptions.ConnectionsException
     */
    public ChangeKeyResultDto invoke(ChangeKeyDto changeKeyDto) {
        failIfWrongInput(changeKeyDto);
        failIfProductionNotConfirmed(changeKeyDto);

        double score = getScore(changeKeyDto);
        try {
            long affected = RedisRepositoryFactory.getInstance().getWriterRepository(
                changeKeyDto.getConnectionUuid(), changeKeyDto.getSessionKey()
            ).applyChange(
                changeKeyDto.getOperation(),
                changeKeyDto.getKey(),
                changeKeyDto.getField(),
                changeKeyDto.getValue(),
                score,
                getInt(changeKeyDto.getTtlSeconds()),
                changeKeyDto.getNewKey(),
                getInt(changeKeyDto.getIndex())
            );
            return ChangeKeyResultDto.fromPrimitives(Primitives.of("affected", affected));
        }
        catch (JedisException e) {
            throw RedisException.badRequestCustom(__(RedisMessageEnum.COULD_NOT_CHANGE_X_X, changeKeyDto.getKey(), e.getMessage()));
        }
    }

    /**
     * @throws RedisException
     */
    private double getScore(ChangeKeyDto changeKeyDto) {
        if (!RedisKeyEnum.OP_ZSET_ADD.equals(changeKeyDto.getOperation())) return 0;
        try {
            return Double.parseDouble(changeKeyDto.getScore());
        }
        catch (NumberFormatException e) {
            throw RedisException.badRequestCustom(__(RedisMessageEnum.INVALID_SCORE_X, changeKeyDto.getScore()));
        }
    }

    /**
     * @throws RedisException
     */
    private void failIfProductionNotConfirmed(ChangeKeyDto changeKeyDto) {
        if (changeKeyDto.isConfirmed()) return;
        if (!RedisKeyEnum.DESTRUCTIVE_OPERATIONS.contains(changeKeyDto.getOperation())) return;
        ConnectionEntity connectionEntity = ConnectionsReaderSqliteRepository.getInstance().getConnectionByUuid(changeKeyDto.getConnectionUuid());
        if (connectionEntity == null || !connectionEntity.isProduction()) return;
        throw RedisException.preconditionRequiredCustom(__(
            RedisMessageEnum.PRODUCTION_X_CONFIRM_X, connectionEntity.getConnectionName(), changeKeyDto.getOperation() + " " + changeKeyDto.getKey()
        ));
    }

    /**
     * @throws RedisException
     */
    private void failIfWrongInput(ChangeKeyDto changeKeyDto) {
        if (changeKeyDto.getConnectionUuid().isEmpty()) {
            throw RedisException.badRequestCustom(__(RedisMessageEnum.FIELD_IS_REQUIRED_X, "connectionUuid"));
        }
        if (changeKeyDto.getSessionKey().isEmpty()) {
            throw RedisException.badRequestCustom(__(RedisMessageEnum.FIELD_IS_REQUIRED_X, "sessionKey"));
        }
        if (changeKeyDto.getKey().isEmpty()) {
            throw RedisException.badRequestCustom(__(RedisMessageEnum.FIELD_IS_REQUIRED_X, "key"));
        }
        if (!RedisKeyEnum.OPERATIONS.contains(changeKeyDto.getOperation())) {
            throw RedisException.badRequestCustom(__(RedisMessageEnum.UNKNOWN_OPERATION_X, changeKeyDto.getOperation()));
        }
        if (RedisKeyEnum.OP_RENAME.equals(changeKeyDto.getOperation()) && changeKeyDto.getNewKey().isEmpty()) {
            throw RedisException.badRequestCustom(__(RedisMessageEnum.FIELD_IS_REQUIRED_X, "newKey"));
        }
        if (RedisKeyEnum.OP_HASH_SET.equals(changeKeyDto.getOperation()) && changeKeyDto.getField().isEmpty()) {
            throw RedisException.badRequestCustom(__(RedisMessageEnum.FIELD_IS_REQUIRED_X, "field"));
        }
        if (RedisKeyEnum.OP_EXPIRE.equals(changeKeyDto.getOperation()) && !EXPIRE_TTL_PATTERN.matcher(changeKeyDto.getTtlSeconds()).matches()) {
            throw RedisException.badRequestCustom(__(RedisMessageEnum.INVALID_EXPIRE_TTL_X, changeKeyDto.getTtlSeconds()));
        }
        if (RedisKeyEnum.OP_LIST_SET.equals(changeKeyDto.getOperation()) && !LIST_INDEX_PATTERN.matcher(changeKeyDto.getIndex()).matches()) {
            throw RedisException.badRequestCustom(__(RedisMessageEnum.INVALID_LIST_INDEX_X, changeKeyDto.getIndex()));
        }
    }
}
