package modules.query.application.executequery;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import modules.shared.domain.services.SqlDialect;
import modules.shared.domain.services.SqlErrorCode;
import modules.shared.domain.services.SqlIdentifierQuoter;
import modules.shared.application.Primitives;
import static modules.shared.application.Primitives.getInt;

import static core.config.Global.__;

import modules.connections.domain.entities.ConnectionEntity;
import modules.connections.infrastructure.factories.TargetSessionFactory;
import modules.connections.infrastructure.repositories.ConnectionsReaderSqliteRepository;

import modules.history.application.recordqueryhistory.RecordQueryHistoryDto;
import modules.history.application.recordqueryhistory.RecordQueryHistoryService;

import modules.query.domain.enums.QueryKeyEnum;
import modules.query.domain.enums.QueryMessageEnum;
import modules.query.domain.entities.SqlStatementEntity;
import modules.query.domain.services.DestructiveSqlDetector;
import modules.query.domain.services.SqlStatementSplitter;
import modules.query.infrastructure.components.RunningStatementsComponent;
import modules.query.infrastructure.repositories.QueryExecutorJdbcRepository;
import modules.query.domain.exceptions.QueryException;

/**
 * Ejecuta lo que manda el editor (la sentencia actual o todas) en la sesion de la pestaña, en orden, y para en la
 * primera que falle o si se cancela. Un error de SQL vuelve como resultado, no como excepcion. Hace red: la UI lo
 * llama fuera del hilo de JavaFX.
 */
public final class ExecuteQueryService {

    private final RunningStatementsComponent runningStatementsComponent = RunningStatementsComponent.getInstance();

    private ExecuteQueryService() {
    }

    public static ExecuteQueryService getInstance() {
        return new ExecuteQueryService();
    }

    /**
     * @throws QueryException
     * @throws modules.connections.domain.exceptions.ConnectionsException
     */
    public ExecuteQueryResultDto invoke(ExecuteQueryDto executeQueryDto) {
        failIfWrongInput(executeQueryDto);

        ConnectionEntity connectionEntity = ConnectionsReaderSqliteRepository.getInstance().getConnectionByUuid(executeQueryDto.getConnectionUuid());
        // si la conexion ya no existe, lo dira la sesion al abrirse; para trocear basta con el dialecto por defecto
        String dbEngine = connectionEntity == null ? SqlDialect.MYSQL : connectionEntity.getDbEngine();
        List<SqlStatementEntity> statements = SqlStatementSplitter.getInstance(dbEngine).getStatements(executeQueryDto.getSqlQuery());
        if (statements.isEmpty()) {
            throw QueryException.badRequestCustom(__(QueryMessageEnum.NOTHING_TO_EXECUTE));
        }
        failIfProductionNotConfirmed(executeQueryDto, connectionEntity, statements);

        String sessionKey = executeQueryDto.getSessionKey();
        runningStatementsComponent.startRun(sessionKey);
        long startNanos = System.nanoTime();

        Connection pdo = TargetSessionFactory.getInstance().getSession(executeQueryDto.getConnectionUuid(), sessionKey);
        QueryExecutorJdbcRepository queryExecutorJdbcRepository = QueryExecutorJdbcRepository.getInstance(pdo, SqlDialect.getInstance(dbEngine));
        List<Map<String, Object>> results = getResults(queryExecutorJdbcRepository, executeQueryDto, statements, dbEngine);
        ExecuteQueryResultDto executeQueryResultDto = getResultDto(queryExecutorJdbcRepository, results, executeQueryDto, startNanos);
        recordHistory(executeQueryDto, executeQueryResultDto);
        return executeQueryResultDto;
    }

    private List<Map<String, Object>> getResults(
        QueryExecutorJdbcRepository queryExecutorJdbcRepository,
        ExecuteQueryDto executeQueryDto,
        List<SqlStatementEntity> statements,
        String dbEngine
    ) {
        List<Map<String, Object>> results = new ArrayList<>();
        try {
            useDatabaseIfChanged(queryExecutorJdbcRepository, executeQueryDto.getDatabaseName());
        }
        catch (SQLException e) {
            // con las comillas del motor: con las de MySQL, el error de PostgreSQL o SQL Server enseñaba una sentencia que no es suya
            results.add(getErrorResult("USE " + SqlIdentifierQuoter.getInstance(dbEngine).getQuoted(executeQueryDto.getDatabaseName()), e));
            return results;
        }

        String sessionKey = executeQueryDto.getSessionKey();
        for (SqlStatementEntity statement : statements) {
            if (runningStatementsComponent.isCancelled(sessionKey)) break;
            try {
                results.addAll(queryExecutorJdbcRepository.executeStatement(
                    statement.getSql(), executeQueryDto.getMaxRows(), sessionKey
                ));
            }
            catch (SQLException e) {
                results.add(getErrorResult(statement.getSql(), e));
                break;
            }
        }
        return results;
    }

