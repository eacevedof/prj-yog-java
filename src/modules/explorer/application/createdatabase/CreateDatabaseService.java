package modules.explorer.application.createdatabase;

import java.sql.SQLException;
import java.util.List;

import modules.shared.application.Primitives;

import static core.config.Global.__;

import modules.connections.domain.entities.ConnectionEntity;
import modules.connections.infrastructure.factories.TargetSessionFactory;
import modules.connections.infrastructure.repositories.ConnectionsReaderSqliteRepository;

import modules.history.application.recordqueryhistory.RecordQueryHistoryDto;
import modules.history.application.recordqueryhistory.RecordQueryHistoryService;

import modules.explorer.domain.enums.ExplorerMessageEnum;
import modules.explorer.domain.entities.DatabaseCreationEntity;
import modules.explorer.domain.services.DatabaseCreateSqlBuilder;
import modules.explorer.infrastructure.repositories.ExplorerWriterJdbcRepository;
import modules.explorer.domain.exceptions.ExplorerException;

/**
 * El "Create Database": CREATE DATABASE (MySQL/MariaDB, SQL Server) o CREATE SCHEMA (PostgreSQL) con las opciones del
 * dialogo. En produccion pide confirmacion (428). Queda en el historial.
 */
public final class CreateDatabaseService {

    private CreateDatabaseService() {
    }

    public static CreateDatabaseService getInstance() {
        return new CreateDatabaseService();
    }

    /**
     * @throws ExplorerException si falta el nombre, una opcion no es valida o el motor no crea BDs
     * @throws SQLException si el servidor rechaza el CREATE: sube tal cual hasta el controller
     * @throws modules.connections.domain.exceptions.ConnectionsException
     */
    public CreateDatabaseResultDto invoke(CreateDatabaseDto createDatabaseDto) throws SQLException {
        failIfWrongInput(createDatabaseDto);
        TargetSessionFactory targetSessionFactory = TargetSessionFactory.getInstance();
        String dbEngine = targetSessionFactory.getDbEngine(createDatabaseDto.getConnectionUuid());
        DatabaseCreateSqlBuilder databaseCreateSqlBuilder = DatabaseCreateSqlBuilder.getInstance(dbEngine);
        if (!databaseCreateSqlBuilder.isSupported()) {
            throw ExplorerException.badRequestCustom(__(ExplorerMessageEnum.CREATE_DATABASE_NOT_SUPPORTED_X, dbEngine));
        }
        DatabaseCreationEntity databaseCreationEntity = DatabaseCreationEntity.fromPrimitives(Primitives.of(
            "databaseName", createDatabaseDto.getDatabaseName(),
            "characterSet", createDatabaseDto.getCharacterSet(),
            "collation", createDatabaseDto.getCollation(),
            "encryption", createDatabaseDto.getEncryption(),
            "comment", createDatabaseDto.getComment(),
            "owner", createDatabaseDto.getOwner(),
            "isIfNotExists", createDatabaseDto.isIfNotExists()
        ));
        List<String> errors = databaseCreateSqlBuilder.getErrors(databaseCreationEntity);
        if (!errors.isEmpty()) throw ExplorerException.badRequestCustom(__(ExplorerMessageEnum.WRONG_DATABASE_OPTIONS_X, String.join(", ", errors)));
        failIfProductionNotConfirmed(createDatabaseDto);

        String createSql = databaseCreateSqlBuilder.getCreateSql(databaseCreationEntity);
        long startNanos = System.nanoTime();
        boolean hasError = true;
        try {
            ExplorerWriterJdbcRepository.getInstance(targetSessionFactory.getSessionPdo(createDatabaseDto.getConnectionUuid(), createDatabaseDto.getSessionKey()))
                .executeStatements(List.of(createSql));
            hasError = false;
        }
        finally {
            RecordQueryHistoryService.getInstance().invoke(RecordQueryHistoryDto.fromPrimitives(Primitives.of(
                "connectionUuid", createDatabaseDto.getConnectionUuid(),
                "databaseName", createDatabaseDto.getDatabaseName(),
                "sqlQuery", createSql,
                "elapsedMs", (System.nanoTime() - startNanos) / 1_000_000,
                "hasError", hasError
            )));
        }
        return CreateDatabaseResultDto.fromPrimitives(Primitives.of(
            "databaseName", createDatabaseDto.getDatabaseName(),
            "createSql", createSql
        ));
    }

    /**
     * @throws ExplorerException
     */
    private void failIfProductionNotConfirmed(CreateDatabaseDto createDatabaseDto) {
        if (createDatabaseDto.isConfirmed()) return;
        ConnectionEntity connectionEntity = ConnectionsReaderSqliteRepository.getInstance().getConnectionByUuid(createDatabaseDto.getConnectionUuid());
        if (connectionEntity == null || !connectionEntity.isProduction()) return;
        throw ExplorerException.preconditionRequiredCustom(__(
            ExplorerMessageEnum.PRODUCTION_X_CREATE_DATABASE_X, connectionEntity.getConnectionName(), createDatabaseDto.getDatabaseName()
        ));
    }

    /**
     * @throws ExplorerException
     */
    private void failIfWrongInput(CreateDatabaseDto createDatabaseDto) {
        if (createDatabaseDto.getConnectionUuid().isEmpty()) {
            throw ExplorerException.badRequestCustom(__(ExplorerMessageEnum.FIELD_IS_REQUIRED_X, "connectionUuid"));
        }
        if (createDatabaseDto.getSessionKey().isEmpty()) {
            throw ExplorerException.badRequestCustom(__(ExplorerMessageEnum.FIELD_IS_REQUIRED_X, "sessionKey"));
        }
        if (createDatabaseDto.getDatabaseName().isEmpty()) {
            throw ExplorerException.badRequestCustom(__(ExplorerMessageEnum.FIELD_IS_REQUIRED_X, "databaseName"));
        }
    }
}
