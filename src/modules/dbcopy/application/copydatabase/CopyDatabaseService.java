package modules.dbcopy.application.copydatabase;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import modules.shared.application.Primitives;
import modules.shared.infrastructure.components.uuid.UuidGenerator;
import static modules.shared.application.Primitives.getBool;
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

import modules.dbcopy.domain.enums.DbCopyCellKindEnum;
import modules.dbcopy.domain.enums.DbCopyConfigEnum;
import modules.dbcopy.domain.enums.DbCopyKeyEnum;
import modules.dbcopy.domain.enums.DbCopyMessageEnum;
import modules.dbcopy.domain.enums.DbCopyObjectTypeEnum;
import modules.dbcopy.domain.services.DbCopySqlBuilder;
import modules.dbcopy.infrastructure.components.DbCopyProgressTracker;
import modules.dbcopy.infrastructure.repositories.DbCopyReaderMysqlRepository;
import modules.dbcopy.infrastructure.repositories.DbCopyWriterMysqlRepository;
import modules.dbcopy.domain.exceptions.DbCopyException;

/**
 * Feature 29, "Copy Database To Different Host/Database" de SQLyog: copia tablas (estructura y datos), vistas,
 * procedimientos, funciones, triggers y eventos de una BD MySQL a otra, en el mismo servidor o en otro (directo, SSH o
 * tunel HTTP). Va objeto a objeto: un fallo queda en el resumen y la copia sigue con el siguiente. Las sesiones de
 * origen y destino son propias de cada copia y se cierran al acabar. Un destino de produccion pide confirmacion (428).
 * Cada invoke trabaja sobre una instancia nueva (getInstance), por eso el DTO y las sesiones van en atributos. El
 * progreso y el Parar van por DbCopyProgressTracker con el copyId del DTO.
 * <p>
 * EXCEPCION DOCUMENTADA a «los servicios no capturan» (decision de Eduardo, 2026-11-25, DDD 17): copyObject y
 * copyViews capturan el error de CADA objeto y lo guardan en el resumen. Por que: el fallo de un objeto (una vista que
 * apunta a una tabla no marcada, una rutina sin permisos) es un resultado de la copia, no un fallo del caso de uso;
 * como en SQLyog, la copia sigue con los demas y el resumen dice cual fallo. Alcance: solo el error por objeto. Lo que
 * impide la copia entera (sesiones, BD destino, lectura de objetos) sube como SQLException hasta el controller (DDD 19).
 * <p>
 * Si origen y destino no son los dos MySQL, la copia (solo tablas, con los tipos traducidos) la hace
 * CopyBetweenEnginesService.
 */
public final class CopyDatabaseService {

    private final TargetSessionFactory targetSessionFactory;
    private final DbCopySqlBuilder dbCopySqlBuilder;
    private final DbCopyProgressTracker dbCopyProgressTracker;

    private CopyDatabaseDto copyDatabaseDto;
    private DbCopyReaderMysqlRepository dbCopyReaderMysqlRepository;
    private DbCopyWriterMysqlRepository dbCopyWriterMysqlRepository;
    private int maxStatementChars;

    private CopyDatabaseService() {
        this.targetSessionFactory = TargetSessionFactory.getInstance();
        this.dbCopySqlBuilder = DbCopySqlBuilder.getInstance();
        this.dbCopyProgressTracker = DbCopyProgressTracker.getInstance();
    }

    public static CopyDatabaseService getInstance() {
        return new CopyDatabaseService();
    }

