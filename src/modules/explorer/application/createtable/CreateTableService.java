package modules.explorer.application.createtable;

import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import modules.shared.application.Primitives;

import static core.config.Global.__;

import modules.connections.domain.entities.ConnectionEntity;
import modules.connections.infrastructure.factories.TargetSessionFactory;
import modules.connections.infrastructure.repositories.ConnectionsReaderSqliteRepository;

import modules.history.application.recordqueryhistory.RecordQueryHistoryDto;
import modules.history.application.recordqueryhistory.RecordQueryHistoryService;

import modules.explorer.domain.enums.ExplorerMessageEnum;
import modules.explorer.domain.services.TableCreateSqlBuilder;
import modules.explorer.infrastructure.repositories.ExplorerWriterJdbcRepository;
import modules.explorer.domain.exceptions.ExplorerException;

/**
 * El "Create Table" de SQLyog desde el editor de tablas, en cualquier motor SQL: valida la tabla, saca sus sentencias
 * con TableCreateSqlBuilder (CREATE TABLE y, segun el motor, CREATE INDEX y COMMENT ON) y las ejecuta en orden. En
 * produccion pide confirmacion (428). Si ya hay una tabla con ese nombre, el CREATE falla y no se toca nada.
 */
public final class CreateTableService {

    private final TargetSessionFactory targetSessionFactory;
    private final ConnectionsReaderSqliteRepository connectionsReaderSqliteRepository;
    private final RecordQueryHistoryService recordQueryHistoryService;
    private CreateTableDto createTableDto;
    private TableCreateSqlBuilder tableCreateSqlBuilder;

    private CreateTableService() {
        this.targetSessionFactory = TargetSessionFactory.getInstance();
        this.connectionsReaderSqliteRepository = ConnectionsReaderSqliteRepository.getInstance();
        this.recordQueryHistoryService = RecordQueryHistoryService.getInstance();
    }

    public static CreateTableService getInstance() {
        return new CreateTableService();
    }

    /**
     * @throws ExplorerException
     * @throws SQLException si una sentencia falla: sube tal cual hasta el controller
     * @throws modules.connections.domain.exceptions.ConnectionsException
     */
    public CreateTableResultDto invoke(CreateTableDto createTableDto) throws SQLException {
        this.createTableDto = createTableDto;
        failIfWrongInput();
        loadTableCreateSqlBuilder();
        failIfWrongDefinition();
        failIfProductionNotConfirmed();

        List<String> statements = tableCreateSqlBuilder.getStatements(createTableDto.getDatabaseName(), createTableDto.getEdited());
        long startNanos = System.nanoTime();
        boolean hasError = true;
        try {
            ExplorerWriterJdbcRepository.getInstance(targetSessionFactory.getSessionPdo(createTableDto.getConnectionUuid(), createTableDto.getSessionKey()))
                .executeStatements(statements);
            hasError = false;
        }
        finally {
            recordQueryHistoryService.invoke(RecordQueryHistoryDto.fromPrimitives(Primitives.of(
                "connectionUuid", createTableDto.getConnectionUuid(),
                "databaseName", createTableDto.getDatabaseName(),
                "sqlQuery", String.join(";\n", statements) + ";",
                "elapsedMs", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos),
                "hasError", hasError
            )));
        }
        return CreateTableResultDto.fromPrimitives(Primitives.of(
            "statements", statements,
            "tableName", createTableDto.getEdited().tableName()
        ));
    }

    /**
     * @throws ExplorerException
     */
    private void failIfWrongInput() {
        if (createTableDto.getConnectionUuid().isEmpty()) {
            throw ExplorerException.badRequestCustom(__(ExplorerMessageEnum.FIELD_IS_REQUIRED_X, "connectionUuid"));
        }
        if (createTableDto.getSessionKey().isEmpty()) {
            throw ExplorerException.badRequestCustom(__(ExplorerMessageEnum.FIELD_IS_REQUIRED_X, "sessionKey"));
        }
        if (createTableDto.getDatabaseName().isEmpty()) {
            throw ExplorerException.badRequestCustom(__(ExplorerMessageEnum.FIELD_IS_REQUIRED_X, "databaseName"));
        }
        if (createTableDto.getEdited() == null) {
            throw ExplorerException.badRequestCustom(__(ExplorerMessageEnum.FIELD_IS_REQUIRED_X, "edited"));
        }
    }

    /**
     * @throws ExplorerException si el motor no tiene tablas (Redis, MongoDB)
     */
    private void loadTableCreateSqlBuilder() {
        String dbEngine = targetSessionFactory.getDbEngine(createTableDto.getConnectionUuid());
        tableCreateSqlBuilder = TableCreateSqlBuilder.getInstance(dbEngine);
        if (tableCreateSqlBuilder.isSupported()) return;
        throw ExplorerException.badRequestCustom(__(ExplorerMessageEnum.CREATE_TABLE_NOT_SUPPORTED_X, dbEngine));
    }

    /**
     * @throws ExplorerException
     */
    private void failIfWrongDefinition() {
        List<String> errors = tableCreateSqlBuilder.getErrors(createTableDto.getEdited());
        if (errors.isEmpty()) return;
        throw ExplorerException.badRequestCustom(String.join("\n", errors));
    }

    /**
     * @throws ExplorerException
     */
    private void failIfProductionNotConfirmed() {
        if (createTableDto.isConfirmed()) return;
        ConnectionEntity connectionEntity = connectionsReaderSqliteRepository.getConnectionByUuid(createTableDto.getConnectionUuid());
        if (connectionEntity == null || !connectionEntity.isProduction()) return;
        throw ExplorerException.preconditionRequiredCustom(__(
            ExplorerMessageEnum.PRODUCTION_X_CREATE_TABLE_X, connectionEntity.getConnectionName(), createTableDto.getEdited().tableName()
        ));
    }
}
