package modules.dbcopy.application.exportsqldump;

import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import modules.shared.application.Primitives;
import modules.shared.infrastructure.components.date.DateTimer;
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

import modules.history.application.recordqueryhistory.QueryHistoryTracer;

import modules.dbcopy.domain.enums.DbCopyCellKindEnum;
import modules.dbcopy.domain.enums.DbCopyConfigEnum;
import modules.dbcopy.domain.enums.DbCopyKeyEnum;
import modules.dbcopy.domain.enums.DbCopyMessageEnum;
import modules.dbcopy.domain.enums.DbCopyObjectTypeEnum;
import modules.dbcopy.domain.enums.SqlDumpModeEnum;
import modules.dbcopy.domain.services.BulkInsertBuffer;
import modules.dbcopy.domain.services.DbCopySqlBuilder;
import modules.dbcopy.domain.services.SqlDumpScriptBuilder;
import modules.dbcopy.infrastructure.components.DbCopyProgressTracker;
import modules.dbcopy.infrastructure.components.SqlDumpFileComponent;
import modules.dbcopy.infrastructure.repositories.DbCopyReaderMysqlRepository;
import modules.dbcopy.domain.exceptions.DbCopyException;

/**
 * El "SQL Dump" de SQLyog: vuelca a un fichero .sql las tablas (estructura, datos o ambos), vistas, rutinas, triggers y
 * eventos marcados de una BD MySQL. Se escribe segun se lee (por paginas, como la copia de BD), con su propia sesion,
 * que se cierra al acabar. Progreso y Parar, por DbCopyProgressTracker con el dumpId del DTO.
 * <p>
 * EXCEPCION DOCUMENTADA a «los servicios no capturan», la misma que la copia de BD (DDD 17): el error de CADA objeto
 * (sin permisos para ver una rutina) queda en el resumen y como comentario en el fichero, y el volcado sigue. Lo que
 * impide el volcado entero (la sesion, leer los objetos, escribir el fichero) sube hasta el controller.
 */
public final class ExportSqlDumpService {

    private static final int DUMP_MAX_STATEMENT_CHARS = 512 * 1024;

    private final TargetSessionFactory targetSessionFactory;
    private final DbCopySqlBuilder dbCopySqlBuilder;
    private final SqlDumpScriptBuilder sqlDumpScriptBuilder;
    private final DbCopyProgressTracker dbCopyProgressTracker;

    private ExportSqlDumpDto exportSqlDumpDto;
    private DbCopyReaderMysqlRepository dbCopyReaderMysqlRepository;
    private SqlDumpFileComponent sqlDumpFileComponent;

    private ExportSqlDumpService() {
        this.targetSessionFactory = TargetSessionFactory.getInstance();
        this.dbCopySqlBuilder = DbCopySqlBuilder.getInstance();
        this.sqlDumpScriptBuilder = SqlDumpScriptBuilder.getInstance();
        this.dbCopyProgressTracker = DbCopyProgressTracker.getInstance();
    }

    public static ExportSqlDumpService getInstance() {
        return new ExportSqlDumpService();
    }

    /**
     * @throws DbCopyException
     * @throws ConnectionsException
     * @throws SQLException si no se puede abrir la sesion o leer los objetos
     */
    public ExportSqlDumpResultDto invoke(ExportSqlDumpDto exportSqlDumpDto) throws SQLException {
        return QueryHistoryTracer.getInstance(exportSqlDumpDto.getConnectionUuid(), exportSqlDumpDto.getDatabaseName()).getTraced(() -> getResult(exportSqlDumpDto));
    }

