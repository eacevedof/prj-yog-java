package modules.explorer.application.droptable;

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
import modules.explorer.domain.services.TableDropSqlBuilder;
import modules.explorer.infrastructure.repositories.ExplorerWriterJdbcRepository;
import modules.explorer.domain.exceptions.ExplorerException;

/**
 * "Eliminar tabla" del arbol: DROP TABLE de la tabla con sus datos. En una conexion de produccion pide confirmacion
 * (428) antes de tocar nada.
 */
public final class DropTableService {

    private DropTableService() {
    }

    public static DropTableService getInstance() {
        return new DropTableService();
    }

    /**
     * @throws ExplorerException
     * @throws SQLException si la BD falla (la tabla no existe, una FK la referencia...): sube tal cual hasta el controller
     * @throws modules.connections.domain.exceptions.ConnectionsException
     */
    public DropTableResultDto invoke(DropTableDto dropTableDto) throws SQLException {
        failIfWrongInput(dropTableDto);
        failIfProductionNotConfirmed(dropTableDto);

        TargetSessionFactory targetSessionFactory = TargetSessionFactory.getInstance();
        String dbEngine = targetSessionFactory.getDbEngine(dropTableDto.getConnectionUuid());
        String dropSql = TableDropSqlBuilder.getInstance(dbEngine).getDropSql(dropTableDto.getDatabaseName(), dropTableDto.getTableName());
        long startNanos = System.nanoTime();
        boolean hasError = true;
        try {
            ExplorerWriterJdbcRepository.getInstance(targetSessionFactory.getSession(dropTableDto.getConnectionUuid(), dropTableDto.getSessionKey()))
                .executeStatements(List.of(dropSql));
            hasError = false;
        }
        finally {
            RecordQueryHistoryService.getInstance().invoke(RecordQueryHistoryDto.fromPrimitives(Primitives.of(
                "connectionUuid", dropTableDto.getConnectionUuid(),
                "databaseName", dropTableDto.getDatabaseName(),
                "sqlQuery", dropSql,
                "elapsedMs", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos),
                "hasError", hasError
            )));
        }
        return DropTableResultDto.fromPrimitives(Primitives.of("dropSql", dropSql));
    }

    /**
     * @throws ExplorerException
     */
    private void failIfProductionNotConfirmed(DropTableDto dropTableDto) {
        if (dropTableDto.isConfirmed()) return;
        ConnectionEntity connectionEntity = ConnectionsReaderSqliteRepository.getInstance().getConnectionByUuid(dropTableDto.getConnectionUuid());
        if (connectionEntity == null || !connectionEntity.isProduction()) return;
        throw ExplorerException.preconditionRequiredCustom(__(
            ExplorerMessageEnum.PRODUCTION_X_DROP_X, connectionEntity.getConnectionName(), dropTableDto.getTableName()
        ));
    }

    /**
     * @throws ExplorerException
     */
    private void failIfWrongInput(DropTableDto dropTableDto) {
        if (dropTableDto.getConnectionUuid().isEmpty()) {
            throw ExplorerException.badRequestCustom(__(ExplorerMessageEnum.FIELD_IS_REQUIRED_X, "connectionUuid"));
        }
        if (dropTableDto.getSessionKey().isEmpty()) {
            throw ExplorerException.badRequestCustom(__(ExplorerMessageEnum.FIELD_IS_REQUIRED_X, "sessionKey"));
        }
        if (dropTableDto.getDatabaseName().isEmpty()) {
            throw ExplorerException.badRequestCustom(__(ExplorerMessageEnum.FIELD_IS_REQUIRED_X, "databaseName"));
        }
        if (dropTableDto.getTableName().isEmpty()) {
            throw ExplorerException.badRequestCustom(__(ExplorerMessageEnum.FIELD_IS_REQUIRED_X, "tableName"));
        }
    }
}
