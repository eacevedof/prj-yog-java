package modules.explorer.application.duplicatetable;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;

import modules.shared.domain.services.SqlDialect;
import modules.shared.application.Primitives;
import modules.shared.infrastructure.components.date.DateTimer;

import static core.config.Global.__;

import modules.connections.infrastructure.factories.TargetSessionFactory;

import modules.history.application.recordqueryhistory.RecordQueryHistoryDto;
import modules.history.application.recordqueryhistory.RecordQueryHistoryService;

import modules.explorer.domain.enums.ExplorerMessageEnum;
import modules.explorer.domain.services.TableDuplicateSqlBuilder;
import modules.explorer.domain.services.TableNameValidator;
import modules.explorer.infrastructure.factories.ExplorerReaderRepositoryFactory;
import modules.explorer.infrastructure.repositories.AbstractExplorerReaderRepository;
import modules.explorer.infrastructure.repositories.ExplorerWriterJdbcRepository;
import modules.explorer.domain.exceptions.ExplorerException;

/**
 * "Duplicar tabla" del arbol: copia estructura y datos a &lt;tabla&gt;_copy_&lt;ymdhis&gt; en la misma BD. Si la copia
 * falla a medias, la tabla nueva se borra.
 */
public final class DuplicateTableService {

    private DuplicateTableService() {
    }

    public static DuplicateTableService getInstance() {
        return new DuplicateTableService();
    }

    /**
     * @throws ExplorerException
     * @throws SQLException si la BD falla: sube tal cual hasta el controller
     * @throws modules.connections.domain.exceptions.ConnectionsException
     */
    public DuplicateTableResultDto invoke(DuplicateTableDto duplicateTableDto) throws SQLException {
        failIfWrongInput(duplicateTableDto);

        String connectionUuid = duplicateTableDto.getConnectionUuid();
        String sessionKey = duplicateTableDto.getSessionKey();
        String databaseName = duplicateTableDto.getDatabaseName();
        String tableName = duplicateTableDto.getTableName();
        TargetSessionFactory targetSessionFactory = TargetSessionFactory.getInstance();
        String dbEngine = targetSessionFactory.getDbEngine(connectionUuid);
        AbstractExplorerReaderRepository explorerReaderRepository = ExplorerReaderRepositoryFactory.getInstance().getRepository(connectionUuid, sessionKey);

        TableDuplicateSqlBuilder tableDuplicateSqlBuilder = TableDuplicateSqlBuilder.getInstance(dbEngine);
        String copyTableName = duplicateTableDto.getCopyTableName().isEmpty()
            ? tableDuplicateSqlBuilder.getCopyTableName(tableName, DateTimer.getInstance().getNowDt())
            : duplicateTableDto.getCopyTableName();
        failIfWrongTableName(dbEngine, copyTableName);
        String sqliteCreateSql = SqlDialect.SQLITE.equals(dbEngine) ? explorerReaderRepository.getCreateStatement(databaseName, tableName, false) : "";
        Set<String> primaryKeyColumns = SqlDialect.MSSQL.equals(dbEngine) ? explorerReaderRepository.getPrimaryKeyColumns(databaseName, tableName) : Set.of();
        List<String> statements = tableDuplicateSqlBuilder.getStatements(databaseName, tableName, copyTableName, sqliteCreateSql, primaryKeyColumns);

        Connection pdo = targetSessionFactory.getSessionPdo(connectionUuid, sessionKey);
        ExplorerWriterJdbcRepository explorerWriterJdbcRepository = ExplorerWriterJdbcRepository.getInstance(pdo);
        long startNanos = System.nanoTime();
        boolean hasError = true;
        try {
            explorerWriterJdbcRepository.executeStatements(List.of(statements.getFirst()));
            try {
                explorerWriterJdbcRepository.executeStatements(statements.subList(1, statements.size()));
            }
            catch (SQLException e) {
                dropHalfCopy(explorerWriterJdbcRepository, tableDuplicateSqlBuilder.getDropSql(databaseName, copyTableName));
                throw e;
            }
            hasError = false;
        }
        finally {
            RecordQueryHistoryService.getInstance().invoke(RecordQueryHistoryDto.fromPrimitives(Primitives.of(
                "connectionUuid", connectionUuid,
                "databaseName", databaseName,
                "sqlQuery", String.join(";\n", statements) + ";",
                "elapsedMs", (System.nanoTime() - startNanos) / 1_000_000,
                "hasError", hasError
            )));
        }
        return DuplicateTableResultDto.fromPrimitives(Primitives.of("copyTableName", copyTableName));
    }

    /**
     * Solo tras crear la copia: si el CREATE falla (p. ej. ya hay una tabla con ese nombre) no se borra nada, porque esa
     * tabla no es nuestra. Si el DROP falla, el error que se ensena es el de la copia.
     */
    private void dropHalfCopy(ExplorerWriterJdbcRepository explorerWriterJdbcRepository, String dropSql) {
        try {
            explorerWriterJdbcRepository.executeStatements(List.of(dropSql));
        }
        catch (SQLException ignored) {
            // la tabla no existia
        }
    }

    /**
     * @throws ExplorerException
     */
    private void failIfWrongTableName(String dbEngine, String copyTableName) {
        if (!TableNameValidator.getInstance(dbEngine).isValid(copyTableName)) {
            throw ExplorerException.badRequestCustom(__(ExplorerMessageEnum.INVALID_TABLE_NAME_X, copyTableName));
        }
    }

    /**
     * @throws ExplorerException
     */
    private void failIfWrongInput(DuplicateTableDto duplicateTableDto) {
        if (duplicateTableDto.getConnectionUuid().isEmpty()) {
            throw ExplorerException.badRequestCustom(__(ExplorerMessageEnum.FIELD_IS_REQUIRED_X, "connectionUuid"));
        }
        if (duplicateTableDto.getSessionKey().isEmpty()) {
            throw ExplorerException.badRequestCustom(__(ExplorerMessageEnum.FIELD_IS_REQUIRED_X, "sessionKey"));
        }
        if (duplicateTableDto.getDatabaseName().isEmpty()) {
            throw ExplorerException.badRequestCustom(__(ExplorerMessageEnum.FIELD_IS_REQUIRED_X, "databaseName"));
        }
        if (duplicateTableDto.getTableName().isEmpty()) {
            throw ExplorerException.badRequestCustom(__(ExplorerMessageEnum.FIELD_IS_REQUIRED_X, "tableName"));
        }
    }
}