    private ExportSqlDumpResultDto getResult(ExportSqlDumpDto exportSqlDumpDto) throws SQLException {
        this.exportSqlDumpDto = exportSqlDumpDto;
        failIfWrongInput();
        ConnectionEntity connectionEntity = tryToGetMysqlConnectionOrFail();

        long startNanos = System.nanoTime();
        String nowDt = DateTimer.getInstance().getNowDt();
        Path filePath = getFilePath(nowDt);
        String sessionKeyPrefix = UuidGenerator.getInstance().getUniqueIdWithPrefix(DbCopyConfigEnum.SESSION_PREFIX + "dump-");
        List<Map<String, Object>> objectResults = new ArrayList<>();
        boolean isCancelled = false;
        try {
            dbCopyReaderMysqlRepository = DbCopyReaderMysqlRepository.getInstance(targetSessionFactory.getSessionPdo(
                connectionEntity.getConnectionUuid(), sessionKeyPrefix + TargetSessionFactory.SESSION_KEY_SEPARATOR + "source"
            ));
            String databaseName = exportSqlDumpDto.getDatabaseName();
            dbCopyReaderMysqlRepository.prepareSession(databaseName);
            if (exportSqlDumpDto.isSingleTransaction()) dbCopyReaderMysqlRepository.startConsistentSnapshot();
            List<DumpItem> dumpItems = getDumpItems();
            String versionInfo = exportSqlDumpDto.isVersionIncluded() ? "multidbs · " + dbCopyReaderMysqlRepository.getServerVersion() : "";

            sqlDumpFileComponent = openFileOrFail(filePath);
            try (SqlDumpFileComponent dumpFile = sqlDumpFileComponent) {
                dumpFile.write(sqlDumpScriptBuilder.getHeader(databaseName, versionInfo, nowDt, exportSqlDumpDto.isForeignKeyChecksOff()));
                if (exportSqlDumpDto.isCreateDatabaseIncluded()) {
                    Map<String, Object> charset = dbCopyReaderMysqlRepository.getDatabaseCharset(databaseName);
                    dumpFile.write(dbCopySqlBuilder.getCreateDatabaseSql(
                        databaseName, getTrimmedString(charset.get(DbCopyKeyEnum.CHARACTER_SET)), getTrimmedString(charset.get(DbCopyKeyEnum.COLLATION))
                    ) + ";" + SqlDumpScriptBuilder.NEW_LINE);
                }
                if (exportSqlDumpDto.isUseIncluded() || exportSqlDumpDto.isCreateDatabaseIncluded()) dumpFile.write(sqlDumpScriptBuilder.getUseSql(databaseName));
                for (int i = 0; i < dumpItems.size(); i++) {
                    if (dbCopyProgressTracker.isCancelled(exportSqlDumpDto.getDumpId())) {
                        isCancelled = true;
                        break;
                    }
                    DumpItem dumpItem = dumpItems.get(i);
                    dbCopyProgressTracker.notifyObjectStarted(exportSqlDumpDto.getDumpId(), dumpItem.objectType(), dumpItem.objectName(), i + 1, dumpItems.size());
                    DumpOutcome dumpOutcome = dumpObject(dumpItem);
                    objectResults.add(getObjectResult(dumpItem, dumpOutcome));
                    if (dumpOutcome.isCancelled()) {
                        isCancelled = true;
                        break;
                    }
                }
                dumpFile.write(sqlDumpScriptBuilder.getFooter(exportSqlDumpDto.isForeignKeyChecksOff()));
            }
        }
        catch (UncheckedIOException e) {
            throw DbCopyException.badRequestCustom(__(DbCopyMessageEnum.CANNOT_WRITE_X_X, filePath, e.getCause().getMessage()));
        }
        finally {
            targetSessionFactory.closeSessions(sessionKeyPrefix);
        }
        int okCount = (int) objectResults.stream().filter(objectResult -> getBool(objectResult.get(DbCopyKeyEnum.IS_OK))).count();
        return ExportSqlDumpResultDto.fromPrimitives(Primitives.of(
            "filePath", filePath.toAbsolutePath().toString(),
            "objects", objectResults,
            "okCount", okCount,
            "errorCount", objectResults.size() - okCount,
            "dumpedRows", objectResults.stream().mapToLong(objectResult -> (long) objectResult.get(DbCopyKeyEnum.COPIED_ROWS)).sum(),
            "isCancelled", isCancelled,
            "elapsedMs", (System.nanoTime() - startNanos) / 1_000_000
        ));
    }

