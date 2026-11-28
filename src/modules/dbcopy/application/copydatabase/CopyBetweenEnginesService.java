package modules.dbcopy.application.copydatabase;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import modules.shared.domain.services.SqlDialect;
import modules.shared.application.Primitives;
import modules.shared.infrastructure.components.uuid.UuidGenerator;
import static modules.shared.application.Primitives.getBool;
import static modules.shared.application.Primitives.getTrimmedString;

import static core.config.Global.__;

import modules.connections.domain.entities.ConnectionEntity;
import modules.connections.infrastructure.factories.TargetSessionFactory;

import modules.history.domain.enums.QueryHistoryConfigEnum;
import modules.history.application.recordqueryhistory.RecordQueryHistoryDto;
import modules.history.application.recordqueryhistory.RecordQueryHistoryService;

import modules.explorer.domain.enums.ExplorerKeyEnum;
import modules.explorer.infrastructure.factories.ExplorerReaderRepositoryFactory;
import modules.explorer.infrastructure.repositories.AbstractExplorerReaderRepository;

import modules.dbcopy.domain.enums.DbCopyConfigEnum;
import modules.dbcopy.domain.enums.DbCopyKeyEnum;
import modules.dbcopy.domain.enums.DbCopyMessageEnum;
import modules.dbcopy.domain.enums.DbCopyObjectTypeEnum;
import modules.dbcopy.domain.enums.DbCopyTypeFamilyEnum;
import modules.dbcopy.domain.entities.PortableColumnEntity;
import modules.dbcopy.domain.services.BulkInsertBuffer;
import modules.dbcopy.domain.services.PortableColumnMapper;
import modules.dbcopy.domain.services.PortableCopySqlBuilder;
import modules.dbcopy.infrastructure.components.DbCopyProgressTracker;
import modules.dbcopy.infrastructure.repositories.DbCopyRowsReaderJdbcRepository;
import modules.dbcopy.infrastructure.repositories.DbCopyWriterJdbcRepository;
import modules.dbcopy.domain.exceptions.DbCopyException;

/**
 * Feature 29 entre motores distintos (o entre dos que no son MySQL): copia TABLAS de un motor SQL a otro (MySQL/MariaDB,
 * PostgreSQL, SQL Server, SQLite y Ficheros como origen). De cada tabla rehace columnas con el tipo traducido
 * (PortableColumnMapper), NOT NULL, clave primaria, el autoincremento de la clave y los indices; despues, los datos
 * por paginas como literales del destino. No van: DEFAULT, claves foraneas, CHECK, comentarios ni columnas calculadas.
 * Vistas, rutinas, triggers y eventos no se traducen entre dialectos: salen en el resumen como no copiados.
 * Lo invoca CopyDatabaseService cuando origen y destino no son los dos MySQL; mismo DTO, mismo resultado, mismo
 * progreso y Parar.
 * <p>
 * Misma EXCEPCION DOCUMENTADA que CopyDatabaseService (DDD 17): el error de cada tabla (y el de cada indice) se
 * guarda en el resumen y la copia sigue. Lo que impide la copia entera sube hasta el controller.
 */
public final class CopyBetweenEnginesService {

    private final TargetSessionFactory targetSessionFactory;
    private final PortableColumnMapper portableColumnMapper;
    private final DbCopyProgressTracker dbCopyProgressTracker;

    private CopyDatabaseDto copyDatabaseDto;
    private String sourceEngine;
    private String targetEngine;
    private AbstractExplorerReaderRepository sourceExplorerReaderRepository;
    private DbCopyRowsReaderJdbcRepository dbCopyRowsReaderJdbcRepository;
    private DbCopyWriterJdbcRepository dbCopyWriterJdbcRepository;
    private PortableCopySqlBuilder portableCopySqlBuilder;
    private Set<String> existingTargetTables = Set.of();
    private int maxStatementChars;

    private CopyBetweenEnginesService() {
        this.targetSessionFactory = TargetSessionFactory.getInstance();
        this.portableColumnMapper = PortableColumnMapper.getInstance();
        this.dbCopyProgressTracker = DbCopyProgressTracker.getInstance();
    }

    public static CopyBetweenEnginesService getInstance() {
        return new CopyBetweenEnginesService();
    }

