package modules.query.application.executesqlfile;

import java.io.UncheckedIOException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import modules.shared.domain.services.SqlDialect;
import modules.shared.application.Primitives;

import static core.config.Global.__;

import modules.connections.domain.enums.ConnectionsMessageEnum;
import modules.connections.domain.entities.ConnectionEntity;
import modules.connections.infrastructure.factories.TargetSessionFactory;
import modules.connections.infrastructure.repositories.ConnectionsReaderSqliteRepository;
import modules.connections.domain.exceptions.ConnectionsException;

import modules.history.domain.enums.QueryHistoryConfigEnum;
import modules.history.application.recordqueryhistory.RecordQueryHistoryDto;
import modules.history.application.recordqueryhistory.RecordQueryHistoryService;

import modules.query.domain.enums.QueryKeyEnum;
import modules.query.domain.enums.QueryMessageEnum;
import modules.query.domain.entities.SqlStatementEntity;
import modules.query.domain.services.SqlScriptChunker;
import modules.query.infrastructure.components.RunningStatementsComponent;
import modules.query.infrastructure.components.SqlFileProgressTracker;
import modules.query.infrastructure.components.SqlFileReaderComponent;
import modules.query.infrastructure.repositories.QueryExecutorJdbcRepository;
import modules.query.domain.exceptions.QueryException;

/**
 * "Execute Query(s) From A File" de SQLyog: ejecuta un .sql sin cargarlo en el editor (un volcado de cientos de MB),
 * leyendolo por trozos y sentencia a sentencia, en la sesion del DTO con su BD activa. Sirve para cualquier motor SQL
 * (el troceo sigue al dialecto: DELIMITER en MySQL, $$ en PostgreSQL, GO en SQL Server). En una conexion de produccion
 * pide confirmacion (428). Progreso por SqlFileProgressTracker y Parar por RunningStatementsComponent, con la clave de
 * la sesion.
 * <p>
 * EXCEPCION DOCUMENTADA a «los servicios no capturan» (como la copia de BD, DDD 17): el error de CADA sentencia es un
 * resultado (se cuenta y se guarda) y, sin "Parar en el primer error", la ejecucion sigue con la siguiente. Lo que
 * impide ejecutar el fichero (la conexion, la sesion, leer el fichero) sube hasta el controller.
 */
public final class ExecuteSqlFileService {

    private static final int MAX_KEPT_ERRORS = 50;
    private static final int ERROR_SQL_CHARS = 300;

    private final RunningStatementsComponent runningStatementsComponent;
    private final SqlFileProgressTracker sqlFileProgressTracker;

    private ExecuteSqlFileService() {
        this.runningStatementsComponent = RunningStatementsComponent.getInstance();
        this.sqlFileProgressTracker = SqlFileProgressTracker.getInstance();
    }

    public static ExecuteSqlFileService getInstance() {
        return new ExecuteSqlFileService();
    }