    /**
     * @throws DbCopyException
     * @throws ConnectionsException
     * @throws SQLException si no se pueden abrir las sesiones, preparar la BD destino o leer los objetos del origen
     */
    public CopyDatabaseResultDto invoke(CopyDatabaseDto copyDatabaseDto) throws SQLException {
        this.copyDatabaseDto = copyDatabaseDto;
        failIfWrongInput();
        ConnectionEntity sourceConnectionEntity = tryToGetSqlConnectionOrFail(copyDatabaseDto.getSourceConnectionUuid(), copyDatabaseDto.getSourceDatabase(), true);
        ConnectionEntity targetConnectionEntity = tryToGetSqlConnectionOrFail(copyDatabaseDto.getTargetConnectionUuid(), copyDatabaseDto.getTargetDatabase(), false);
        boolean isSameDatabaseName = copyDatabaseDto.getSourceDatabase().equalsIgnoreCase(copyDatabaseDto.getTargetDatabase());
        if (isSameDatabaseName && sourceConnectionEntity.getConnectionUuid().equals(targetConnectionEntity.getConnectionUuid())) {
            throw DbCopyException.badRequestCustom(__(DbCopyMessageEnum.SAME_SOURCE_AND_TARGET));
        }
        boolean isMysqlToMysql = DbEngineEnum.MYSQL.equals(sourceConnectionEntity.getDbEngine()) && DbEngineEnum.MYSQL.equals(targetConnectionEntity.getDbEngine());
        if (!isMysqlToMysql) {
            // dos conexiones guardadas del mismo motor al mismo servidor y BD: el DROP borraria el origen
            if (isSameDatabaseName && isSameServer(sourceConnectionEntity, targetConnectionEntity)) {
                throw DbCopyException.badRequestCustom(__(DbCopyMessageEnum.SAME_SOURCE_AND_TARGET));
            }
            return CopyBetweenEnginesService.getInstance().invoke(copyDatabaseDto, sourceConnectionEntity, targetConnectionEntity);
        }

        long startNanos = System.nanoTime();
        String sessionKeyPrefix = UuidGenerator.getInstance().getUniqueIdWithPrefix(DbCopyConfigEnum.SESSION_PREFIX);
        List<Map<String, Object>> objectResults = new ArrayList<>();
        boolean isCancelled = false;
        try {
            dbCopyReaderMysqlRepository = DbCopyReaderMysqlRepository.getInstance(targetSessionFactory.getSessionPdo(
                sourceConnectionEntity.getConnectionUuid(), sessionKeyPrefix + TargetSessionFactory.SESSION_KEY_SEPARATOR + "source"
            ));
            String targetSessionKey = sessionKeyPrefix + TargetSessionFactory.SESSION_KEY_SEPARATOR + "target";
            DbCopyReaderMysqlRepository targetDbCopyReaderMysqlRepository = DbCopyReaderMysqlRepository.getInstance(
                targetSessionFactory.getSessionPdo(targetConnectionEntity.getConnectionUuid(), targetSessionKey)
            );
            dbCopyWriterMysqlRepository = DbCopyWriterMysqlRepository.getInstance(
                targetSessionFactory.getSessionPdo(targetConnectionEntity.getConnectionUuid(), targetSessionKey)
            );
            // dos conexiones guardadas distintas pueden llegar al mismo servidor: copiar una BD sobre si misma la borraria
            if (isSameDatabaseName && dbCopyReaderMysqlRepository.getServerIdentity().equals(targetDbCopyReaderMysqlRepository.getServerIdentity())) {
                throw DbCopyException.badRequestCustom(__(DbCopyMessageEnum.SAME_SOURCE_AND_TARGET));
            }
            if (targetConnectionEntity.isProduction() && !copyDatabaseDto.isConfirmed()) {
                throw DbCopyException.preconditionRequiredCustom(__(
                    DbCopyMessageEnum.TARGET_IS_PRODUCTION_X_X, targetConnectionEntity.getConnectionName(), copyDatabaseDto.getTargetDatabase()
                ));
            }
            List<CopyItem> copyItems = getCopyItems();
            prepareTarget(targetDbCopyReaderMysqlRepository);
            maxStatementChars = targetDbCopyReaderMysqlRepository.getMaxStatementChars();

            List<CopyItem> pendingViews = new ArrayList<>();
            for (int i = 0; i < copyItems.size(); i++) {
                if (dbCopyProgressTracker.isCancelled(copyDatabaseDto.getCopyId())) {
                    isCancelled = true;
                    break;
                }
                CopyItem copyItem = copyItems.get(i);
                dbCopyProgressTracker.notifyObjectStarted(copyDatabaseDto.getCopyId(), copyItem.objectType(), copyItem.objectName(), i + 1, copyItems.size());
                if (DbCopyObjectTypeEnum.VIEW.equals(copyItem.objectType())) {
                    pendingViews.add(copyItem);
                    boolean isLastView = i + 1 == copyItems.size() || !DbCopyObjectTypeEnum.VIEW.equals(copyItems.get(i + 1).objectType());
                    if (isLastView) objectResults.addAll(copyViews(pendingViews));
                    continue;
                }
                CopyOutcome copyOutcome = copyObject(copyItem);
                objectResults.add(getObjectResult(copyItem, copyOutcome));
                if (copyOutcome.isCancelled()) {
                    isCancelled = true;
                    break;
                }
            }
        }
        finally {
            targetSessionFactory.closeSessions(sessionKeyPrefix);
        }
        int okCount = (int) objectResults.stream().filter(objectResult -> getBool(objectResult.get(DbCopyKeyEnum.IS_OK))).count();
        long copiedRows = objectResults.stream().mapToLong(objectResult -> (long) objectResult.get(DbCopyKeyEnum.COPIED_ROWS)).sum();
        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;
        RecordQueryHistoryService.getInstance().invoke(RecordQueryHistoryDto.fromPrimitives(Primitives.of(
            "connectionUuid", targetConnectionEntity.getConnectionUuid(),
            "databaseName", copyDatabaseDto.getTargetDatabase(),
            "sqlQuery", QueryHistoryConfigEnum.COPY_DATABASE_X_X_X.formatted(
                sourceConnectionEntity.getConnectionName(), copyDatabaseDto.getSourceDatabase(), copyDatabaseDto.getTargetDatabase()
            ),
            "elapsedMs", elapsedMs,
            "rowsCount", copiedRows,
            "hasError", okCount < objectResults.size()
        )));
        return CopyDatabaseResultDto.fromPrimitives(Primitives.of(
            "objects", objectResults,
            "okCount", okCount,
            "errorCount", objectResults.size() - okCount,
            "copiedRows", copiedRows,
            "isCancelled", isCancelled,
            "elapsedMs", elapsedMs
        ));
    }

