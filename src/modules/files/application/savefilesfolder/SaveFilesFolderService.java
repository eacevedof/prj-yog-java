package modules.files.application.savefilesfolder;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import modules.shared.application.Primitives;
import modules.shared.infrastructure.components.date.DateTimer;
import static modules.shared.application.Primitives.getTrimmedString;

import static core.config.Global.__;

import modules.connections.domain.enums.DbEngineEnum;
import modules.connections.domain.entities.ConnectionEntity;
import modules.connections.infrastructure.factories.TargetSessionFactory;
import modules.connections.infrastructure.repositories.ConnectionsReaderSqliteRepository;

import modules.files.domain.enums.FilesColumnKindEnum;
import modules.files.domain.enums.FilesConfigEnum;
import modules.files.domain.enums.FilesFormatEnum;
import modules.files.domain.enums.FilesKeyEnum;
import modules.files.domain.enums.FilesMessageEnum;
import modules.files.domain.entities.FileTableEntity;
import modules.files.domain.services.CsvTableWriter;
import modules.files.domain.services.FileTableStateBuilder;
import modules.files.domain.services.TableRowsDigester;
import modules.files.infrastructure.components.JsonTableConverter;
import modules.files.infrastructure.repositories.FileTablesReaderSqliteRepository;
import modules.files.infrastructure.repositories.FileTablesWriterSqliteRepository;
import modules.files.infrastructure.repositories.FilesReaderDiskRepository;
import modules.files.infrastructure.repositories.FilesWriterDiskRepository;
import modules.files.infrastructure.repositories.WorkingTablesReaderJdbcRepository;
import modules.files.domain.exceptions.FilesException;

/**
 * "Guardar en ficheros": cada tabla cargada que ha cambiado desde que se cargo (o se guardo) vuelve a su fichero, en
 * su formato y con su separador, saltos de linea y BOM; las que no cambiaron no se tocan. Una tabla creada con SQL
 * (CREATE TABLE) se guarda como &lt;tabla&gt;.csv nuevo, sin pisar nunca un fichero que ya exista. Una tabla cargada
 * que se ha borrado no borra su fichero.
 */
public final class SaveFilesFolderService {

    private static final String NEW_FILE_DELIMITER = ",";

    private SaveFilesFolderService() {
    }

    public static SaveFilesFolderService getInstance() {
        return new SaveFilesFolderService();
    }

    /**
     * @throws FilesException
     * @throws SQLException si el SQLite de trabajo falla
     * @throws IOException si un fichero no se puede escribir (los anteriores ya quedaron guardados)
     * @throws modules.connections.domain.exceptions.ConnectionsException
     */
    public SaveFilesFolderResultDto invoke(SaveFilesFolderDto saveFilesFolderDto) throws SQLException, IOException {
        failIfWrongInput(saveFilesFolderDto);
        String connectionUuid = saveFilesFolderDto.getConnectionUuid();
        // la ruta es una carpeta (cada fichero, una tabla) o un fichero concreto (solo esa tabla)
        String filesPath = getFilesConnection(connectionUuid).getDatabaseName();
        FilesReaderDiskRepository filesReaderDiskRepository = FilesReaderDiskRepository.getInstance();
        if (!filesReaderDiskRepository.isFolderOrDataFile(filesPath)) {
            throw FilesException.notFoundCustom(__(FilesMessageEnum.FOLDER_X_NOT_FOUND, filesPath));
        }
        String folder = filesReaderDiskRepository.getFolder(filesPath);

        Connection pdo = TargetSessionFactory.getInstance().getSession(connectionUuid, saveFilesFolderDto.getSessionKey());
        WorkingTablesReaderJdbcRepository workingTablesReaderJdbcRepository = WorkingTablesReaderJdbcRepository.getInstance(pdo);
        Map<String, String> workingTablesByLowerName = new HashMap<>();
        for (String tableName : workingTablesReaderJdbcRepository.getTableNames()) workingTablesByLowerName.put(tableName.toLowerCase(Locale.ROOT), tableName);

        List<String> savedFileNames = new ArrayList<>();
        int unchangedCount = 0;
        String savedAt = DateTimer.getInstance().getNowDt();
        Set<String> knownLowerTables = new HashSet<>();
        for (Map<String, Object> savedTable : FileTablesReaderSqliteRepository.getInstance().getFileTables(connectionUuid)) {
            String tableName = getTrimmedString(savedTable.get(FilesKeyEnum.TABLE_NAME));
            knownLowerTables.add(tableName.toLowerCase(Locale.ROOT));
            if (!workingTablesByLowerName.containsKey(tableName.toLowerCase(Locale.ROOT))) continue;

            FileTableEntity fileTableEntity = getChangedTable(workingTablesReaderJdbcRepository, tableName, savedTable);
            if (fileTableEntity == null) {
                unchangedCount++;
                continue;
            }
            String fileName = getTrimmedString(savedTable.get(FilesKeyEnum.FILE_NAME));
            writeTable(connectionUuid, folder, tableName, fileName, fileTableEntity, savedAt);
            savedFileNames.add(fileName);
        }

        List<String> createdFileNames = new ArrayList<>();
        Set<String> folderLowerFileNames = new HashSet<>();
        for (Map<String, Object> dataFile : filesReaderDiskRepository.getDataFiles(folder)) {
            folderLowerFileNames.add(getTrimmedString(dataFile.get(FilesKeyEnum.FILE_NAME)).toLowerCase(Locale.ROOT));
        }
        for (Map.Entry<String, String> workingTable : workingTablesByLowerName.entrySet()) {
            if (knownLowerTables.contains(workingTable.getKey())) continue;
            String fileName = workingTable.getValue() + "." + FilesFormatEnum.EXTENSION_CSV;
            if (folderLowerFileNames.contains(fileName.toLowerCase(Locale.ROOT))) continue;
            writeTable(connectionUuid, folder, workingTable.getValue(), fileName, getNewCsvTable(workingTablesReaderJdbcRepository, workingTable.getValue()), savedAt);
            createdFileNames.add(fileName);
        }
        return SaveFilesFolderResultDto.fromPrimitives(Primitives.of(
            "savedFileNames", savedFileNames,
            "createdFileNames", createdFileNames,
            "unchangedCount", unchangedCount
        ));
    }

