package modules.explorer.application.renametable;

import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import modules.shared.domain.services.SqlDialect;
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
 * F2 sobre una tabla del arbol: la renombra en su BD. El nombre nuevo sigue la regla de TableNameValidator; en SQL
 * Server no puede cambiar de esquema. En una conexion de produccion pide confirmacion (428) antes de tocar nada.
 */
public final class RenameTableService {

    private RenameTableService() {
    }

    public static RenameTableService getInstance() {
        return new RenameTableService();
    }

    /**
     * @throws ExplorerException
     * @throws SQLException si la BD falla (ya existe una tabla con ese nombre...): sube tal cual hasta el controller
     * @throws modules.connections.domain.exceptions.ConnectionsException
     */
    public RenameTableResultDto invoke(RenameTableDto renameTableDto) throws SQLException {
        failIfWrongInput(renameTableDto);

        TargetSessionFactory targetSessionFactory = TargetSessionFactory.getInstance();
        String dbEngine = targetSessionFactory.getDbEngine(renameTableDto.getConnectionUuid());
        TableRenameSqlBuilder tableRenameSqlBuilder = TableRenameSqlBuilder.getInstance(dbEngine);
        String newTableName = getNewTableName(dbEngine, tableRenameSqlBuilder, renameTableDto);
        failIfProductionNotConfirmed(renameTableDto);

        String renameSql = tableRenameSqlBuilder.getRenameTableSql(renameTableDto.getDatabaseName(), renameTableDto.getTableName(), newTableName);
        long startNanos = System.nanoTime();
        boolean hasError = true;
        try {
            ExplorerWriterJdbcRepository.getInstance(targetSessionFactory.getSession(renameTableDto.getConnectionUuid(), renameTableDto.getSessionKey()))
                .executeStatements(List.of(renameSql));
            hasError = false;
        }
        finally {
            RecordQueryHistoryService.getInstance().invoke(RecordQueryHistoryDto.fromPrimitives(Primitives.of(
                "connectionUuid", renameTableDto.getConnectionUuid(),
                "databaseName", renameTableDto.getDatabaseName(),
                "sqlQuery", renameSql,
                "elapsedMs", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos),
                "hasError", hasError
            )));
        }
        return RenameTableResultDto.fromPrimitives(Primitives.of(
            "newTableName", newTableName,
            "renameSql", renameSql
        ));
    }

    /**
     * El nombre nuevo tal como saldra en el arbol. En SQL Server se queda en el esquema de la tabla: "pedidos2" de
     * "ventas.pedidos" es "ventas.pedidos2", y escribir otro esquema es un error.
     *
     * @throws ExplorerException
     */
    private String getNewTableName(String dbEngine, TableRenameSqlBuilder tableRenameSqlBuilder, RenameTableDto renameTableDto) {
        String newTableName = renameTableDto.getNewTableName();
        if (!TableNameValidator.getInstance(dbEngine).isValid(newTableName)) {
            throw ExplorerException.badRequestCustom(__(ExplorerMessageEnum.INVALID_TABLE_NAME_X, newTableName));
        }
        if (!SqlDialect.MSSQL.equals(dbEngine)) return newTableName;

        String tableName = renameTableDto.getTableName();
        String schemaName = tableRenameSqlBuilder.getSchemaPart(tableName);
        if (newTableName.contains(".") && !tableRenameSqlBuilder.getSchemaPart(newTableName).equalsIgnoreCase(schemaName)) {
            throw ExplorerException.badRequestCustom(__(ExplorerMessageEnum.RENAME_SCHEMA_CHANGE_X, newTableName));
        }
        String newObjectName = tableRenameSqlBuilder.getObjectPart(newTableName);
        return tableName.contains(".") ? schemaName + "." + newObjectName : newObjectName;
    }

    /**
     * @throws ExplorerException
     */
    private void failIfProductionNotConfirmed(RenameTableDto renameTableDto) {
        if (renameTableDto.isConfirmed()) return;
        ConnectionEntity connectionEntity = ConnectionsReaderSqliteRepository.getInstance().getConnectionByUuid(renameTableDto.getConnectionUuid());
        if (connectionEntity == null || !connectionEntity.isProduction()) return;
        throw ExplorerException.preconditionRequiredCustom(__(
            ExplorerMessageEnum.PRODUCTION_X_RENAME_X_TO_X, connectionEntity.getConnectionName(), renameTableDto.getTableName(), renameTableDto.getNewTableName()
        ));
    }

    /**
     * @throws ExplorerException
     */
    private void failIfWrongInput(RenameTableDto renameTableDto) {
        if (renameTableDto.getConnectionUuid().isEmpty()) {
            throw ExplorerException.badRequestCustom(__(ExplorerMessageEnum.FIELD_IS_REQUIRED_X, "connectionUuid"));
        }
        if (renameTableDto.getSessionKey().isEmpty()) {
            throw ExplorerException.badRequestCustom(__(ExplorerMessageEnum.FIELD_IS_REQUIRED_X, "sessionKey"));
        }
        if (renameTableDto.getDatabaseName().isEmpty()) {
            throw ExplorerException.badRequestCustom(__(ExplorerMessageEnum.FIELD_IS_REQUIRED_X, "databaseName"));
        }
        if (renameTableDto.getTableName().isEmpty()) {
            throw ExplorerException.badRequestCustom(__(ExplorerMessageEnum.FIELD_IS_REQUIRED_X, "tableName"));
        }
        if (renameTableDto.getNewTableName().isEmpty()) {
            throw ExplorerException.badRequestCustom(__(ExplorerMessageEnum.FIELD_IS_REQUIRED_X, "newTableName"));
        }
    }
}