    /**
     * @throws DbCopyException
     */
    private void failIfWrongInput() {
        if (copyDatabaseDto.getSourceConnectionUuid().isEmpty()) {
            throw DbCopyException.badRequestCustom(__(DbCopyMessageEnum.FIELD_IS_REQUIRED_X, "sourceConnectionUuid"));
        }
        if (copyDatabaseDto.getSourceDatabase().isEmpty()) {
            throw DbCopyException.badRequestCustom(__(DbCopyMessageEnum.FIELD_IS_REQUIRED_X, "sourceDatabase"));
        }
        if (copyDatabaseDto.getTargetConnectionUuid().isEmpty()) {
            throw DbCopyException.badRequestCustom(__(DbCopyMessageEnum.FIELD_IS_REQUIRED_X, "targetConnectionUuid"));
        }
        if (copyDatabaseDto.getTargetDatabase().isEmpty()) {
            throw DbCopyException.badRequestCustom(__(DbCopyMessageEnum.FIELD_IS_REQUIRED_X, "targetDatabase"));
        }
        if (DbCopyObjectTypeEnum.VALUES.stream().allMatch(objectType -> copyDatabaseDto.getObjectNames(objectType).isEmpty())) {
            throw DbCopyException.badRequestCustom(__(DbCopyMessageEnum.NOTHING_SELECTED));
        }
    }

    /**
     * Un motor SQL; "Ficheros" solo como origen (sus tablas nuevas no tendrian fichero donde guardarse).
     * @throws DbCopyException si no es un motor SQL
     * @throws ConnectionsException si no existe
     */
    private ConnectionEntity tryToGetSqlConnectionOrFail(String connectionUuid, String databaseName, boolean isSource) {
        ConnectionEntity connectionEntity = ConnectionsReaderSqliteRepository.getInstance().getConnectionByUuid(connectionUuid);
        if (connectionEntity == null) {
            throw ConnectionsException.notFoundCustom(__(ConnectionsMessageEnum.CONNECTION_X_NOT_FOUND, connectionUuid));
        }
        String dbEngine = connectionEntity.getDbEngine();
        boolean isFilesSource = isSource && DbEngineEnum.FILES.equals(dbEngine);
        if (!DbEngineEnum.isSql(dbEngine) && !isFilesSource) {
            throw DbCopyException.badRequestCustom(__(
                DbCopyMessageEnum.NOT_SQL_ENGINE_X_X, connectionEntity.getConnectionName() + " (" + databaseName + ")", dbEngine
            ));
        }
        return connectionEntity;
    }