    /**
     * Feature 8: una fila de historial por ejecucion (filas devueltas + afectadas, y si hubo error).
     */
    private void recordHistory(ExecuteQueryDto executeQueryDto, ExecuteQueryResultDto executeQueryResultDto) {
        int rowsCount = 0;
        boolean hasError = false;
        for (Map<String, Object> result : executeQueryResultDto.getResults()) {
            rowsCount += getInt(result.get(QueryKeyEnum.AFFECTED_ROWS));
            Object rows = result.get(QueryKeyEnum.ROWS);
            if (rows instanceof List<?> rowList) rowsCount += rowList.size();
            if (result.get(QueryKeyEnum.ERROR_MESSAGE) != null) hasError = true;
        }
        RecordQueryHistoryService.getInstance().invoke(RecordQueryHistoryDto.fromPrimitives(Primitives.of(
            "connectionUuid", executeQueryDto.getConnectionUuid(),
            "databaseName", executeQueryResultDto.getCurrentDatabase(),
            "sqlQuery", executeQueryDto.getSqlQuery(),
            "elapsedMs", executeQueryResultDto.getElapsedMs(),
            "rowsCount", rowsCount,
            "hasError", hasError
        )));
    }

    /**
     * Feature 44: en una conexion marcada como produccion, UPDATE/DELETE sin WHERE, DROP y TRUNCATE no se lanzan sin
     * que el usuario lo confirme. Se comprueba aqui (no solo en la UI) para que ningun camino se lo salte.
     * @throws QueryException
     */
    private void failIfProductionNotConfirmed(ExecuteQueryDto executeQueryDto, ConnectionEntity connectionEntity, List<SqlStatementEntity> statements) {
        if (executeQueryDto.isConfirmed()) return;
        if (connectionEntity == null || !connectionEntity.isProduction()) return;

        List<SqlStatementEntity> destructiveStatements = DestructiveSqlDetector.getInstance().getDestructiveStatements(statements);
        if (destructiveStatements.isEmpty()) return;

        String destructiveSqls = String.join(";\n", destructiveStatements.stream().map(SqlStatementEntity::getSql).toList()) + ";";
        throw QueryException.preconditionRequiredCustom(
            __(QueryMessageEnum.PRODUCTION_X_CONFIRM_X, connectionEntity.getConnectionName(), destructiveSqls)
        );
    }

    private void useDatabaseIfChanged(QueryExecutorJdbcRepository queryExecutorJdbcRepository, String databaseName) throws SQLException {
        if (databaseName.isEmpty()) return;
        if (databaseName.equals(queryExecutorJdbcRepository.getCurrentDatabase())) return;
        queryExecutorJdbcRepository.useDatabase(databaseName);
    }

    /**
     * Los resultados ya estan: si leer la BD activa falla (la conexion cayo tras la ultima sentencia) no se pierden, y la
     * BD activa es la que se pidio en vez de vacia (vacia, la UI y el historial perdian en que BD se estaba).
     */
    private ExecuteQueryResultDto getResultDto(
        QueryExecutorJdbcRepository queryExecutorJdbcRepository,
        List<Map<String, Object>> results,
        ExecuteQueryDto executeQueryDto,
        long startNanos
    ) {
        String currentDatabase;
        try {
            currentDatabase = queryExecutorJdbcRepository.getCurrentDatabase();
        }
        catch (SQLException e) {
            currentDatabase = executeQueryDto.getDatabaseName();
        }
        return ExecuteQueryResultDto.fromPrimitives(Primitives.of(
            "results", results,
            "currentDatabase", currentDatabase,
            "isCancelled", runningStatementsComponent.isCancelled(executeQueryDto.getSessionKey()),
            "elapsedMs", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos)
        ));
    }

    /**
     * Como SQLyog: "Error Code: 1146 - Table 'x.y' doesn't exist". Una sentencia cortada por Cancelar lo dice.
     */
    private Map<String, Object> getErrorResult(String sql, SQLException e) {
        String errorMessage = __(QueryMessageEnum.ERROR_CODE_X_X, SqlErrorCode.getInstance().getCode(e), e.getMessage());
        return Primitives.of(
            QueryKeyEnum.SQL, sql,
            QueryKeyEnum.HAS_RESULT_SET, false,
            QueryKeyEnum.COLUMN_NAMES, List.of(),
            QueryKeyEnum.ROWS, List.of(),
            QueryKeyEnum.AFFECTED_ROWS, 0,
            QueryKeyEnum.IS_TRUNCATED, false,
            QueryKeyEnum.ELAPSED_MS, 0L,
            QueryKeyEnum.ERROR_MESSAGE, errorMessage
        );
    }

    /**
     * @throws QueryException
     */
    private void failIfWrongInput(ExecuteQueryDto executeQueryDto) {
        if (executeQueryDto.getConnectionUuid().isEmpty()) {
            throw QueryException.badRequestCustom(__(QueryMessageEnum.FIELD_IS_REQUIRED_X, "connectionUuid"));
        }
        if (executeQueryDto.getSessionKey().isEmpty()) {
            throw QueryException.badRequestCustom(__(QueryMessageEnum.FIELD_IS_REQUIRED_X, "sessionKey"));
        }
        if (executeQueryDto.getSqlQuery().isEmpty()) {
            throw QueryException.badRequestCustom(__(QueryMessageEnum.NOTHING_TO_EXECUTE));
        }
    }
}