    /**
     * @throws QueryException
     * @throws ConnectionsException
     * @throws SQLException si no se puede abrir la sesion o activar la BD
     */
    public ExecuteSqlFileResultDto invoke(ExecuteSqlFileDto executeSqlFileDto) throws SQLException {
        failIfWrongInput(executeSqlFileDto);
        ConnectionEntity connectionEntity = ConnectionsReaderSqliteRepository.getInstance().getConnectionByUuid(executeSqlFileDto.getConnectionUuid());
        if (connectionEntity == null) {
            throw ConnectionsException.notFoundCustom(__(ConnectionsMessageEnum.CONNECTION_X_NOT_FOUND, executeSqlFileDto.getConnectionUuid()));
        }
        Path filePath = tryToGetFilePathOrFail(executeSqlFileDto.getFilePath());
        if (connectionEntity.isProduction() && !executeSqlFileDto.isConfirmed()) {
            throw QueryException.preconditionRequiredCustom(__(
                QueryMessageEnum.PRODUCTION_X_RUN_FILE_X, connectionEntity.getConnectionName(), filePath.getFileName()
            ));
        }

        String sessionKey = executeSqlFileDto.getSessionKey();
        long startNanos = System.nanoTime();
        runningStatementsComponent.startRun(sessionKey);
        RunState runState = new RunState();
        SqlScriptChunker sqlScriptChunker = SqlScriptChunker.getInstance(connectionEntity.getDbEngine());
        // la sesion y el USE van dentro: si fallan, el historial tambien lo apunta (como ExecuteQueryService)
        runState.isAborted = true;
        try {
            Connection pdo = TargetSessionFactory.getInstance().getSession(executeSqlFileDto.getConnectionUuid(), sessionKey);
            QueryExecutorJdbcRepository queryExecutorJdbcRepository = QueryExecutorJdbcRepository.getInstance(pdo, SqlDialect.getInstance(connectionEntity.getDbEngine()));
            if (!executeSqlFileDto.getDatabaseName().isEmpty()) queryExecutorJdbcRepository.useDatabase(executeSqlFileDto.getDatabaseName());
            runState.isAborted = false;
            executeFile(queryExecutorJdbcRepository, executeSqlFileDto, filePath, sqlScriptChunker, runState);
        }
        catch (UncheckedIOException e) {
            throw QueryException.badRequestCustom(__(QueryMessageEnum.CANNOT_READ_X_X, filePath, e.getCause().getMessage()));
        }
        finally {
            RecordQueryHistoryService.getInstance().invoke(RecordQueryHistoryDto.fromPrimitives(Primitives.of(
                "connectionUuid", executeSqlFileDto.getConnectionUuid(),
                "databaseName", executeSqlFileDto.getDatabaseName(),
                "sqlQuery", QueryHistoryConfigEnum.SQL_FILE_PREFIX + filePath,
                "elapsedMs", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos),
                "rowsCount", runState.executedCount,
                "hasError", runState.getErrorCount() > 0 || runState.isAborted
            )));
        }
        return ExecuteSqlFileResultDto.fromPrimitives(Primitives.of(
            "executedCount", runState.executedCount,
            "errorCount", runState.getErrorCount(),
            "errors", runState.errors,
            "isCancelled", runState.isCancelled,
            "isAborted", runState.isAborted,
            "elapsedMs", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos)
        ));
    }

    /**
     * El fichero a trozos: las sentencias completas de cada trozo se lanzan en cuanto se tienen.
     * @throws QueryException si el fichero no se puede leer
     */
    private void executeFile(
        QueryExecutorJdbcRepository queryExecutorJdbcRepository,
        ExecuteSqlFileDto executeSqlFileDto,
        Path filePath,
        SqlScriptChunker sqlScriptChunker,
        RunState runState
    ) {
        try (SqlFileReaderComponent sqlFileReaderComponent = openFileOrFail(filePath)) {
            long totalChars = sqlFileReaderComponent.getSizeBytes();
            long readChars = 0;
            while (!runState.isStopped()) {
                String chunk = sqlFileReaderComponent.readChunk();
                if (chunk == null) {
                    sqlScriptChunker.finish();
                }
                else {
                    sqlScriptChunker.addChunk(chunk);
                    readChars += chunk.length();
                }
                executeStatements(queryExecutorJdbcRepository, executeSqlFileDto, sqlScriptChunker.getCompleteStatements(), runState);
                sqlFileProgressTracker.notifyProgress(executeSqlFileDto.getSessionKey(), Math.min(readChars, totalChars), totalChars, runState.executedCount, runState.getErrorCount());
                if (chunk == null) break;
            }
        }
    }

    /**
     * @throws QueryException si la ruta no es valida en este sistema (caracteres prohibidos)
     */
    private Path tryToGetFilePathOrFail(String filePath) {
        try {
            return Path.of(filePath);
        }
        catch (InvalidPathException e) {
            throw QueryException.badRequestCustom(__(QueryMessageEnum.CANNOT_READ_X_X, filePath, e.getMessage()));
        }
    }

    private void executeStatements(
        QueryExecutorJdbcRepository queryExecutorJdbcRepository,
        ExecuteSqlFileDto executeSqlFileDto,
        List<SqlStatementEntity> statements,
        RunState runState
    ) {
        String sessionKey = executeSqlFileDto.getSessionKey();
        for (SqlStatementEntity statement : statements) {
            if (runningStatementsComponent.isCancelled(sessionKey)) {
                runState.isCancelled = true;
                return;
            }
            try {
                queryExecutorJdbcRepository.executeStatement(statement.getSql(), 1, sessionKey);
                runState.executedCount++;
            }
            catch (SQLException e) {
                if (runningStatementsComponent.isCancelled(sessionKey)) {
                    runState.isCancelled = true;
                    return;
                }
                runState.addError(statement.getSql(), e.getMessage());
                if (executeSqlFileDto.isAbortOnError()) {
                    runState.isAborted = true;
                    return;
                }
            }
        }
    }

    /**
     * @throws QueryException si no se puede abrir el fichero
     */
    private SqlFileReaderComponent openFileOrFail(Path filePath) {
        try {
            return SqlFileReaderComponent.open(filePath);
        }
        catch (UncheckedIOException e) {
            throw QueryException.badRequestCustom(__(QueryMessageEnum.CANNOT_READ_X_X, filePath, e.getCause().getMessage()));
        }
    }

    /**
     * @throws QueryException
     */
    private void failIfWrongInput(ExecuteSqlFileDto executeSqlFileDto) {
        if (executeSqlFileDto.getConnectionUuid().isEmpty()) {
            throw QueryException.badRequestCustom(__(QueryMessageEnum.FIELD_IS_REQUIRED_X, "connectionUuid"));
        }
        if (executeSqlFileDto.getSessionKey().isEmpty()) {
            throw QueryException.badRequestCustom(__(QueryMessageEnum.FIELD_IS_REQUIRED_X, "sessionKey"));
        }
        if (executeSqlFileDto.getFilePath().isEmpty()) {
            throw QueryException.badRequestCustom(__(QueryMessageEnum.FIELD_IS_REQUIRED_X, "filePath"));
        }
    }

    /**
     * Lo que va pasando: los primeros MAX_KEPT_ERRORS errores con su SQL recortado; del resto solo se cuentan.
     */
    private static final class RunState {

        private long executedCount;
        private final List<Map<String, Object>> errors = new ArrayList<>();
        private long droppedErrors;
        private boolean isCancelled;
        private boolean isAborted;

        private boolean isStopped() {
            return isCancelled || isAborted;
        }

        private long getErrorCount() {
            return errors.size() + droppedErrors;
        }

        private void addError(String sql, String message) {
            if (errors.size() >= MAX_KEPT_ERRORS) {
                droppedErrors++;
                return;
            }
            String shortSql = sql.length() > ERROR_SQL_CHARS ? sql.substring(0, ERROR_SQL_CHARS) + "..." : sql;
            errors.add(Primitives.of(QueryKeyEnum.SQL, shortSql, QueryKeyEnum.MESSAGE, message == null ? "" : message));
        }
    }
}