    /**
     * @throws DbCopyException
     */
    private void failIfWrongInput() {
        if (exportSqlDumpDto.getConnectionUuid().isEmpty()) {
            throw DbCopyException.badRequestCustom(__(DbCopyMessageEnum.FIELD_IS_REQUIRED_X, "connectionUuid"));
        }
        if (exportSqlDumpDto.getDatabaseName().isEmpty()) {
            throw DbCopyException.badRequestCustom(__(DbCopyMessageEnum.FIELD_IS_REQUIRED_X, "databaseName"));
        }
        if (exportSqlDumpDto.getFilePath().isEmpty()) {
            throw DbCopyException.badRequestCustom(__(DbCopyMessageEnum.FIELD_IS_REQUIRED_X, "filePath"));
        }
        if (!SqlDumpModeEnum.VALUES.contains(exportSqlDumpDto.getDumpMode())) {
            throw DbCopyException.badRequestCustom(__(DbCopyMessageEnum.WRONG_DUMP_MODE_X, exportSqlDumpDto.getDumpMode()));
        }
        if (DbCopyObjectTypeEnum.VALUES.stream().allMatch(objectType -> exportSqlDumpDto.getObjectNames(objectType).isEmpty())) {
            throw DbCopyException.badRequestCustom(__(DbCopyMessageEnum.NOTHING_SELECTED_TO_DUMP));
        }
    }

