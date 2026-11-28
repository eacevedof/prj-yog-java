package modules.dbcopy.application.renamedatabase;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import modules.shared.application.Primitives;
import modules.shared.infrastructure.components.uuid.UuidGenerator;
import static modules.shared.application.Primitives.getTrimmedString;

import static core.config.Global.__;

import modules.connections.domain.enums.ConnectionsMessageEnum;
import modules.connections.domain.enums.DbEngineEnum;
import modules.connections.domain.entities.ConnectionEntity;
import modules.connections.infrastructure.factories.TargetSessionFactory;
import modules.connections.infrastructure.repositories.ConnectionsReaderSqliteRepository;
import modules.connections.domain.exceptions.ConnectionsException;

import modules.history.domain.enums.QueryHistoryConfigEnum;
import modules.history.application.recordqueryhistory.RecordQueryHistoryDto;
import modules.history.application.recordqueryhistory.RecordQueryHistoryService;

import modules.query.application.executesqlfile.ExecuteSqlFileDto;
import modules.query.application.executesqlfile.ExecuteSqlFileResultDto;
import modules.query.application.executesqlfile.ExecuteSqlFileService;

import modules.dbcopy.domain.enums.DbCopyConfigEnum;
import modules.dbcopy.domain.enums.DbCopyKeyEnum;
import modules.dbcopy.domain.enums.DbCopyMessageEnum;
import modules.dbcopy.domain.enums.DbCopyObjectTypeEnum;
import modules.dbcopy.domain.enums.RenameDatabasePhaseEnum;
import modules.dbcopy.domain.enums.SqlDumpModeEnum;
import modules.dbcopy.domain.services.DatabaseNameValidator;
import modules.dbcopy.application.exportsqldump.ExportSqlDumpDto;
import modules.dbcopy.application.exportsqldump.ExportSqlDumpResultDto;
import modules.dbcopy.application.exportsqldump.ExportSqlDumpService;
import modules.dbcopy.infrastructure.components.DbCopyProgressTracker;
import modules.dbcopy.infrastructure.repositories.DbCopyReaderMysqlRepository;
import modules.dbcopy.infrastructure.repositories.DbCopyWriterMysqlRepository;
import modules.dbcopy.domain.exceptions.DbCopyException;

/**
 * "Renombrar BD" (MySQL/MariaDB no tienen RENAME DATABASE): vuelca la original entera a un .sql (que se queda como
 * copia de seguridad), crea la nueva con el mismo juego de caracteres, carga en ella el volcado (ExecuteSqlFileService,
 * parando en el primer error), comprueba que tiene los mismos objetos y las mismas filas en cada tabla y, si se pidio y
 * todo cuadra, borra la original. Si la carga o la comprobacion fallan, la nueva se borra y la original no se toca.
 * En una conexion de produccion pide confirmacion (428) antes de empezar.
 */
public final class RenameDatabaseService {

    private static final Map<String, String> OBJECTS_KEY_BY_TYPE = Map.of(
        DbCopyObjectTypeEnum.TABLE, DbCopyKeyEnum.TABLES,
        DbCopyObjectTypeEnum.VIEW, DbCopyKeyEnum.VIEWS,
        DbCopyObjectTypeEnum.PROCEDURE, DbCopyKeyEnum.PROCEDURES,
        DbCopyObjectTypeEnum.FUNCTION, DbCopyKeyEnum.FUNCTIONS,
        DbCopyObjectTypeEnum.TRIGGER, DbCopyKeyEnum.TRIGGERS,
        DbCopyObjectTypeEnum.EVENT, DbCopyKeyEnum.EVENTS
    );

    private final TargetSessionFactory targetSessionFactory = TargetSessionFactory.getInstance();
    private final DbCopyProgressTracker dbCopyProgressTracker = DbCopyProgressTracker.getInstance();

    private RenameDatabaseService() {
    }

    public static RenameDatabaseService getInstance() {
        return new RenameDatabaseService();
    }

