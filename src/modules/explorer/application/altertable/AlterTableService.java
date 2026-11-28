package modules.explorer.application.altertable;

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
import modules.explorer.domain.services.TableAlterSqlBuilder;
import modules.explorer.infrastructure.repositories.ExplorerWriterJdbcRepository;
import modules.explorer.domain.exceptions.ExplorerException;

/**
 * Guarda el editor de tablas ("Alter Table" de SQLyog, MySQL/MariaDB): valida lo editado, saca las sentencias con
 * TableAlterSqlBuilder (solo lo que cambia) y las ejecuta en orden. En produccion pide confirmacion (428). Un ALTER
 * no va en transaccion en MySQL: si una sentencia falla, las anteriores ya quedaron hechas y el error dice cual fallo.
 */
public final class AlterTableService {

    private AlterTableService() {
    }

    public static AlterTableService getInstance() {
        return new AlterTableService();
    }

    /**
     * @throws ExplorerException
     * @throws SQLException si una sentencia falla: sube tal cual hasta el controller
     * @throws modules.connections.domain.exceptions.ConnectionsException
     */
    public AlterTableResultDto invoke(AlterTableDto alterTableDto) throws SQLException {
        failIfWrongInput(alterTableDto);
        TargetSessionFactory targetSessionFactory = TargetSessionFactory.getInstance();
        String dbEngine = targetSessionFactory.getDbEngine(alterTableDto.getConnectionUuid());
        if (!SqlDialect.MYSQL.equals(dbEngine)) {
            throw ExplorerException.badRequestCustom(__(ExplorerMessageEnum.ONLY_MYSQL_TABLE_EDITOR_X, dbEngine));
        }
        TableAlterSqlBuilder tableAlterSqlBuilder = TableAlterSqlBuilder.getInstance();
        List<String> errors = tableAlterSqlBuilder.getErrors(alterTableDto.getEdited());
        if (!errors.isEmpty()) throw ExplorerException.badRequestCustom(String.join("\n", errors));
        List<String> statements = tableAlterSqlBuilder.getStatements(alterTableDto.getDatabaseName(), alterTableDto.getOriginal(), alterTableDto.getEdited());
        if (statements.isEmpty()) throw ExplorerException.badRequestCustom(__(ExplorerMessageEnum.NOTHING_TO_ALTER));
        failIfProductionNotConfirmed(alterTableDto);

        long startNanos = System.nanoTime();
        boolean hasError = true;
        try {
            ExplorerWriterJdbcRepository.getInstance(targetSessionFactory.getSessionPdo(alterTableDto.getConnectionUuid(), alterTableDto.getSessionKey()))
                .executeStatements(statements);
            hasError = false;
        }
        finally {
            RecordQueryHistoryService.getInstance().invoke(RecordQueryHistoryDto.fromPrimitives(Primitives.of(
                "connectionUuid", alterTableDto.getConnectionUuid(),
                "databaseName", alterTableDto.getDatabaseName(),
                "sqlQuery", String.join(";\n", statements) + ";",
                "elapsedMs", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos),
                "hasError", hasError
            )));
        }
        return AlterTableResultDto.fromPrimitives(Primitives.of(
            "statements", statements,
            "tableName", alterTableDto.getEdited().tableName()
        ));
    }

    /**
     * @throws ExplorerException
     */
    private void failIfProductionNotConfirmed(AlterTableDto alterTableDto) {
        if (alterTableDto.isConfirmed()) return;
        ConnectionEntity connectionEntity = ConnectionsReaderSqliteRepository.getInstance().getConnectionByUuid(alterTableDto.getConnectionUuid());
        if (connectionEntity == null || !connectionEntity.isProduction()) return;
        throw ExplorerException.preconditionRequiredCustom(__(
            ExplorerMessageEnum.PRODUCTION_X_ALTER_X, connectionEntity.getConnectionName(), alterTableDto.getOriginal().tableName()
        ));
    }

    /**
     * @throws ExplorerException
     */
    private void failIfWrongInput(AlterTableDto alterTableDto) {
        if (alterTableDto.getConnectionUuid().isEmpty()) {
            throw ExplorerException.badRequestCustom(__(ExplorerMessageEnum.FIELD_IS_REQUIRED_X, "connectionUuid"));
        }
        if (alterTableDto.getSessionKey().isEmpty()) {
            throw ExplorerException.badRequestCustom(__(ExplorerMessageEnum.FIELD_IS_REQUIRED_X, "sessionKey"));
        }
        if (alterTableDto.getDatabaseName().isEmpty()) {
            throw ExplorerException.badRequestCustom(__(ExplorerMessageEnum.FIELD_IS_REQUIRED_X, "databaseName"));
        }
        if (alterTableDto.getOriginal() == null || alterTableDto.getEdited() == null) {
            throw ExplorerException.badRequestCustom(__(ExplorerMessageEnum.FIELD_IS_REQUIRED_X, "original/edited"));
        }
    }
}