    /**
     * Mismo motor, host, puerto y BD de la conexion (en PostgreSQL la BD conectada; en SQLite el fichero).
     */
    private boolean isSameServer(ConnectionEntity sourceConnectionEntity, ConnectionEntity targetConnectionEntity) {
        return sourceConnectionEntity.getDbEngine().equals(targetConnectionEntity.getDbEngine())
            && sourceConnectionEntity.getHost().equalsIgnoreCase(targetConnectionEntity.getHost())
            && sourceConnectionEntity.getPort() == targetConnectionEntity.getPort()
            && sourceConnectionEntity.getDatabaseName().equalsIgnoreCase(targetConnectionEntity.getDatabaseName());
    }

    /**
     * Lo marcado, sin repetir y en orden de creacion (DbCopyObjectTypeEnum.VALUES). Con "Copiar triggers asociados",
     * tambien los triggers de las tablas marcadas aunque su carpeta no lo este.
     */
    private List<CopyItem> getCopyItems() throws SQLException {
        dbCopyReaderMysqlRepository.prepareSession(copyDatabaseDto.getSourceDatabase());
        Set<String> triggerNames = new LinkedHashSet<>(copyDatabaseDto.getObjectNames(DbCopyObjectTypeEnum.TRIGGER));
        if (copyDatabaseDto.isCopyAssociatedTriggers() && !copyDatabaseDto.getObjectNames(DbCopyObjectTypeEnum.TABLE).isEmpty()) {
            Set<String> tableNames = new HashSet<>(copyDatabaseDto.getObjectNames(DbCopyObjectTypeEnum.TABLE));
            Object triggerTables = dbCopyReaderMysqlRepository.getObjects(copyDatabaseDto.getSourceDatabase()).get(DbCopyKeyEnum.TRIGGER_TABLES);
            if (triggerTables instanceof Map<?, ?> tablesByTrigger) {
                tablesByTrigger.forEach((trigger, table) -> {
                    if (tableNames.contains(String.valueOf(table))) triggerNames.add(String.valueOf(trigger));
                });
            }
        }
        List<CopyItem> copyItems = new ArrayList<>();
        for (String objectType : DbCopyObjectTypeEnum.VALUES) {
            Set<String> objectNames = DbCopyObjectTypeEnum.TRIGGER.equals(objectType) ? triggerNames : new LinkedHashSet<>(copyDatabaseDto.getObjectNames(objectType));
            for (String objectName : objectNames) {
                if (!objectName.isEmpty()) copyItems.add(new CopyItem(objectType, objectName));
            }
        }
        return copyItems;
    }

    /**
     * Crea la BD destino si no existe (con el juego de caracteres del origen) y deja lista la sesion de escritura.
     */
    private void prepareTarget(DbCopyReaderMysqlRepository targetDbCopyReaderMysqlRepository) throws SQLException {
        String targetDatabase = copyDatabaseDto.getTargetDatabase();
        if (!targetDbCopyReaderMysqlRepository.isDatabaseExisting(targetDatabase)) {
            Map<String, Object> sourceCharset = dbCopyReaderMysqlRepository.getDatabaseCharset(copyDatabaseDto.getSourceDatabase());
            dbCopyWriterMysqlRepository.createDatabase(
                targetDatabase,
                getTrimmedString(sourceCharset.get(DbCopyKeyEnum.CHARACTER_SET)),
                getTrimmedString(sourceCharset.get(DbCopyKeyEnum.COLLATION))
            );
        }
        dbCopyWriterMysqlRepository.prepareSession(targetDatabase);
    }