    /**
     * @throws DbCopyException
     * @throws ConnectionsException
     * @throws modules.query.domain.exceptions.QueryException
     * @throws SQLException si la BD falla fuera de la carga (sesion, CREATE, DROP)
     */
    public RenameDatabaseResultDto invoke(RenameDatabaseDto renameDatabaseDto) throws SQLException {
        failIfWrongInput(renameDatabaseDto);
        ConnectionEntity connectionEntity = tryToGetMysqlConnectionOrFail(renameDatabaseDto.getConnectionUuid());
        failIfProductionNotConfirmed(connectionEntity, renameDatabaseDto);

        long startNanos = System.nanoTime();
        String renameId = renameDatabaseDto.getRenameId();
        String databaseName = renameDatabaseDto.getDatabaseName();
        String newDatabaseName = renameDatabaseDto.getNewDatabaseName();
        String connectionUuid = connectionEntity.getConnectionUuid();
        String sessionKeyPrefix = UuidGenerator.getInstance().getUniqueIdWithPrefix(DbCopyConfigEnum.SESSION_PREFIX + "rename-");
        try {
            DbCopyReaderMysqlRepository dbCopyReaderMysqlRepository = DbCopyReaderMysqlRepository.getInstance(
                targetSessionFactory.getSessionPdo(connectionUuid, sessionKeyPrefix + TargetSessionFactory.SESSION_KEY_SEPARATOR + "check")
            );
            failIfDatabasesAreWrong(dbCopyReaderMysqlRepository, databaseName, newDatabaseName);
            Map<String, Object> originalObjects = dbCopyReaderMysqlRepository.getObjects(databaseName);
            Map<String, Object> charset = dbCopyReaderMysqlRepository.getDatabaseCharset(databaseName);

            dbCopyProgressTracker.notifyPhaseStarted(renameId, RenameDatabasePhaseEnum.DUMP);
            ExportSqlDumpResultDto exportSqlDumpResultDto = dumpOriginal(renameDatabaseDto, originalObjects);

            dbCopyProgressTracker.notifyPhaseStarted(renameId, RenameDatabasePhaseEnum.CREATE);
            DbCopyWriterMysqlRepository dbCopyWriterMysqlRepository = DbCopyWriterMysqlRepository.getInstance(
                targetSessionFactory.getSessionPdo(connectionUuid, sessionKeyPrefix + TargetSessionFactory.SESSION_KEY_SEPARATOR + "write")
            );
            dbCopyWriterMysqlRepository.createDatabase(
                newDatabaseName, getTrimmedString(charset.get(DbCopyKeyEnum.CHARACTER_SET)), getTrimmedString(charset.get(DbCopyKeyEnum.COLLATION))
            );

            long rowCount;
            try {
                dbCopyProgressTracker.notifyPhaseStarted(renameId, RenameDatabasePhaseEnum.LOAD);
                String loadError = loadDump(connectionUuid, sessionKeyPrefix, newDatabaseName, exportSqlDumpResultDto.getFilePath());
                if (!loadError.isEmpty()) {
                    throw DbCopyException.badRequestCustom(__(DbCopyMessageEnum.RENAME_LOAD_FAILED_X_X, newDatabaseName, loadError));
                }

                dbCopyProgressTracker.notifyPhaseStarted(renameId, RenameDatabasePhaseEnum.VERIFY);
                List<String> mismatches = new ArrayList<>();
                rowCount = getRowCountOrMismatches(dbCopyReaderMysqlRepository, databaseName, newDatabaseName, originalObjects, mismatches);
                if (!mismatches.isEmpty()) {
                    throw DbCopyException.badRequestCustom(__(DbCopyMessageEnum.RENAME_MISMATCH_X_X, newDatabaseName, String.join("; ", mismatches)));
                }
            }
            catch (SQLException | RuntimeException exception) {
                dropHalfLoadedDatabase(dbCopyWriterMysqlRepository, newDatabaseName, exception);
                throw exception;
            }

            if (renameDatabaseDto.isDropOriginal()) {
                dbCopyProgressTracker.notifyPhaseStarted(renameId, RenameDatabasePhaseEnum.DROP);
                dbCopyWriterMysqlRepository.dropDatabase(databaseName);
            }
            RecordQueryHistoryService.getInstance().invoke(RecordQueryHistoryDto.fromPrimitives(Primitives.of(
                "connectionUuid", connectionUuid,
                "databaseName", newDatabaseName,
                "sqlQuery", (renameDatabaseDto.isDropOriginal()
                    ? QueryHistoryConfigEnum.RENAME_DATABASE_DROPPED_X_X
                    : QueryHistoryConfigEnum.RENAME_DATABASE_X_X).formatted(databaseName, newDatabaseName),
                "elapsedMs", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos),
                "rowsCount", rowCount
            )));
            return RenameDatabaseResultDto.fromPrimitives(Primitives.of(
                "newDatabaseName", newDatabaseName,
                "filePath", exportSqlDumpResultDto.getFilePath(),
                "objectCount", getObjectCount(originalObjects),
                "rowCount", rowCount,
                "isOriginalDropped", renameDatabaseDto.isDropOriginal(),
                "elapsedMs", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos)
            ));
        }
        finally {
            targetSessionFactory.closeSessions(sessionKeyPrefix);
        }
    }

    /**
     * Compensacion: la BD nueva ya existe y la carga o la comprobacion fallaron (con error devuelto o lanzado), asi
     * que se borra para no dejarla huerfana. Si el DROP tambien falla, va como suprimida del error original, que es
     * el que sube.
     */
    private void dropHalfLoadedDatabase(DbCopyWriterMysqlRepository dbCopyWriterMysqlRepository, String newDatabaseName, Exception exception) {
        try {
            dbCopyWriterMysqlRepository.dropDatabase(newDatabaseName);
        }
        catch (SQLException | RuntimeException dropException) {
            exception.addSuppressed(dropException);
        }
    }

    /**
     * Todo, estructura y datos, en una foto coherente; sin USE ni CREATE DATABASE (se carga en la nueva) y con los
     * DEFINER de siempre.
     * @throws DbCopyException si algun objeto no se pudo volcar o se paro: entonces no se crea nada
     */
    private ExportSqlDumpResultDto dumpOriginal(RenameDatabaseDto renameDatabaseDto, Map<String, Object> originalObjects) throws SQLException {
        Map<String, Object> objectsByType = new LinkedHashMap<>();
        for (String objectType : DbCopyObjectTypeEnum.VALUES) objectsByType.put(objectType, getNames(originalObjects, objectType));
        ExportSqlDumpResultDto exportSqlDumpResultDto = ExportSqlDumpService.getInstance().invoke(ExportSqlDumpDto.fromPrimitives(Primitives.of(
            "dumpId", renameDatabaseDto.getRenameId(),
            "connectionUuid", renameDatabaseDto.getConnectionUuid(),
            "databaseName", renameDatabaseDto.getDatabaseName(),
            "filePath", renameDatabaseDto.getFilePath(),
            "dumpMode", SqlDumpModeEnum.STRUCTURE_AND_DATA,
            "objects", objectsByType,
            "isSingleTransaction", true,
            "isForeignKeyChecksOff", true,
            "isBulkInsert", true
        )));
        if (exportSqlDumpResultDto.getErrorCount() > 0 || exportSqlDumpResultDto.isCancelled()) {
            throw DbCopyException.badRequestCustom(__(DbCopyMessageEnum.RENAME_DUMP_FAILED_X, exportSqlDumpResultDto.getErrorCount()));
        }
        return exportSqlDumpResultDto;
    }

    /**
     * El volcado en la BD nueva, parando en el primer error. Vacio si fue bien; si no, el primer error.
     */
    private String loadDump(String connectionUuid, String sessionKeyPrefix, String newDatabaseName, String filePath) throws SQLException {
        ExecuteSqlFileResultDto executeSqlFileResultDto = ExecuteSqlFileService.getInstance().invoke(ExecuteSqlFileDto.fromPrimitives(Primitives.of(
            "connectionUuid", connectionUuid,
            "sessionKey", sessionKeyPrefix + TargetSessionFactory.SESSION_KEY_SEPARATOR + "load",
            "databaseName", newDatabaseName,
            "filePath", filePath,
            "isAbortOnError", true,
            "isConfirmed", true
        )));
        if (executeSqlFileResultDto.getErrorCount() == 0 && !executeSqlFileResultDto.isAborted() && !executeSqlFileResultDto.isCancelled()) return "";
        if (executeSqlFileResultDto.getErrors().isEmpty()) return "cancelled";
        return getTrimmedString(executeSqlFileResultDto.getErrors().getFirst().get("message"));
    }

    /**
     * Mismos objetos de cada tipo y mismas filas (COUNT) en cada tabla; lo que no cuadra va a mismatches. Devuelve las
     * filas que tiene la nueva.
     */
    private long getRowCountOrMismatches(
        DbCopyReaderMysqlRepository dbCopyReaderMysqlRepository,
        String databaseName,
        String newDatabaseName,
        Map<String, Object> originalObjects,
        List<String> mismatches
    ) throws SQLException {
        Map<String, Object> newObjects = dbCopyReaderMysqlRepository.getObjects(newDatabaseName);
        for (String objectType : DbCopyObjectTypeEnum.VALUES) {
            List<String> originalNames = getNames(originalObjects, objectType);
            List<String> newNames = getNames(newObjects, objectType);
            if (!originalNames.equals(newNames)) mismatches.add(objectType + ": " + originalNames.size() + " -> " + newNames.size());
        }
        long rowCount = 0;
        for (String tableName : getNames(originalObjects, DbCopyObjectTypeEnum.TABLE)) {
            long originalRows = dbCopyReaderMysqlRepository.getExactRowCount(databaseName, tableName);
            long newRows = getNames(newObjects, DbCopyObjectTypeEnum.TABLE).contains(tableName)
                ? dbCopyReaderMysqlRepository.getExactRowCount(newDatabaseName, tableName)
                : -1;
            if (originalRows != newRows) mismatches.add(tableName + ": " + originalRows + " -> " + newRows);
            rowCount += Math.max(newRows, 0);
        }
        return rowCount;
    }

    private static List<String> getNames(Map<String, Object> objects, String objectType) {
        List<String> names = new ArrayList<>();
        if (objects.get(OBJECTS_KEY_BY_TYPE.get(objectType)) instanceof List<?> rawNames) {
            for (Object rawName : rawNames) names.add(getTrimmedString(rawName));
        }
        return names;
    }

    private static int getObjectCount(Map<String, Object> objects) {
        int objectCount = 0;
        for (String objectType : DbCopyObjectTypeEnum.VALUES) objectCount += getNames(objects, objectType).size();
        return objectCount;
    }

    /**
     * @throws DbCopyException
     */
    private void failIfDatabasesAreWrong(DbCopyReaderMysqlRepository dbCopyReaderMysqlRepository, String databaseName, String newDatabaseName)
        throws SQLException {
        if (!dbCopyReaderMysqlRepository.isDatabaseExisting(databaseName)) {
            throw DbCopyException.badRequestCustom(__(DbCopyMessageEnum.DATABASE_X_NOT_FOUND, databaseName));
        }
        if (dbCopyReaderMysqlRepository.isDatabaseExisting(newDatabaseName)) {
            throw DbCopyException.badRequestCustom(__(DbCopyMessageEnum.DATABASE_X_ALREADY_EXISTS, newDatabaseName));
        }
    }

    /**
     * @throws DbCopyException
     */
    private void failIfProductionNotConfirmed(ConnectionEntity connectionEntity, RenameDatabaseDto renameDatabaseDto) {
        if (renameDatabaseDto.isConfirmed() || !connectionEntity.isProduction()) return;
        String message = renameDatabaseDto.isDropOriginal() ? DbCopyMessageEnum.PRODUCTION_X_RENAME_DROP_X_X : DbCopyMessageEnum.PRODUCTION_X_RENAME_X_X;
        throw DbCopyException.preconditionRequiredCustom(__(
            message, connectionEntity.getConnectionName(), renameDatabaseDto.getDatabaseName(), renameDatabaseDto.getNewDatabaseName()
        ));
    }

    /**
     * @throws ConnectionsException si no existe
     * @throws DbCopyException si no es MySQL/MariaDB
     */
    private ConnectionEntity tryToGetMysqlConnectionOrFail(String connectionUuid) {
        ConnectionEntity connectionEntity = ConnectionsReaderSqliteRepository.getInstance().getConnectionByUuid(connectionUuid);
        if (connectionEntity == null) {
            throw ConnectionsException.notFoundCustom(__(ConnectionsMessageEnum.CONNECTION_X_NOT_FOUND, connectionUuid));
        }
        if (!DbEngineEnum.MYSQL.equals(connectionEntity.getDbEngine())) {
            throw DbCopyException.badRequestCustom(__(DbCopyMessageEnum.DUMP_ONLY_MYSQL_X_X, connectionEntity.getConnectionName(), connectionEntity.getDbEngine()));
        }
        return connectionEntity;
    }

    /**
     * @throws DbCopyException
     */
    private void failIfWrongInput(RenameDatabaseDto renameDatabaseDto) {
        if (renameDatabaseDto.getConnectionUuid().isEmpty()) {
            throw DbCopyException.badRequestCustom(__(DbCopyMessageEnum.FIELD_IS_REQUIRED_X, "connectionUuid"));
        }
        if (renameDatabaseDto.getDatabaseName().isEmpty()) {
            throw DbCopyException.badRequestCustom(__(DbCopyMessageEnum.FIELD_IS_REQUIRED_X, "databaseName"));
        }
        if (renameDatabaseDto.getFilePath().isEmpty()) {
            throw DbCopyException.badRequestCustom(__(DbCopyMessageEnum.FIELD_IS_REQUIRED_X, "filePath"));
        }
        String newDatabaseName = renameDatabaseDto.getNewDatabaseName();
        if (!DatabaseNameValidator.getInstance().isValid(newDatabaseName)) {
            throw DbCopyException.badRequestCustom(__(DbCopyMessageEnum.WRONG_DATABASE_NAME_X, newDatabaseName));
        }
        if (newDatabaseName.equalsIgnoreCase(renameDatabaseDto.getDatabaseName())) {
            throw DbCopyException.badRequestCustom(__(DbCopyMessageEnum.SAME_DATABASE_NAME_X, newDatabaseName));
        }
    }
}