    /**
     * Las conexiones ya vienen comprobadas por CopyDatabaseService (existen, son SQL y no es la misma BD).
     * @throws DbCopyException
     * @throws SQLException si no se pueden abrir las sesiones, preparar la BD destino o leer las tablas del origen
     */
    public CopyDatabaseResultDto invoke(CopyDatabaseDto copyDatabaseDto, ConnectionEntity sourceConnectionEntity, ConnectionEntity targetConnectionEntity)
        throws SQLException {
        this.copyDatabaseDto = copyDatabaseDto;
        this.sourceEngine = targetSessionFactory.getDbEngine(sourceConnectionEntity.getConnectionUuid());
        this.targetEngine = targetSessionFactory.getDbEngine(targetConnectionEntity.getConnectionUuid());
        if (targetConnectionEntity.isProduction() && !copyDatabaseDto.isConfirmed()) {
            throw DbCopyException.preconditionRequiredCustom(__(
                DbCopyMessageEnum.TARGET_IS_PRODUCTION_X_X, targetConnectionEntity.getConnectionName(), copyDatabaseDto.getTargetDatabase()
            ));
        }

        long startNanos = System.nanoTime();
        String sessionKeyPrefix = UuidGenerator.getInstance().getUniqueIdWithPrefix(DbCopyConfigEnum.SESSION_PREFIX);
        String sourceSessionKey = sessionKeyPrefix + TargetSessionFactory.SESSION_KEY_SEPARATOR + "source";
        String targetSessionKey = sessionKeyPrefix + TargetSessionFactory.SESSION_KEY_SEPARATOR + "target";
        List<Map<String, Object>> objectResults = new ArrayList<>();
        boolean isCancelled = false;
        try {
            ExplorerReaderRepositoryFactory explorerReaderRepositoryFactory = ExplorerReaderRepositoryFactory.getInstance();
            sourceExplorerReaderRepository = explorerReaderRepositoryFactory.getRepository(sourceConnectionEntity.getConnectionUuid(), sourceSessionKey);
            AbstractExplorerReaderRepository targetExplorerReaderRepository = explorerReaderRepositoryFactory.getRepository(
                targetConnectionEntity.getConnectionUuid(), targetSessionKey
            );
            dbCopyRowsReaderJdbcRepository = DbCopyRowsReaderJdbcRepository.getInstance(
                targetSessionFactory.getSessionPdo(sourceConnectionEntity.getConnectionUuid(), sourceSessionKey), sourceEngine
            );
            dbCopyWriterJdbcRepository = DbCopyWriterJdbcRepository.getInstance(
                targetSessionFactory.getSessionPdo(targetConnectionEntity.getConnectionUuid(), targetSessionKey), targetEngine
            );
            portableCopySqlBuilder = PortableCopySqlBuilder.getInstance(sourceEngine, targetEngine, dbCopyWriterJdbcRepository.isHttpTunnel());
            dbCopyRowsReaderJdbcRepository.prepareSession();
            prepareTarget(targetExplorerReaderRepository);
            maxStatementChars = dbCopyWriterJdbcRepository.getMaxStatementChars();

            List<CopyItem> copyItems = getCopyItems();
            for (int i = 0; i < copyItems.size(); i++) {
                if (dbCopyProgressTracker.isCancelled(copyDatabaseDto.getCopyId())) {
                    isCancelled = true;
                    break;
                }
                CopyItem copyItem = copyItems.get(i);
                dbCopyProgressTracker.notifyObjectStarted(copyDatabaseDto.getCopyId(), copyItem.objectType(), copyItem.objectName(), i + 1, copyItems.size());
                CopyOutcome copyOutcome = DbCopyObjectTypeEnum.TABLE.equals(copyItem.objectType())
                    ? copyTable(copyItem.objectName())
                    : CopyOutcome.failed(__(DbCopyMessageEnum.ONLY_TABLES_BETWEEN_ENGINES));
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
     * La BD destino (un esquema en PostgreSQL) se crea si no existe; y se apuntan sus tablas, para no rehacer las que
     * ya estan cuando no se pide borrar.
     */
    private void prepareTarget(AbstractExplorerReaderRepository targetExplorerReaderRepository) throws SQLException {
        String targetDatabase = copyDatabaseDto.getTargetDatabase();
        boolean isExisting = targetExplorerReaderRepository.getDatabaseNames().stream().anyMatch(targetDatabase::equalsIgnoreCase);
        if (!isExisting) dbCopyWriterJdbcRepository.executeAlone(portableCopySqlBuilder.getCreateDatabaseSql(targetDatabase));
        dbCopyWriterJdbcRepository.prepareSession();
        if (!isExisting) return;
        Set<String> tableNames = new HashSet<>();
        Object tables = targetExplorerReaderRepository.getTablesAndViews(targetDatabase).get(ExplorerKeyEnum.TABLES);
        if (tables instanceof List<?> names) {
            for (Object name : names) {
                tableNames.add(String.valueOf(name).toLowerCase(Locale.ROOT));
            }
        }
        existingTargetTables = tableNames;
    }

    /**
     * Lo marcado, sin repetir, en orden de creacion. Los triggers asociados no aplican entre motores.
     */
    private List<CopyItem> getCopyItems() {
        List<CopyItem> copyItems = new ArrayList<>();
        for (String objectType : DbCopyObjectTypeEnum.VALUES) {
            for (String objectName : new LinkedHashSet<>(copyDatabaseDto.getObjectNames(objectType))) {
                if (!objectName.isEmpty()) copyItems.add(new CopyItem(objectType, objectName));
            }
        }
        return copyItems;
    }

    private CopyOutcome copyTable(String tableName) {
        try {
            String sourceDatabase = copyDatabaseDto.getSourceDatabase();
            String targetDatabase = copyDatabaseDto.getTargetDatabase();
            List<PortableColumnEntity> columns = getPortableColumns(tableName);
            if (columns.isEmpty()) return CopyOutcome.ok(0, "");
            Set<String> columnNames = new HashSet<>(columns.stream().map(PortableColumnEntity::name).toList());
            List<String> primaryKeyColumns = new ArrayList<>(sourceExplorerReaderRepository.getPrimaryKeyColumns(sourceDatabase, tableName));
            // una clave con columnas calculadas (no se copian) no se puede rehacer
            if (!columnNames.containsAll(primaryKeyColumns)) primaryKeyColumns = List.of();
            Map<String, TableIndex> indexes = getIndexes(tableName, columnNames);

            Set<String> indexedColumns = new HashSet<>(primaryKeyColumns);
            indexes.values().forEach(tableIndex -> indexedColumns.addAll(tableIndex.columnNames()));
            String identityColumn = portableCopySqlBuilder.getIdentityColumn(columns, primaryKeyColumns);
            List<String> targetTypes = new ArrayList<>();
            for (PortableColumnEntity column : columns) {
                targetTypes.add(portableColumnMapper.getTargetType(targetEngine, column, indexedColumns.contains(column.name()), column.name().equals(identityColumn)));
            }

            boolean isCreated = false;
            if (copyDatabaseDto.isDropIfExists()) {
                dbCopyWriterJdbcRepository.executeAlone(portableCopySqlBuilder.getDropTableSql(targetDatabase, tableName));
                isCreated = true;
            }
            else if (!existingTargetTables.contains(tableName.toLowerCase(Locale.ROOT))) isCreated = true;
            if (isCreated) {
                dbCopyWriterJdbcRepository.executeAlone(portableCopySqlBuilder.getCreateTableSql(targetDatabase, tableName, columns, targetTypes, primaryKeyColumns));
            }

            long copiedRows = 0;
            boolean isCancelled = false;
            if (!copyDatabaseDto.isStructureOnly()) {
                CopyOutcome rowsOutcome = copyRows(tableName, columns, primaryKeyColumns, isCreated ? identityColumn : "");
                copiedRows = rowsOutcome.copiedRows();
                isCancelled = rowsOutcome.isCancelled();
            }
            if (isCancelled) return CopyOutcome.cancelled(copiedRows);
            String indexErrors = isCreated ? createIndexes(tableName, indexes) : "";
            return CopyOutcome.ok(copiedRows, indexErrors);
        }
        catch (SQLException | DbCopyException e) {
            return CopyOutcome.failed(e.getMessage());
        }
    }

    /**
     * Las columnas que se copian, en su orden: las calculadas no (el destino no las tiene).
     */
    private List<PortableColumnEntity> getPortableColumns(String tableName) throws SQLException {
        List<PortableColumnEntity> columns = new ArrayList<>();
        for (Map<String, Object> column : sourceExplorerReaderRepository.getTableColumns(copyDatabaseDto.getSourceDatabase(), tableName)) {
            String extra = getTrimmedString(column.get(ExplorerKeyEnum.EXTRA));
            if (extra.toLowerCase(Locale.ROOT).contains("generated")) continue;
            columns.add(portableColumnMapper.getPortableColumn(
                sourceEngine,
                getTrimmedString(column.get(ExplorerKeyEnum.COLUMN_NAME)),
                getTrimmedString(column.get(ExplorerKeyEnum.COLUMN_TYPE)),
                getTrimmedString(column.get(ExplorerKeyEnum.DATA_TYPE)),
                getBool(column.get(ExplorerKeyEnum.IS_NULLABLE)),
                extra
            ));
        }
        return columns;
    }

    /**
     * Los indices del origen que no son la clave primaria, con sus columnas en orden. Fuera los que tocan una columna
     * que no se copia (los de expresiones de PostgreSQL) y los FULLTEXT/SPATIAL de MySQL.
     */
    private Map<String, TableIndex> getIndexes(String tableName, Set<String> columnNames) throws SQLException {
        Map<String, TableIndex> indexes = new LinkedHashMap<>();
        Set<String> skippedIndexes = new HashSet<>();
        for (Map<String, Object> row : sourceExplorerReaderRepository.getIndexes(copyDatabaseDto.getSourceDatabase(), tableName)) {
            String indexName = getTrimmedString(row.get("index_name"));
            String indexType = getTrimmedString(row.get("index_type")).toUpperCase(Locale.ROOT);
            String columnName = getTrimmedString(row.get("column_name"));
            if ("PRIMARY".equals(indexName) || skippedIndexes.contains(indexName)) continue;
            if (!columnNames.contains(columnName) || indexType.equals("FULLTEXT") || indexType.equals("SPATIAL")) {
                skippedIndexes.add(indexName);
                indexes.remove(indexName);
                continue;
            }
            boolean isUnique = "0".equals(getTrimmedString(row.get("non_unique"))) || Boolean.FALSE.equals(row.get("non_unique"));
            indexes.computeIfAbsent(indexName, name -> new TableIndex(name, isUnique, new ArrayList<>())).columnNames().add(columnName);
        }
        return indexes;
    }

    /**
     * Los indices, despues de los datos (cargar sin ellos es mas rapido). Uno que falla no tira la tabla: queda en el
     * mensaje del resumen.
     */
    private String createIndexes(String tableName, Map<String, TableIndex> indexes) {
        List<String> errors = new ArrayList<>();
        for (TableIndex tableIndex : indexes.values()) {
            try {
                dbCopyWriterJdbcRepository.executeAlone(portableCopySqlBuilder.getCreateIndexSql(
                    copyDatabaseDto.getTargetDatabase(), tableName, tableIndex.indexName(), tableIndex.isUnique(), tableIndex.columnNames()
                ));
            }
            catch (SQLException e) {
                errors.add(__(DbCopyMessageEnum.INDEX_NOT_CREATED_X_X, tableIndex.indexName(), e.getMessage()));
            }
        }
        return String.join("\n", errors);
    }

    /**
     * Los datos por paginas: con clave primaria de una sola columna entera, por rango (keyset); si no, por OFFSET.
     * identityColumn: la columna IDENTITY que se acaba de crear (SQL Server pide IDENTITY_INSERT; PostgreSQL, mover la
     * secuencia al final).
     */
    private CopyOutcome copyRows(String tableName, List<PortableColumnEntity> columns, List<String> primaryKeyColumns, String identityColumn)
        throws SQLException {
        String sourceDatabase = copyDatabaseDto.getSourceDatabase();
        String targetDatabase = copyDatabaseDto.getTargetDatabase();
        boolean[] binaryColumns = new boolean[columns.size()];
        for (int i = 0; i < columns.size(); i++) {
            binaryColumns[i] = DbCopyTypeFamilyEnum.BINARY.equals(columns.get(i).family());
        }
        String keysetColumn = getKeysetColumn(columns, primaryKeyColumns);
        int keysetIndex = keysetColumn.isEmpty() ? -1 : columns.stream().map(PortableColumnEntity::name).toList().indexOf(keysetColumn);
        long estimatedRows = getEstimatedRows(tableName);
        String insertPrefix = portableCopySqlBuilder.getInsertPrefix(targetDatabase, tableName, columns);
        boolean isMssqlIdentity = !identityColumn.isEmpty() && SqlDialect.getInstance(targetEngine).isMssql();
        List<String> sessionSqls = isMssqlIdentity ? List.of(portableCopySqlBuilder.getIdentityInsertSql(targetDatabase, tableName, true)) : List.of();

        long copiedRows = 0;
        String lastKeyValue = "";
        while (true) {
            if (dbCopyProgressTracker.isCancelled(copyDatabaseDto.getCopyId())) return CopyOutcome.cancelled(copiedRows);
            List<List<Object>> rows = dbCopyRowsReaderJdbcRepository.getRows(
                portableCopySqlBuilder.getSelectPageSql(
                    sourceDatabase, tableName, columns, primaryKeyColumns, keysetColumn, lastKeyValue, DbCopyConfigEnum.PAGE_ROWS, copiedRows
                ),
                binaryColumns
            );
            BulkInsertBuffer bulkInsertBuffer = copyDatabaseDto.isBulkInsert()
                ? BulkInsertBuffer.getBulkInstance(insertPrefix, maxStatementChars)
                : BulkInsertBuffer.getRowByRowInstance(insertPrefix);
            for (List<Object> row : rows) {
                bulkInsertBuffer.add(portableCopySqlBuilder.getRowTuple(columns, row));
            }
            dbCopyWriterJdbcRepository.executeInserts(sessionSqls, bulkInsertBuffer.takeAllStatements());
            copiedRows += rows.size();
            dbCopyProgressTracker.notifyRowsCopied(copyDatabaseDto.getCopyId(), tableName, copiedRows, estimatedRows);
            if (rows.size() < DbCopyConfigEnum.PAGE_ROWS) break;
            if (keysetIndex >= 0) {
                lastKeyValue = portableCopySqlBuilder.getKeysetLiteral(rows.getLast().get(keysetIndex));
                // una clave NULL o rara no deja seguir por rango: lo que queda, por OFFSET
                if (lastKeyValue.isEmpty()) {
                    keysetColumn = "";
                    keysetIndex = -1;
                }
            }
        }
        if (isMssqlIdentity) dbCopyWriterJdbcRepository.executeAlone(portableCopySqlBuilder.getIdentityInsertSql(targetDatabase, tableName, false));
        if (!identityColumn.isEmpty() && SqlDialect.getInstance(targetEngine).isPgsql() && copiedRows > 0) {
            dbCopyWriterJdbcRepository.executeAlone(portableCopySqlBuilder.getIdentityRestartSql(targetDatabase, tableName, identityColumn));
        }
        return CopyOutcome.ok(copiedRows, "");
    }

    /**
     * La columna para paginar por rango: la clave primaria si es una sola columna entera que se lee tal cual.
     */
    private String getKeysetColumn(List<PortableColumnEntity> columns, List<String> primaryKeyColumns) {
        if (primaryKeyColumns.size() != 1) return "";
        for (PortableColumnEntity column : columns) {
            if (column.name().equals(primaryKeyColumns.getFirst()) && DbCopyTypeFamilyEnum.INTEGER.equals(column.family()) && !column.isCastToNumber()) {
                return column.name();
            }
        }
        return "";
    }

    /**
     * Las filas que estima el origen, solo para la barra de progreso; 0 si no lo sabe.
     */
    private long getEstimatedRows(String tableName) throws SQLException {
        String tableRows = getTrimmedString(sourceExplorerReaderRepository.getTableStatus(copyDatabaseDto.getSourceDatabase(), tableName).get("table_rows"));
        return tableRows.matches("\\d{1,18}") ? Long.parseLong(tableRows) : 0;
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

    private record TableIndex(String indexName, boolean isUnique, List<String> columnNames) {
    }

    private record CopyOutcome(boolean isOk, boolean isCancelled, long copiedRows, String message) {

        static CopyOutcome ok(long copiedRows, String message) {
            return new CopyOutcome(true, false, copiedRows, message);
        }

        static CopyOutcome cancelled(long copiedRows) {
            return new CopyOutcome(true, true, copiedRows, __(DbCopyMessageEnum.CANCELLED_TABLE_INCOMPLETE));
        }

        static CopyOutcome failed(String message) {
            return new CopyOutcome(false, false, 0, message == null ? "" : message);
        }
    }
}
