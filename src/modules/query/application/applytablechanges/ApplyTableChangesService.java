package modules.query.application.applytablechanges;

import java.sql.SQLException;
import java.util.List;
import java.util.Set;

import modules.shared.domain.services.SqlDialect;
import modules.shared.domain.services.SqlLiteralQuoter;
import modules.shared.application.Primitives;

import static core.config.Global.__;

import modules.connections.domain.entities.ConnectionEntity;
import modules.connections.infrastructure.factories.TargetSessionFactory;
import modules.connections.infrastructure.repositories.ConnectionsReaderSqliteRepository;

import modules.history.application.recordqueryhistory.RecordQueryHistoryDto;
import modules.history.application.recordqueryhistory.RecordQueryHistoryService;

import modules.explorer.infrastructure.factories.ExplorerReaderRepositoryFactory;

import modules.query.domain.enums.QueryMessageEnum;
import modules.query.domain.entities.PreparedSqlEntity;
import modules.query.domain.services.TableChangesSqlBuilder;
import modules.query.infrastructure.repositories.TableChangesWriterJdbcRepository;
import modules.query.domain.exceptions.QueryException;

/**
 * Guarda los cambios de la rejilla editable (feature 6) en una transaccion. La clave primaria la lee el servicio del
 * servidor (no se fia de la UI) y sin ella no se toca nada. En una conexion de produccion pide confirmacion (428).
 * Hace red: la UI lo llama fuera del hilo de JavaFX.
 */
public final class ApplyTableChangesService {

    private ApplyTableChangesService() {
    }

    public static ApplyTableChangesService getInstance() {
        return new ApplyTableChangesService();
    }

    /**
     * @throws QueryException
     * @throws SQLException si la BD falla: sube tal cual hasta el controller
     * @throws modules.connections.domain.exceptions.ConnectionsException
     */
    public ApplyTableChangesResultDto invoke(ApplyTableChangesDto applyTableChangesDto) throws SQLException {
        failIfWrongInput(applyTableChangesDto);
        failIfProductionNotConfirmed(applyTableChangesDto);

        String databaseName = applyTableChangesDto.getDatabaseName();
        String tableName = applyTableChangesDto.getTableName();
        String dbEngine = TargetSessionFactory.getInstance().getDbEngine(applyTableChangesDto.getConnectionUuid());
        TableChangesWriterJdbcRepository tableChangesWriterJdbcRepository = TableChangesWriterJdbcRepository.getInstance(
            TargetSessionFactory.getInstance().getSessionPdo(applyTableChangesDto.getConnectionUuid(), applyTableChangesDto.getSessionKey()),
            SqlDialect.getInstance(dbEngine)
        );
        Set<String> primaryKeyColumns = ExplorerReaderRepositoryFactory.getInstance()
            .getRepository(applyTableChangesDto.getConnectionUuid(), applyTableChangesDto.getSessionKey())
            .getPrimaryKeyColumns(databaseName, tableName);
        if (primaryKeyColumns.isEmpty()) {
            throw QueryException.badRequestCustom(__(QueryMessageEnum.TABLE_X_WITHOUT_PRIMARY_KEY, databaseName + "." + tableName));
        }
        List<PreparedSqlEntity> statements = getStatements(applyTableChangesDto, dbEngine, primaryKeyColumns);
        long startNanos = System.nanoTime();
        int affectedRows = 0;
        boolean hasError = true;
        try {
            affectedRows = tableChangesWriterJdbcRepository.applyInTransaction(statements);
            hasError = false;
        }
        finally {
            recordHistory(applyTableChangesDto, dbEngine, statements, affectedRows, hasError, startNanos);
        }
        return ApplyTableChangesResultDto.fromPrimitives(Primitives.of("affectedRows", affectedRows));
    }

    /**
     * Feature 8: los cambios de la rejilla, con sus valores ya puestos en el SQL (asi se pueden volver a lanzar).
     */
    private void recordHistory(
        ApplyTableChangesDto applyTableChangesDto,
        String dbEngine,
        List<PreparedSqlEntity> statements,
        int affectedRows,
        boolean hasError,
        long startNanos
    ) {
        SqlLiteralQuoter sqlLiteralQuoter = SqlLiteralQuoter.getInstance(dbEngine);
        List<String> inlinedStatements = statements.stream()
            .map(statement -> sqlLiteralQuoter.getInlined(statement.getSql(), statement.getParams()))
            .toList();
        RecordQueryHistoryService.getInstance().invoke(RecordQueryHistoryDto.fromPrimitives(Primitives.of(
            "connectionUuid", applyTableChangesDto.getConnectionUuid(),
            "databaseName", applyTableChangesDto.getDatabaseName(),
            "sqlQuery", String.join(";\n", inlinedStatements) + ";",
            "elapsedMs", (System.nanoTime() - startNanos) / 1_000_000,
            "rowsCount", affectedRows,
            "hasError", hasError
        )));
    }

    /**
     * @throws QueryException
     */
    private List<PreparedSqlEntity> getStatements(ApplyTableChangesDto applyTableChangesDto, String dbEngine, Set<String> primaryKeyColumns) {
        try {
            return TableChangesSqlBuilder.getInstance(dbEngine).getStatements(
                applyTableChangesDto.getDatabaseName(), applyTableChangesDto.getTableName(), primaryKeyColumns, applyTableChangesDto.getChanges()
            );
        }
        catch (IllegalArgumentException e) {
            throw QueryException.badRequestCustom(__(QueryMessageEnum.CHANGES_NOT_SAVED_X, e.getMessage()));
        }
    }

    /**
     * @throws QueryException
     */
    private void failIfProductionNotConfirmed(ApplyTableChangesDto applyTableChangesDto) {
        if (applyTableChangesDto.isConfirmed()) return;
        ConnectionEntity connectionEntity = ConnectionsReaderSqliteRepository.getInstance().getConnectionByUuid(applyTableChangesDto.getConnectionUuid());
        if (connectionEntity == null || !connectionEntity.isProduction()) return;
        throw QueryException.preconditionRequiredCustom(__(
            QueryMessageEnum.PRODUCTION_X_SAVE_X_CHANGES, connectionEntity.getConnectionName(), applyTableChangesDto.getChanges().size()
        ));
    }

    /**
     * @throws QueryException
     */
    private void failIfWrongInput(ApplyTableChangesDto applyTableChangesDto) {
        if (applyTableChangesDto.getConnectionUuid().isEmpty()) {
            throw QueryException.badRequestCustom(__(QueryMessageEnum.FIELD_IS_REQUIRED_X, "connectionUuid"));
        }
        if (applyTableChangesDto.getSessionKey().isEmpty()) {
            throw QueryException.badRequestCustom(__(QueryMessageEnum.FIELD_IS_REQUIRED_X, "sessionKey"));
        }
        if (applyTableChangesDto.getDatabaseName().isEmpty() || applyTableChangesDto.getTableName().isEmpty()) {
            throw QueryException.badRequestCustom(__(QueryMessageEnum.FIELD_IS_REQUIRED_X, "databaseName, tableName"));
        }
        if (applyTableChangesDto.getChanges().isEmpty()) {
            throw QueryException.badRequestCustom(__(QueryMessageEnum.NO_CHANGES_TO_SAVE));
        }
    }
}