    /**
     * @throws FilesException
     */
    private void failIfWrongInput(SaveFilesFolderDto saveFilesFolderDto) {
        if (saveFilesFolderDto.getConnectionUuid().isEmpty()) {
            throw FilesException.badRequestCustom(__(FilesMessageEnum.FIELD_IS_REQUIRED_X, "connectionUuid"));
        }
        if (saveFilesFolderDto.getSessionKey().isEmpty()) {
            throw FilesException.badRequestCustom(__(FilesMessageEnum.FIELD_IS_REQUIRED_X, "sessionKey"));
        }
    }

    /**
     * @throws FilesException
     */
    private ConnectionEntity getFilesConnection(String connectionUuid) {
        ConnectionEntity connectionEntity = ConnectionsReaderSqliteRepository.getInstance().getConnectionByUuid(connectionUuid);
        if (connectionEntity == null) {
            throw FilesException.notFoundCustom(__(FilesMessageEnum.CONNECTION_X_NOT_FOUND, connectionUuid));
        }
        if (!DbEngineEnum.FILES.equals(connectionEntity.getDbEngine())) {
            throw FilesException.badRequestCustom(__(FilesMessageEnum.CONNECTION_X_IS_NOT_FILES, connectionEntity.getConnectionName()));
        }
        return connectionEntity;
    }

    /**
     * La tabla lista para escribir, o null si su huella es la de cuando se cargo. Una columna anadida con SQL va como
     * texto.
     */
    private FileTableEntity getChangedTable(WorkingTablesReaderJdbcRepository workingTablesReaderJdbcRepository, String tableName, Map<String, Object> savedTable) throws SQLException {
        List<String> columnNames = workingTablesReaderJdbcRepository.getColumnNames(tableName);
        int firstDataColumn = getFirstDataColumn(columnNames);
        List<String> dataColumnNames = columnNames.subList(firstDataColumn, columnNames.size());
        List<List<Object>> dataRows = new ArrayList<>();
        for (List<Object> row : workingTablesReaderJdbcRepository.getRows(tableName)) dataRows.add(row.subList(firstDataColumn, row.size()));
        String rowsDigest = TableRowsDigester.getInstance().getDigest(dataColumnNames, dataRows);
        if (rowsDigest.equals(getTrimmedString(savedTable.get(FilesKeyEnum.ROWS_DIGEST)))) return null;

        FileTableStateBuilder fileTableStateBuilder = FileTableStateBuilder.getInstance();
        List<String> savedNames = fileTableStateBuilder.getList(savedTable.get(FilesKeyEnum.COLUMN_NAMES));
        List<String> savedKinds = fileTableStateBuilder.getList(savedTable.get(FilesKeyEnum.COLUMN_KINDS));
        Map<String, String> kindsByLowerName = new HashMap<>();
        for (int i = 0; i < savedNames.size() && i < savedKinds.size(); i++) kindsByLowerName.put(savedNames.get(i).toLowerCase(Locale.ROOT), savedKinds.get(i));
        List<String> columnKinds = new ArrayList<>();
        for (String columnName : dataColumnNames) columnKinds.add(kindsByLowerName.getOrDefault(columnName.toLowerCase(Locale.ROOT), FilesColumnKindEnum.STRING));
        return FileTableEntity.fromPrimitives(Primitives.of(
            FilesKeyEnum.FILE_FORMAT, savedTable.get(FilesKeyEnum.FILE_FORMAT),
            FilesKeyEnum.CSV_DELIMITER, getSavedDelimiter(savedTable),
            FilesKeyEnum.LINE_SEPARATOR, String.valueOf(savedTable.get(FilesKeyEnum.LINE_SEPARATOR)),
            FilesKeyEnum.HAS_BOM, savedTable.get(FilesKeyEnum.HAS_BOM),
            FilesKeyEnum.COLUMN_NAMES, dataColumnNames,
            FilesKeyEnum.COLUMN_KINDS, columnKinds,
            FilesKeyEnum.EXPLICIT_NULL_COLUMNS, fileTableStateBuilder.getList(savedTable.get(FilesKeyEnum.EXPLICIT_NULL_COLUMNS)),
            FilesKeyEnum.ROWS, dataRows
        ));
    }