    /**
     * @throws ConnectionsException si no existe
     * @throws DbCopyException si no es MySQL/MariaDB
     */
    private ConnectionEntity tryToGetMysqlConnectionOrFail() {
        String connectionUuid = exportSqlDumpDto.getConnectionUuid();
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
     * Con "Fecha delante del nombre": 20261126_153045_volcado.sql en la misma carpeta.
     */
    private Path getFilePath(String nowDt) {
        Path filePath = Path.of(exportSqlDumpDto.getFilePath());
        if (!exportSqlDumpDto.isTimestampPrefix()) return filePath;
        String digits = nowDt.replaceAll("\\D", "");
        String prefix = digits.length() >= 14 ? digits.substring(0, 8) + "_" + digits.substring(8, 14) + "_" : digits + "_";
        Path parent = filePath.getParent();
        Path prefixedName = Path.of(prefix + filePath.getFileName());
        return parent == null ? prefixedName : parent.resolve(prefixedName);
    }

    /**
     * @throws DbCopyException si no se puede crear el fichero
     */
    private SqlDumpFileComponent openFileOrFail(Path filePath) {
        try {
            return SqlDumpFileComponent.open(filePath);
        }
        catch (UncheckedIOException e) {
            throw DbCopyException.badRequestCustom(__(DbCopyMessageEnum.CANNOT_WRITE_X_X, filePath, e.getCause().getMessage()));
        }
    }

    /**
     * Lo marcado en orden de creacion; con "solo datos", solo las tablas. Las vistas, despues de las vistas de las que
     * dependen (su CREATE las nombra), para que el fichero se cargue de corrido.
     */
    private List<DumpItem> getDumpItems() {
        boolean isDataOnly = SqlDumpModeEnum.DATA_ONLY.equals(exportSqlDumpDto.getDumpMode());
        List<DumpItem> dumpItems = new ArrayList<>();
        for (String objectType : DbCopyObjectTypeEnum.VALUES) {
            if (isDataOnly && !DbCopyObjectTypeEnum.TABLE.equals(objectType)) continue;
            List<String> objectNames = DbCopyObjectTypeEnum.VIEW.equals(objectType)
                ? getViewsInDependencyOrder(exportSqlDumpDto.getObjectNames(objectType))
                : exportSqlDumpDto.getObjectNames(objectType);
            for (String objectName : objectNames) dumpItems.add(new DumpItem(objectType, objectName));
        }
        return dumpItems;
    }

    private List<String> getViewsInDependencyOrder(List<String> viewNames) {
        Map<String, String> createSqlsByView = new LinkedHashMap<>();
        for (String viewName : viewNames) createSqlsByView.put(viewName, getViewCreateSqlForOrder(viewName));
        List<String> orderedViews = new ArrayList<>();
        Set<String> pendingViews = new HashSet<>(viewNames);
        while (!pendingViews.isEmpty()) {
            boolean isProgressing = false;
            for (String viewName : viewNames) {
                if (!pendingViews.contains(viewName)) continue;
                String createSql = createSqlsByView.get(viewName);
                boolean hasPendingDependency = pendingViews.stream()
                    .anyMatch(otherView -> !otherView.equals(viewName) && createSql.contains("`" + otherView + "`"));
                if (hasPendingDependency) continue;
                orderedViews.add(viewName);
                pendingViews.remove(viewName);
                isProgressing = true;
            }
            // dependencias en circulo (no deberia pasar): las que queden, en su orden
            if (!isProgressing) {
                for (String viewName : viewNames) if (pendingViews.remove(viewName)) orderedViews.add(viewName);
            }
        }
        return orderedViews;
    }

    /**
     * El CREATE de la vista solo para ordenarla. Si el servidor no lo da (sin permisos, error 1142), "" (sin
     * dependencias): el fallo no tira el volcado entero, sino que lo vuelve a dar y lo apunta dumpObject en esa vista.
     */
    private String getViewCreateSqlForOrder(String viewName) {
        try {
            return dbCopyReaderMysqlRepository.getCreateSql(DbCopyObjectTypeEnum.VIEW, exportSqlDumpDto.getDatabaseName(), viewName);
        }
        catch (SQLException e) {
            return "";
        }
    }

    private DumpOutcome dumpObject(DumpItem dumpItem) {
        try {
            String objectType = dumpItem.objectType();
            String objectName = dumpItem.objectName();
            boolean isTable = DbCopyObjectTypeEnum.TABLE.equals(objectType);
            boolean isDataOnly = SqlDumpModeEnum.DATA_ONLY.equals(exportSqlDumpDto.getDumpMode());
            if (!isDataOnly) {
                sqlDumpFileComponent.write(sqlDumpScriptBuilder.getSectionComment(getSectionLabel(objectType), objectName));
                String createSql = getCreateSqlOrFail(dumpItem);
                if (exportSqlDumpDto.isDropIncluded()) {
                    sqlDumpFileComponent.write(dbCopySqlBuilder.getDropSql(objectType, objectName) + ";" + SqlDumpScriptBuilder.NEW_LINE);
                }
                else if (isTable) {
                    createSql = dbCopySqlBuilder.getCreateTableIfNotExists(createSql);
                }
                sqlDumpFileComponent.write(sqlDumpScriptBuilder.getStatement(objectType, createSql));
            }
            if (!isTable || SqlDumpModeEnum.STRUCTURE_ONLY.equals(exportSqlDumpDto.getDumpMode())) return DumpOutcome.ok(0);
            return dumpRows(objectName);
        }
        catch (SQLException | DbCopyException e) {
            String message = e.getMessage() == null ? "" : e.getMessage();
            sqlDumpFileComponent.write(sqlDumpScriptBuilder.getErrorComment(dumpItem.objectName(), message));
            return DumpOutcome.failed(message);
        }
    }

    private String getSectionLabel(String objectType) {
        return switch (objectType) {
            case DbCopyObjectTypeEnum.VIEW -> "Vista";
            case DbCopyObjectTypeEnum.PROCEDURE -> "Procedimiento";
            case DbCopyObjectTypeEnum.FUNCTION -> "Funcion";
            case DbCopyObjectTypeEnum.TRIGGER -> "Trigger";
            case DbCopyObjectTypeEnum.EVENT -> "Evento";
            default -> "Tabla";
        };
    }

    /**
     * @throws DbCopyException si el servidor no da la definicion (sin permisos sobre la rutina)
     */
    private String getCreateSqlOrFail(DumpItem dumpItem) throws SQLException {
        String createSql = dbCopyReaderMysqlRepository.getCreateSql(dumpItem.objectType(), exportSqlDumpDto.getDatabaseName(), dumpItem.objectName()).strip();
        if (createSql.isEmpty()) {
            throw DbCopyException.serviceUnavailableCustom(__(DbCopyMessageEnum.NO_DEFINITION_X, dumpItem.objectName()));
        }
        return exportSqlDumpDto.isIgnoreDefiner() ? dbCopySqlBuilder.getWithoutDefiner(createSql) : createSql;
    }

    /**
     * Las filas por paginas (keyset si hay clave primaria), en INSERT de varias filas o uno por fila.
     */
    private DumpOutcome dumpRows(String tableName) throws SQLException {
        String databaseName = exportSqlDumpDto.getDatabaseName();
        List<String> columnNames = new ArrayList<>();
        List<String> cellKinds = new ArrayList<>();
        Set<String> bitColumns = new HashSet<>();
        for (Map<String, Object> column : dbCopyReaderMysqlRepository.getCopyableColumns(databaseName, tableName)) {
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
        if (columnNames.isEmpty()) return DumpOutcome.ok(0);
        List<String> primaryKeyColumns = dbCopyReaderMysqlRepository.getPrimaryKeyColumns(databaseName, tableName);
        List<Integer> primaryKeyIndexes = new ArrayList<>();
        for (String primaryKeyColumn : primaryKeyColumns) primaryKeyIndexes.add(columnNames.indexOf(primaryKeyColumn));
        if (primaryKeyIndexes.contains(-1) || primaryKeyColumns.stream().anyMatch(bitColumns::contains)) {
            primaryKeyColumns = List.of();
            primaryKeyIndexes = List.of();
        }
        long estimatedRows = dbCopyReaderMysqlRepository.getEstimatedRows(databaseName, tableName);
        String insertPrefix = dbCopySqlBuilder.getInsertPrefix(tableName, columnNames);

        long dumpedRows = 0;
        List<Object> lastKeyValues = List.of();
        boolean isFirstPage = true;
        while (true) {
            if (dbCopyProgressTracker.isCancelled(exportSqlDumpDto.getDumpId())) return DumpOutcome.cancelled(dumpedRows);
            List<List<Object>> rows = dbCopyReaderMysqlRepository.getRowsPage(
                databaseName, tableName, columnNames, cellKinds, primaryKeyColumns, lastKeyValues, dumpedRows
            );
            if (isFirstPage && !rows.isEmpty()) {
                sqlDumpFileComponent.write(sqlDumpScriptBuilder.getDataComment(tableName));
            }
            isFirstPage = false;
            BulkInsertBuffer bulkInsertBuffer = exportSqlDumpDto.isBulkInsert()
                ? BulkInsertBuffer.getBulkInstance(insertPrefix, DUMP_MAX_STATEMENT_CHARS)
                : BulkInsertBuffer.getRowByRowInstance(insertPrefix);
            for (List<Object> row : rows) bulkInsertBuffer.add(dbCopySqlBuilder.getRowTuple(row));
            for (String insertSql : bulkInsertBuffer.takeAllStatements()) sqlDumpFileComponent.write(insertSql + ";" + SqlDumpScriptBuilder.NEW_LINE);
            dumpedRows += rows.size();
            dbCopyProgressTracker.notifyRowsCopied(exportSqlDumpDto.getDumpId(), tableName, dumpedRows, estimatedRows);
            if (rows.size() < DbCopyConfigEnum.PAGE_ROWS) return DumpOutcome.ok(dumpedRows);
            if (!primaryKeyIndexes.isEmpty()) {
                List<Object> lastRow = rows.getLast();
                List<Object> keyValues = new ArrayList<>();
                for (int primaryKeyIndex : primaryKeyIndexes) keyValues.add(lastRow.get(primaryKeyIndex));
                lastKeyValues = keyValues;
            }
        }
    }

    private Map<String, Object> getObjectResult(DumpItem dumpItem, DumpOutcome dumpOutcome) {
        return Primitives.of(
            DbCopyKeyEnum.OBJECT_TYPE, dumpItem.objectType(),
            DbCopyKeyEnum.OBJECT_NAME, dumpItem.objectName(),
            DbCopyKeyEnum.IS_OK, dumpOutcome.isOk(),
            DbCopyKeyEnum.COPIED_ROWS, dumpOutcome.dumpedRows(),
            DbCopyKeyEnum.MESSAGE, dumpOutcome.message()
        );
    }

    private record DumpItem(String objectType, String objectName) {
    }

    private record DumpOutcome(boolean isOk, boolean isCancelled, long dumpedRows, String message) {

        static DumpOutcome ok(long dumpedRows) {
            return new DumpOutcome(true, false, dumpedRows, "");
        }

        static DumpOutcome cancelled(long dumpedRows) {
            return new DumpOutcome(true, true, dumpedRows, __(DbCopyMessageEnum.CANCELLED_TABLE_INCOMPLETE));
        }

        static DumpOutcome failed(String message) {
            return new DumpOutcome(false, false, 0, message);
        }
    }
}
