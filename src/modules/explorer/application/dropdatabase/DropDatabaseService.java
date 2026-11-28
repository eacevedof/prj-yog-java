package modules.explorer.application.dropdatabase;

import java.sql.SQLException;
import java.util.List;

import modules.shared.domain.services.SystemDatabaseDetector;
import modules.shared.application.Primitives;

import static core.config.Global.__;

import modules.connections.domain.entities.ConnectionEntity;
import modules.connections.infrastructure.factories.TargetSessionFactory;
import modules.connections.infrastructure.repositories.ConnectionsReaderSqliteRepository;

import modules.history.application.recordqueryhistory.RecordQueryHistoryDto;
import modules.history.application.recordqueryhistory.RecordQueryHistoryService;

import modules.explorer.domain.enums.ExplorerMessageEnum;
import modules.explorer.domain.services.DatabaseDropSqlBuilder;
import modules.explorer.infrastructure.repositories.ExplorerWriterJdbcRepository;
import modules.explorer.domain.exceptions.ExplorerException;

/**
 * "Eliminar BD" del arbol: borra la BD (el esquema en PostgreSQL) con todo lo que tiene. Nunca una BD de sistema; en
 * una conexion de produccion pide confirmacion (428) antes de tocar nada. Queda en el historial.
 */
public final class DropDatabaseService {

    private DropDatabaseService() {
    }

    public static DropDatabaseService getInstance() {
        return new DropDatabaseService();
    }

    /**
     * @throws ExplorerException si falta un campo, es una BD de sistema o el motor no borra BDs
     * @throws SQLException si el servidor rechaza el DROP: sube tal cual hasta el controller
     * @throws modules.connections.domain.exceptions.ConnectionsException
     */
    public DropDatabaseResultDto invoke(DropDatabaseDto dropDatabaseDto) throws SQLException {
        failIfWrongInput(dropDatabaseDto);
        TargetSessionFactory targetSessionFactory = TargetSessionFactory.getInstance();
        String dbEngine = targetSessionFactory.getDbEngine(dropDatabaseDto.getConnectionUuid());
        DatabaseDropSqlBuilder databaseDropSqlBuilder = DatabaseDropSqlBuilder.getInstance(dbEngine);
        if (!databaseDropSqlBuilder.isSupported()) {
            throw ExplorerException.badRequestCustom(__(ExplorerMessageEnum.DROP_DATABASE_NOT_SUPPORTED_X, dbEngine));
        }
        if (SystemDatabaseDetector.getInstance(dbEngine).isSystemDatabase(dropDatabaseDto.getDatabaseName())) {
            throw ExplorerException.badRequestCustom(__(ExplorerMessageEnum.SYSTEM_DATABASE_X_NOT_DROPPABLE, dropDatabaseDto.getDatabaseName()));
        }
        failIfProductionNotConfirmed(dropDatabaseDto);

        List<String> dropStatements = databaseDropSqlBuilder.getDropStatements(dropDatabaseDto.getDatabaseName());
        String dropSql = String.join(";\n", dropStatements);
        long startNanos = System.nanoTime();
        boolean hasError = true;
        try {
            ExplorerWriterJdbcRepository.getInstance(targetSessionFactory.getSessionPdo(dropDatabaseDto.getConnectionUuid(), dropDatabaseDto.getSessionKey()))
                .executeStatements(dropStatements);
            hasError = false;
        }
        finally {
            RecordQueryHistoryService.getInstance().invoke(RecordQueryHistoryDto.fromPrimitives(Primitives.of(
                "connectionUuid", dropDatabaseDto.getConnectionUuid(),
                "databaseName", dropDatabaseDto.getDatabaseName(),
                "sqlQuery", dropSql,
                "elapsedMs", (System.nanoTime() - startNanos) / 1_000_000,
                "hasError", hasError
            )));
        }
        return DropDatabaseResultDto.fromPrimitives(Primitives.of(
            "databaseName", dropDatabaseDto.getDatabaseName(),
            "dropSql", dropSql
        ));
    }

    /**
     * @throws ExplorerException
     */
    private void failIfProductionNotConfirmed(DropDatabaseDto dropDatabaseDto) {
        if (dropDatabaseDto.isConfirmed()) return;
        ConnectionEntity connectionEntity = ConnectionsReaderSqliteRepository.getInstance().getConnectionByUuid(dropDatabaseDto.getConnectionUuid());
        if (connectionEntity == null || !connectionEntity.isProduction()) return;
        throw ExplorerException.preconditionRequiredCustom(__(
            ExplorerMessageEnum.PRODUCTION_X_DROP_DATABASE_X, connectionEntity.getConnectionName(), dropDatabaseDto.getDatabaseName()
        ));
    }

    /**
     * @throws ExplorerException
     */
    private void failIfWrongInput(DropDatabaseDto dropDatabaseDto) {
        if (dropDatabaseDto.getConnectionUuid().isEmpty()) {
            throw ExplorerException.badRequestCustom(__(ExplorerMessageEnum.FIELD_IS_REQUIRED_X, "connectionUuid"));
        }
        if (dropDatabaseDto.getSessionKey().isEmpty()) {
            throw ExplorerException.badRequestCustom(__(ExplorerMessageEnum.FIELD_IS_REQUIRED_X, "sessionKey"));
        }
        if (dropDatabaseDto.getDatabaseName().isEmpty()) {
            throw ExplorerException.badRequestCustom(__(ExplorerMessageEnum.FIELD_IS_REQUIRED_X, "databaseName"));
        }
    }
}
