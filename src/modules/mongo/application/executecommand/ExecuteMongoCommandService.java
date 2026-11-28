package modules.mongo.application.executecommand;

import java.util.concurrent.TimeUnit;

import com.mongodb.MongoException;
import com.mongodb.MongoSocketException;
import com.mongodb.MongoTimeoutException;

import org.bson.json.JsonParseException;

import modules.shared.application.Primitives;

import static core.config.Global.__;

import modules.connections.domain.entities.ConnectionEntity;
import modules.connections.infrastructure.repositories.ConnectionsReaderSqliteRepository;

import modules.mongo.domain.enums.MongoMessageEnum;
import modules.mongo.domain.entities.MongoShellCommandEntity;
import modules.mongo.domain.services.MongoDangerousCommandDetector;
import modules.mongo.domain.services.MongoShellParser;
import modules.mongo.infrastructure.factories.MongoRepositoryFactory;
import modules.mongo.infrastructure.repositories.AbstractMongoShellRepository;
import modules.mongo.domain.exceptions.MongoModuleException;

/**
 * Una orden de la consola de MongoDB (subconjunto de mongosh, ver MongoShellParser). Un error del servidor o de
 * sintaxis vuelve como resultado "(error) ...", no como excepcion. En produccion, lo destructivo (deleteMany, drop,
 * dropDatabase...) pide confirmacion (428).
 */
public final class ExecuteMongoCommandService {

    private static final String DEFAULT_DATABASE = "test";

    private ExecuteMongoCommandService() {
    }

    public static ExecuteMongoCommandService getInstance() {
        return new ExecuteMongoCommandService();
    }

    /**
     * @throws MongoModuleException
     * @throws modules.connections.domain.exceptions.ConnectionsException
     */
    public ExecuteMongoCommandResultDto invoke(ExecuteMongoCommandDto executeMongoCommandDto) {
        failIfWrongInput(executeMongoCommandDto);

        String databaseName = executeMongoCommandDto.getDatabaseName().isEmpty() ? DEFAULT_DATABASE : executeMongoCommandDto.getDatabaseName();
        MongoShellCommandEntity command;
        try {
            command = MongoShellParser.getInstance().getCommand(executeMongoCommandDto.getCommandLine());
        }
        catch (IllegalArgumentException e) {
            return getResult("(error) " + __(MongoMessageEnum.UNKNOWN_COMMAND_X, e.getMessage()), true, databaseName, 0);
        }
        if (MongoShellCommandEntity.USE.equals(command.type())) {
            return getResult("switched to db " + command.name(), false, command.name(), 0);
        }
        failIfProductionNotConfirmed(executeMongoCommandDto, command);

        AbstractMongoShellRepository mongoShellRepository = MongoRepositoryFactory.getInstance().getShellRepository(
            executeMongoCommandDto.getConnectionUuid(), executeMongoCommandDto.getSessionKey()
        );
        long startNanos = System.nanoTime();
        try {
            String output = mongoShellRepository.execute(command, databaseName);
            return getResult(output, false, databaseName, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos));
        }
        catch (MongoSocketException | MongoTimeoutException e) {
            throw MongoModuleException.serviceUnavailableCustom(e.getMessage());
        }
        catch (MongoException | JsonParseException e) {
            return getResult("(error) " + e.getMessage(), true, databaseName, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos));
        }
        catch (IllegalArgumentException e) {
            String message = String.valueOf(e.getMessage());
            String error = message.startsWith("method:") && message.length() > "method:".length() && !isKnownMethod(message.substring(7))
                ? __(MongoMessageEnum.UNKNOWN_METHOD_X, message.substring(7))
                : __(MongoMessageEnum.WRONG_ARGUMENTS_X, command.method().isEmpty() ? command.type() : command.method()) + " (" + message + ")";
            return getResult("(error) " + error, true, databaseName, 0);
        }
    }

    private boolean isKnownMethod(String method) {
        return java.util.Set.of(
            "find", "findOne", "countDocuments", "estimatedDocumentCount", "distinct", "aggregate", "insertOne", "insertMany",
            "updateOne", "updateMany", "replaceOne", "deleteOne", "deleteMany", "getIndexes", "createIndex", "dropIndex", "drop"
        ).contains(method);
    }

    private ExecuteMongoCommandResultDto getResult(String output, boolean isError, String currentDatabase, long elapsedMs) {
        return ExecuteMongoCommandResultDto.fromPrimitives(Primitives.of(
            "output", output, "isError", isError, "currentDatabase", currentDatabase, "elapsedMs", elapsedMs
        ));
    }

    /**
     * @throws MongoModuleException
     */
    private void failIfProductionNotConfirmed(ExecuteMongoCommandDto executeMongoCommandDto, MongoShellCommandEntity command) {
        if (executeMongoCommandDto.isConfirmed()) return;
        if (!MongoDangerousCommandDetector.getInstance().isDangerous(command)) return;
        ConnectionEntity connectionEntity = ConnectionsReaderSqliteRepository.getInstance().getConnectionByUuid(executeMongoCommandDto.getConnectionUuid());
        if (connectionEntity == null || !connectionEntity.isProduction()) return;
        throw MongoModuleException.preconditionRequiredCustom(__(
            MongoMessageEnum.PRODUCTION_X_CONFIRM_X, connectionEntity.getConnectionName(), executeMongoCommandDto.getCommandLine()
        ));
    }

    /**
     * @throws MongoModuleException
     */
    private void failIfWrongInput(ExecuteMongoCommandDto executeMongoCommandDto) {
        if (executeMongoCommandDto.getConnectionUuid().isEmpty() || executeMongoCommandDto.getSessionKey().isEmpty()) {
            throw MongoModuleException.badRequestCustom(__(MongoMessageEnum.FIELD_IS_REQUIRED_X, "connectionUuid, sessionKey"));
        }
        if (executeMongoCommandDto.getCommandLine().isEmpty()) {
            throw MongoModuleException.badRequestCustom(__(MongoMessageEnum.NOTHING_TO_EXECUTE));
        }
    }
}