    private CopyOutcome copyObject(CopyItem copyItem) {
        try {
            String createSql = tryToGetTargetCreateSqlOrFail(copyItem);
            if (DbCopyObjectTypeEnum.TABLE.equals(copyItem.objectType())) {
                if (copyDatabaseDto.isDropIfExists()) dbCopyWriterMysqlRepository.dropAndCreateTable(copyItem.objectName(), createSql);
                else dbCopyWriterMysqlRepository.createTableIfMissing(createSql);
                if (copyDatabaseDto.isStructureOnly()) return CopyOutcome.ok(0);
                return copyRows(copyItem.objectName());
            }
            if (copyDatabaseDto.isDropIfExists()) dbCopyWriterMysqlRepository.dropObject(copyItem.objectType(), copyItem.objectName());
            dbCopyWriterMysqlRepository.createObject(createSql);
            return CopyOutcome.ok(0);
        }
        catch (SQLException | DbCopyException e) {
            return CopyOutcome.failed(e.getMessage());
        }
    }

    /**
     * Las vistas pueden depender de otras vistas: se crean por pasadas, y la que falla se reintenta mientras alguna
     * otra salga adelante. Lo que sigue fallando se queda con su ultimo error.
     */
    private List<Map<String, Object>> copyViews(List<CopyItem> viewItems) {
        Map<CopyItem, CopyOutcome> outcomes = new LinkedHashMap<>();
        Map<CopyItem, String> createSqls = new LinkedHashMap<>();
        for (CopyItem viewItem : viewItems) {
            try {
                createSqls.put(viewItem, tryToGetTargetCreateSqlOrFail(viewItem));
                if (copyDatabaseDto.isDropIfExists()) dbCopyWriterMysqlRepository.dropObject(viewItem.objectType(), viewItem.objectName());
            }
            catch (SQLException | DbCopyException e) {
                outcomes.put(viewItem, CopyOutcome.failed(e.getMessage()));
            }
        }
        boolean isProgressing = true;
        while (isProgressing) {
            isProgressing = false;
            for (Map.Entry<CopyItem, String> entry : createSqls.entrySet()) {
                CopyOutcome previousOutcome = outcomes.get(entry.getKey());
                if (previousOutcome != null && previousOutcome.isOk()) continue;
                try {
                    dbCopyWriterMysqlRepository.createObject(entry.getValue());
                    outcomes.put(entry.getKey(), CopyOutcome.ok(0));
                    isProgressing = true;
                }
                catch (SQLException e) {
                    outcomes.put(entry.getKey(), CopyOutcome.failed(e.getMessage()));
                }
            }
        }
        List<Map<String, Object>> viewResults = new ArrayList<>();
        for (CopyItem viewItem : viewItems) {
            viewResults.add(getObjectResult(viewItem, outcomes.get(viewItem)));
        }
        return viewResults;
    }

    /**
     * El CREATE del origen listo para el destino: sin DEFINER si se pide (el usuario del origen puede no existir alli).
     * @throws DbCopyException si el servidor no da la definicion (sin permisos sobre la rutina)
     */
    private String tryToGetTargetCreateSqlOrFail(CopyItem copyItem) throws SQLException {
        String createSql = dbCopyReaderMysqlRepository.getCreateSql(copyItem.objectType(), copyDatabaseDto.getSourceDatabase(), copyItem.objectName()).strip();
        if (createSql.isEmpty()) {
            throw DbCopyException.serviceUnavailableCustom(__(DbCopyMessageEnum.NO_DEFINITION_X, copyItem.objectName()));
        }
        return copyDatabaseDto.isIgnoreDefiner() ? dbCopySqlBuilder.getWithoutDefiner(createSql) : createSql;
    }

