package modules.explorer.application.renametablecolumn;

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
import modules.explorer.domain.services.TableNameValidator;
import modules.explorer.domain.services.TableRenameSqlBuilder;
import modules.explorer.infrastructure.repositories.ExplorerWriterJdbcRepository;
import modules.explorer.domain.exceptions.ExplorerException;

/**
 * F2 sobre una columna del arbol: la renombra sin tocar su tipo (RENAME COLUMN; sp_rename en SQL Server). En una
 * conexion de produccion pide confirmacion (428) antes de tocar nada.
 */
public final class RenameTableColumnService {

    private RenameTableColumnService() {
    }

    public static RenameTableColumnService getInstance() {
        return new RenameTableColumnService();
    }

    /**
     * @throws ExplorerException
     * @throws SQLException si la BD falla (la columna ya existe, MySQL anterior a 8...): sube tal cual hasta el controller
     * @throws modules.connections.domain.exceptions.ConnectionsException
     */
    public RenameTableColumnResultDto invoke(RenameTableColumnDto renameTableColumnDto) throws SQLException {
        failIfWrongInput(renameTableColumnDto);

        TargetSessionFactory targetSessionFactory = TargetSessionFactory.getInstance();
        String dbEngine = targetSessionFactory.getDbEngine(renameTableColumnDto.getConnectionUuid());
        failIfWrongColumnName(dbEngine, renameTableColumnDto.getNewColumnName());
        failIfProductionNotConfirmed(renameTableColumnDto);

        String renameSql = TableRenameSqlBuilder.getInstance(dbEngine).getRenameColumnSql(
            renameTableColumnDto.getDatabaseName(), renameTableColumnDto.getTableName(),
            renameTableColumnDto.getColumnName(), renameTableColumnDto.getNewColumnName()
        );
        long startNanos = System.nanoTime();
        boolean hasError = true;
        try {
            ExplorerWriterJdbcRepository.getInstance(targetSessionFactory.getSessionPdo(renameTableColumnDto.getConnectionUuid(), renameTableColumnDto.getSessionKey()))
                .executeStatements(List.of(renameSql));
            hasError = false;
        }
        finally {
            RecordQueryHistoryService.getInstance().invoke(RecordQueryHistoryDto.fromPrimitives(Primitives.of(
                "connectionUuid", renameTableColumnDto.getConnectionUuid(),
                "databaseName", renameTableColumnDto.getDatabaseName(),
                "sqlQuery", renameSql,
                "elapsedMs", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos),
                "hasError", hasError
            )));
        }
        return RenameTableColumnResultDto.fromPrimitives(Primitives.of("renameSql", renameSql));
    }

    /**
     * @throws ExplorerException
     */
    private void failIfWrongColumnName(String dbEngine, String newColumnName) {
        if (TableNameValidator.getInstance(dbEngine).isValidColumn(newColumnName)) return;
        throw ExplorerException.badRequestCustom(__(ExplorerMessageEnum.INVALID_COLUMN_NAME_X, newColumnName));
    }

    /**
     * @throws ExplorerException
     */
    private void failIfProductionNotConfirmed(RenameTableColumnDto renameTableColumnDto) {
        if (renameTableColumnDto.isConfirmed()) return;
        ConnectionEntity connectionEntity = ConnectionsReaderSqliteRepository.getInstance().getConnectionByUuid(renameTableColumnDto.getConnectionUuid());
        if (connectionEntity == null || !connectionEntity.isProduction()) return;
        throw ExplorerException.preconditionRequiredCustom(__(
            ExplorerMessageEnum.PRODUCTION_X_RENAME_X_TO_X, connectionEntity.getConnectionName(),
            renameTableColumnDto.getTableName() + "." + renameTableColumnDto.getColumnName(), renameTableColumnDto.getNewColumnName()
        ));
    }

    /**
     * @throws ExplorerException
     */
    private void failIfWrongInput(RenameTableColumnDto renameTableColumnDto) {
        if (renameTableColumnDto.getConnectionUuid().isEmpty()) {
            throw ExplorerException.badRequestCustom(__(ExplorerMessageEnum.FIELD_IS_REQUIRED_X, "connectionUuid"));
        }
        if (renameTableColumnDto.getSessionKey().isEmpty()) {
            throw ExplorerException.badRequestCustom(__(ExplorerMessageEnum.FIELD_IS_REQUIRED_X, "sessionKey"));
        }
        if (renameTableColumnDto.getDatabaseName().isEmpty()) {
            throw ExplorerException.badRequestCustom(__(ExplorerMessageEnum.FIELD_IS_REQUIRED_X, "databaseName"));
        }
        if (renameTableColumnDto.getTableName().isEmpty()) {
            throw ExplorerException.badRequestCustom(__(ExplorerMessageEnum.FIELD_IS_REQUIRED_X, "tableName"));
        }
        if (renameTableColumnDto.getColumnName().isEmpty()) {
            throw ExplorerException.badRequestCustom(__(ExplorerMessageEnum.FIELD_IS_REQUIRED_X, "columnName"));
        }
    }
}
