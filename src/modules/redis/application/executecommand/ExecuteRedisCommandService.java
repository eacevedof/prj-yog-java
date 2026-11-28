package modules.redis.application.executecommand;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

import redis.clients.jedis.exceptions.JedisConnectionException;
import redis.clients.jedis.exceptions.JedisDataException;

import modules.shared.domain.enums.SharedConfigEnum;
import modules.shared.application.Primitives;

import static core.config.Global.__;

import modules.connections.domain.entities.ConnectionEntity;
import modules.connections.infrastructure.repositories.ConnectionsReaderSqliteRepository;

import modules.redis.domain.enums.RedisMessageEnum;
import modules.redis.domain.services.RedisCommandLineParser;
import modules.redis.domain.services.RedisDangerousCommandDetector;
import modules.redis.domain.services.RedisReplyFormatter;
import modules.redis.infrastructure.factories.RedisRepositoryFactory;
import modules.redis.infrastructure.repositories.AbstractRedisWriterRepository;
import modules.redis.domain.exceptions.RedisException;

/**
 * Un comando de la consola de Redis, como en redis-cli. Un error de Redis (WRONGTYPE, comando desconocido...) vuelve
 * como resultado, no como excepcion. En una conexion de produccion, los comandos peligrosos (FLUSHDB, DEL, CONFIG
 * SET...) piden confirmacion (428).
 */
public final class ExecuteRedisCommandService {

    private ExecuteRedisCommandService() {
    }

    public static ExecuteRedisCommandService getInstance() {
        return new ExecuteRedisCommandService();
    }

    /**
     * @throws RedisException
     * @throws modules.connections.domain.exceptions.ConnectionsException
     */
    public ExecuteRedisCommandResultDto invoke(ExecuteRedisCommandDto executeRedisCommandDto) {
        failIfWrongInput(executeRedisCommandDto);
        RedisCommandLineParser redisCommandLineParser = RedisCommandLineParser.getInstance();
        List<String> arguments;
        List<byte[]> rawArguments;
        try {
            rawArguments = redisCommandLineParser.getRawArguments(executeRedisCommandDto.getCommandLine());
            arguments = rawArguments.stream().map(rawArgument -> new String(rawArgument, StandardCharsets.UTF_8)).toList();
        }
        catch (IllegalArgumentException e) {
            throw RedisException.badRequestCustom(e.getMessage());
        }
        if (arguments.isEmpty()) {
            throw RedisException.badRequestCustom(__(RedisMessageEnum.NOTHING_TO_EXECUTE));
        }
        failIfProductionNotConfirmed(executeRedisCommandDto, arguments);

        AbstractRedisWriterRepository redisWriterRepository = RedisRepositoryFactory.getInstance().getWriterRepository(
            executeRedisCommandDto.getConnectionUuid(), executeRedisCommandDto.getSessionKey()
        );
        long startNanos = System.nanoTime();
        String output;
        boolean isError = false;
        try {
            output = RedisReplyFormatter.getInstance().getText(redisWriterRepository.executeCommand(rawArguments));
        }
        catch (JedisDataException e) {
            output = SharedConfigEnum.CONSOLE_ERROR_PREFIX + e.getMessage();
            isError = true;
        }
        catch (JedisConnectionException e) {
            throw RedisException.serviceUnavailableCustom(e.getMessage());
        }
        return ExecuteRedisCommandResultDto.fromPrimitives(Primitives.of(
            "output", output,
            "isError", isError,
            "elapsedMs", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos),
            "currentDatabase", redisWriterRepository.getCurrentDatabase()
        ));
    }

    /**
     * @throws RedisException
     */
    private void failIfProductionNotConfirmed(ExecuteRedisCommandDto executeRedisCommandDto, List<String> arguments) {
        if (executeRedisCommandDto.isConfirmed()) return;
        if (!RedisDangerousCommandDetector.getInstance().isDangerous(arguments)) return;
        ConnectionEntity connectionEntity = ConnectionsReaderSqliteRepository.getInstance().getConnectionByUuid(executeRedisCommandDto.getConnectionUuid());
        if (connectionEntity == null || !connectionEntity.isProduction()) return;
        throw RedisException.preconditionRequiredCustom(__(
            RedisMessageEnum.PRODUCTION_X_CONFIRM_X, connectionEntity.getConnectionName(), executeRedisCommandDto.getCommandLine()
        ));
    }

    /**
     * @throws RedisException
     */
    private void failIfWrongInput(ExecuteRedisCommandDto executeRedisCommandDto) {
        if (executeRedisCommandDto.getConnectionUuid().isEmpty()) {
            throw RedisException.badRequestCustom(__(RedisMessageEnum.FIELD_IS_REQUIRED_X, "connectionUuid"));
        }
        if (executeRedisCommandDto.getSessionKey().isEmpty()) {
            throw RedisException.badRequestCustom(__(RedisMessageEnum.FIELD_IS_REQUIRED_X, "sessionKey"));
        }
    }
}