    /**
     * Los datos de una tabla por paginas (keyset si tiene clave primaria), en INSERT de hasta maxStatementChars. Si se
     * cancela, lo leido hasta ahi se escribe y la tabla queda a medias.
     */
    private CopyOutcome copyRows(String tableName) throws SQLException {
        String sourceDatabase = copyDatabaseDto.getSourceDatabase();
        List<String> columnNames = new ArrayList<>();
        List<String> cellKinds = new ArrayList<>();
        Set<String> bitColumns = new HashSet<>();
        for (Map<String, Object> column : dbCopyReaderMysqlRepository.getCopyableColumns(sourceDatabase, tableName)) {
            String columnName = getTrimmedString(column.get(DbCopyKeyEnum.COLUMN_NAME));
            String dataType = getTrimmedString(column.get(DbCopyKeyEnum.DATA_TYPE));
            columnNames.add(columnName);
            if (dbCopySqlBuilder.isBitDataType(dataType)) {
                bitColumns.add(columnName);
                cellKinds.add(DbCopyCellKindEnum.BIT);
                continue;
            }
            cellKinds.add(dbCopySqlBuilder.isBinaryDataType(dataType) ? DbCopyCellKindEnum.BINARY : DbCopyCellKindEnum.TEXT);
        }
        if (columnNames.isEmpty()) return CopyOutcome.ok(0);
        List<String> primaryKeyColumns = dbCopyReaderMysqlRepository.getPrimaryKeyColumns(sourceDatabase, tableName);
        List<Integer> primaryKeyIndexes = new ArrayList<>();
        for (String primaryKeyColumn : primaryKeyColumns) {
            primaryKeyIndexes.add(columnNames.indexOf(primaryKeyColumn));
        }
        // una clave con columnas generadas (no se leen) o BIT no vale para paginar por rango: se pagina por OFFSET
        if (primaryKeyIndexes.contains(-1) || primaryKeyColumns.stream().anyMatch(bitColumns::contains)) {
            primaryKeyColumns = List.of();
            primaryKeyIndexes = List.of();
        }
        long estimatedRows = dbCopyReaderMysqlRepository.getEstimatedRows(sourceDatabase, tableName);

        long copiedRows = 0;
        List<Object> lastKeyValues = List.of();
        while (true) {
            if (dbCopyProgressTracker.isCancelled(copyDatabaseDto.getCopyId())) return CopyOutcome.cancelled(copiedRows);
            List<List<Object>> rows = dbCopyReaderMysqlRepository.getRowsPage(
                sourceDatabase, tableName, columnNames, cellKinds, primaryKeyColumns, lastKeyValues, copiedRows
            );
            if (copyDatabaseDto.isBulkInsert()) dbCopyWriterMysqlRepository.insertRowsInBulk(tableName, columnNames, rows, maxStatementChars);
            else dbCopyWriterMysqlRepository.insertRowsOneByOne(tableName, columnNames, rows);
            copiedRows += rows.size();
            dbCopyProgressTracker.notifyRowsCopied(copyDatabaseDto.getCopyId(), tableName, copiedRows, estimatedRows);
            if (rows.size() < DbCopyConfigEnum.PAGE_ROWS) return CopyOutcome.ok(copiedRows);
            if (!primaryKeyIndexes.isEmpty()) {
                List<Object> lastRow = rows.getLast();
                List<Object> keyValues = new ArrayList<>();
                for (int primaryKeyIndex : primaryKeyIndexes) {
                    keyValues.add(lastRow.get(primaryKeyIndex));
                }
                lastKeyValues = keyValues;
            }
        }
    }

    private Map<String, Object> getObjectResult(CopyItem copyItem, CopyOutcome copyOutcome) {
        return Primitives.of(
            DbCopyKeyEnum.OBJECT_TYPE, copyItem.objectType(),
            DbCopyKeyEnum.OBJECT_NAME, copyItem.objectName(),
            DbCopyKeyEnum.IS_OK, copyOutcome.isOk(),
            DbCopyKeyEnum.COPIED_ROWS, copyOutcome.copiedRows(),
            DbCopyKeyEnum.MESSAGE, copyOutcome.message()
        );
    }

    private record CopyItem(String objectType, String objectName) {
    }

    private record CopyOutcome(boolean isOk, boolean isCancelled, long copiedRows, String message) {

        static CopyOutcome ok(long copiedRows) {
            return new CopyOutcome(true, false, copiedRows, "");
        }

        static CopyOutcome cancelled(long copiedRows) {
            return new CopyOutcome(true, true, copiedRows, __(DbCopyMessageEnum.CANCELLED_TABLE_INCOMPLETE));
        }

        static CopyOutcome failed(String message) {
            return new CopyOutcome(false, false, 0, message == null ? "" : message);
        }
    }
}