    /**
     * El separador tal cual se guardo, sin trim: es un dato donde el blanco importa (el de un TSV es "\t"). Sin
     * separador guardado (JSON), el de los ficheros nuevos.
     */
    private String getSavedDelimiter(Map<String, Object> savedTable) {
        Object csvDelimiter = savedTable.get(FilesKeyEnum.CSV_DELIMITER);
        return csvDelimiter instanceof String delimiter && !delimiter.isEmpty() ? delimiter : NEW_FILE_DELIMITER;
    }

    /**
     * Una tabla creada con SQL: CSV con comas y \n; si empieza por la columna clave de las cargadas, sin ella.
     */
    private FileTableEntity getNewCsvTable(WorkingTablesReaderJdbcRepository workingTablesReaderJdbcRepository, String tableName) throws SQLException {
        List<String> columnNames = workingTablesReaderJdbcRepository.getColumnNames(tableName);
        int firstDataColumn = getFirstDataColumn(columnNames);
        List<List<Object>> dataRows = new ArrayList<>();
        for (List<Object> row : workingTablesReaderJdbcRepository.getRows(tableName)) dataRows.add(row.subList(firstDataColumn, row.size()));
        List<String> dataColumnNames = columnNames.subList(firstDataColumn, columnNames.size());
        List<String> columnKinds = new ArrayList<>();
        for (int i = 0; i < dataColumnNames.size(); i++) columnKinds.add(FilesColumnKindEnum.STRING);
        return FileTableEntity.fromPrimitives(Primitives.of(
            FilesKeyEnum.FILE_FORMAT, FilesFormatEnum.CSV,
            FilesKeyEnum.CSV_DELIMITER, NEW_FILE_DELIMITER,
            FilesKeyEnum.LINE_SEPARATOR, FilesConfigEnum.LINE_SEPARATOR_LF,
            FilesKeyEnum.HAS_BOM, false,
            FilesKeyEnum.COLUMN_NAMES, dataColumnNames,
            FilesKeyEnum.COLUMN_KINDS, columnKinds,
            FilesKeyEnum.EXPLICIT_NULL_COLUMNS, List.of(),
            FilesKeyEnum.ROWS, dataRows
        ));
    }

    /**
     * 1 si la tabla empieza por la columna clave de las cargadas (no es un dato del fichero); 0 si no (creada con SQL).
     */
    private int getFirstDataColumn(List<String> columnNames) {
        return !columnNames.isEmpty() && FilesConfigEnum.ROW_COLUMN.equals(columnNames.getFirst()) ? 1 : 0;
    }

    /**
     * El fichero en su formato y lo que se sabe de el, con la nueva huella: hasta que vuelva a cambiar, no se reescribe.
     * @throws IOException si no se puede escribir
     */
    private void writeTable(String connectionUuid, String folder, String tableName, String fileName, FileTableEntity fileTableEntity, String savedAt) throws IOException {
        String text = FilesFormatEnum.JSON_FORMATS.contains(fileTableEntity.getFormat())
            ? JsonTableConverter.getInstance().getText(fileTableEntity)
            : CsvTableWriter.getInstance().getText(fileTableEntity);
        FilesWriterDiskRepository.getInstance().writeText(folder, fileName, text);
        String rowsDigest = TableRowsDigester.getInstance().getDigest(fileTableEntity.getColumnNames(), fileTableEntity.getRows());
        FileTablesWriterSqliteRepository.getInstance().saveFileTable(FileTableStateBuilder.getInstance().getFileTableState(
            connectionUuid, tableName, fileName, fileTableEntity, FilesReaderDiskRepository.getInstance().getFileState(folder, fileName), rowsDigest, savedAt
        ));
    }
}
