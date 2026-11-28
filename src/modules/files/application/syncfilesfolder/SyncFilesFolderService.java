package modules.files.application.syncfilesfolder;

import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.bson.BSONException;
import org.bson.json.JsonParseException;

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
import modules.files.domain.services.CsvTableReader;
import modules.files.domain.services.FileTableNameBuilder;
import modules.files.domain.services.FileTableStateBuilder;
import modules.files.domain.services.TableRowsDigester;
import modules.files.infrastructure.components.JsonTableConverter;
import modules.files.infrastructure.repositories.FileTablesReaderSqliteRepository;
import modules.files.infrastructure.repositories.FileTablesWriterSqliteRepository;
import modules.files.infrastructure.repositories.FilesReaderDiskRepository;
import modules.files.infrastructure.repositories.WorkingTablesReaderJdbcRepository;
import modules.files.infrastructure.repositories.WorkingTablesWriterJdbcRepository;
import modules.files.domain.exceptions.FilesException;
import modules.files.infrastructure.components.JsonLineParseException;

/**
 * Motor "Ficheros", al abrir la conexion (o "Recargar desde ficheros"): cada CSV o JSON de la carpeta pasa a ser una
 * tabla del SQLite de trabajo. Solo se cargan los nuevos y los que cambiaron en disco desde la ultima vez: los demas
 * se quedan como estan, con lo que se haya editado y aun no se haya guardado. Las tablas de ficheros que ya no estan se
 * quitan.
 * <p>
 * Excepcion declarada a "los servicios no capturan": un fichero ilegible o con JSON roto no impide cargar los demas;
 * su error se devuelve en el resultado.
 */
public final class SyncFilesFolderService {

    private SyncFilesFolderService() {
    }

    public static SyncFilesFolderService getInstance() {
        return new SyncFilesFolderService();
    }

    /**
     * @throws FilesException
     * @throws SQLException si el SQLite de trabajo falla
     * @throws modules.connections.domain.exceptions.ConnectionsException
     */
    public SyncFilesFolderResultDto invoke(SyncFilesFolderDto syncFilesFolderDto) throws SQLException {
        failIfWrongInput(syncFilesFolderDto);
        String connectionUuid = syncFilesFolderDto.getConnectionUuid();
        // la ruta es una carpeta (cada fichero, una tabla) o un fichero concreto (solo esa tabla)
        String filesPath = getFilesConnection(connectionUuid).getDatabaseName();
        FilesReaderDiskRepository filesReaderDiskRepository = FilesReaderDiskRepository.getInstance();
        if (!filesReaderDiskRepository.isFolderOrDataFile(filesPath)) {
            throw FilesException.notFoundCustom(__(FilesMessageEnum.FOLDER_X_NOT_FOUND, filesPath));
        }
        String folder = filesReaderDiskRepository.getFolder(filesPath);

        Connection pdo = TargetSessionFactory.getInstance().getSession(connectionUuid, syncFilesFolderDto.getSessionKey());
        WorkingTablesReaderJdbcRepository workingTablesReaderJdbcRepository = WorkingTablesReaderJdbcRepository.getInstance(pdo);
        WorkingTablesWriterJdbcRepository workingTablesWriterJdbcRepository = WorkingTablesWriterJdbcRepository.getInstance(pdo);
        FileTablesWriterSqliteRepository fileTablesWriterSqliteRepository = FileTablesWriterSqliteRepository.getInstance();

        Map<String, Map<String, Object>> savedTablesByFile = new HashMap<>();
        for (Map<String, Object> savedTable : FileTablesReaderSqliteRepository.getInstance().getFileTables(connectionUuid)) {
            savedTablesByFile.put(getTrimmedString(savedTable.get(FilesKeyEnum.FILE_NAME)), savedTable);
        }
        Set<String> workingLowerNames = new HashSet<>();
        for (String tableName : workingTablesReaderJdbcRepository.getTableNames()) workingLowerNames.add(tableName.toLowerCase(Locale.ROOT));
        Set<String> usedLowerNames = new HashSet<>(workingLowerNames);
        for (Map<String, Object> savedTable : savedTablesByFile.values()) {
            usedLowerNames.add(getTrimmedString(savedTable.get(FilesKeyEnum.TABLE_NAME)).toLowerCase(Locale.ROOT));
        }

        int loadedCount = 0;
        int unchangedCount = 0;
        List<String> errors = new ArrayList<>();
        String loadedAt = DateTimer.getInstance().getNowDt();
        for (Map<String, Object> dataFile : filesReaderDiskRepository.getDataFiles(filesPath)) {
            String fileName = getTrimmedString(dataFile.get(FilesKeyEnum.FILE_NAME));
            Map<String, Object> savedTable = savedTablesByFile.remove(fileName);
            if (!syncFilesFolderDto.isReloadForced() && isUnchanged(savedTable, dataFile, workingLowerNames)) {
                unchangedCount++;
                continue;
            }
            String tableName = savedTable != null
                ? getTrimmedString(savedTable.get(FilesKeyEnum.TABLE_NAME))
                : FileTableNameBuilder.getInstance().getTableName(fileName, usedLowerNames);
            usedLowerNames.add(tableName.toLowerCase(Locale.ROOT));
            FileTableEntity fileTableEntity;
            try {
                fileTableEntity = getFileTable(filesReaderDiskRepository, folder, fileName);
            }
            // cada error dice el fichero y, si se sabe, la linea: los demas ficheros se cargan igual
            catch (CharacterCodingException e) {
                errors.add(__(FilesMessageEnum.FILE_X_NOT_UTF8, fileName));
                continue;
            }
            catch (IOException e) {
                errors.add(__(FilesMessageEnum.FILE_X_UNREADABLE_X, fileName, e.getMessage()));
                continue;
            }
            catch (JsonLineParseException e) {
                errors.add(__(FilesMessageEnum.JSON_X_WRONG_AT_LINE_X_X, fileName, e.getLineNumber(), e.getDetail()));
                continue;
            }
            catch (JsonParseException | BSONException e) {
                errors.add(__(FilesMessageEnum.JSON_X_WRONG_X, fileName, e.getMessage()));
                continue;
            }
            if (fileTableEntity == null) {
                errors.add(__(FilesMessageEnum.JSON_X_NOT_ROWS, fileName));
                continue;
            }
            List<String> sqlTypes = new ArrayList<>();
            for (String columnKind : fileTableEntity.getColumnKinds()) sqlTypes.add(FilesColumnKindEnum.SQLITE_TYPES.get(columnKind));
            workingTablesWriterJdbcRepository.replaceTable(tableName, FilesConfigEnum.ROW_COLUMN, fileTableEntity.getColumnNames(), sqlTypes, fileTableEntity.getRows());

            fileTablesWriterSqliteRepository.saveFileTable(FileTableStateBuilder.getInstance().getFileTableState(
                connectionUuid, tableName, fileName, fileTableEntity, dataFile, getRowsDigest(workingTablesReaderJdbcRepository, tableName), loadedAt
            ));
            loadedCount++;
        }

        for (Map<String, Object> goneTable : savedTablesByFile.values()) {
            String tableName = getTrimmedString(goneTable.get(FilesKeyEnum.TABLE_NAME));
            workingTablesWriterJdbcRepository.dropTable(tableName);
            fileTablesWriterSqliteRepository.deleteFileTable(connectionUuid, tableName);
        }
        return SyncFilesFolderResultDto.fromPrimitives(Primitives.of(
            "loadedCount", loadedCount,
            "unchangedCount", unchangedCount,
            "removedCount", savedTablesByFile.size(),
            "errors", errors
        ));
    }

    /**
     * @throws FilesException
     */
    private void failIfWrongInput(SyncFilesFolderDto syncFilesFolderDto) {
        if (syncFilesFolderDto.getConnectionUuid().isEmpty()) {
            throw FilesException.badRequestCustom(__(FilesMessageEnum.FIELD_IS_REQUIRED_X, "connectionUuid"));
        }
        if (syncFilesFolderDto.getSessionKey().isEmpty()) {
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
     * Ya cargado, con la misma fecha y tamano en disco y su tabla aun en el SQLite de trabajo.
     */
    private boolean isUnchanged(Map<String, Object> savedTable, Map<String, Object> dataFile, Set<String> workingLowerNames) {
        if (savedTable == null) return false;
        if (!workingLowerNames.contains(getTrimmedString(savedTable.get(FilesKeyEnum.TABLE_NAME)).toLowerCase(Locale.ROOT))) return false;
        return getTrimmedString(savedTable.get(FilesKeyEnum.FILE_MTIME)).equals(getTrimmedString(dataFile.get(FilesKeyEnum.FILE_MTIME)))
            && getTrimmedString(savedTable.get(FilesKeyEnum.FILE_SIZE)).equals(getTrimmedString(dataFile.get(FilesKeyEnum.FILE_SIZE)));
    }

    /**
     * null si es un JSON que no es una lista de objetos.
     * @throws IOException si no se puede leer
     */
    private FileTableEntity getFileTable(FilesReaderDiskRepository filesReaderDiskRepository, String folder, String fileName) throws IOException {
        String extension = filesReaderDiskRepository.getExtension(fileName);
        String format = FilesFormatEnum.FORMATS_BY_EXTENSION.get(extension);
        String text = filesReaderDiskRepository.getText(folder, fileName);
        if (FilesFormatEnum.CSV.equals(format)) return CsvTableReader.getInstance().getTable(text, extension);
        return JsonTableConverter.getInstance().getTable(text, format);
    }

    /**
     * La huella de la tabla tal como quedo en SQLite (sin la columna clave): la que se compara al guardar.
     */
    private String getRowsDigest(WorkingTablesReaderJdbcRepository workingTablesReaderJdbcRepository, String tableName) throws SQLException {
        List<String> columnNames = workingTablesReaderJdbcRepository.getColumnNames(tableName);
        int firstDataColumn = getFirstDataColumn(columnNames);
        List<List<Object>> dataRows = new ArrayList<>();
        for (List<Object> row : workingTablesReaderJdbcRepository.getRows(tableName)) dataRows.add(row.subList(firstDataColumn, row.size()));
        return TableRowsDigester.getInstance().getDigest(columnNames.subList(firstDataColumn, columnNames.size()), dataRows);
    }

    /**
     * La columna clave (_row) solo se salta si esta: la misma regla que SaveFilesFolderService, o las huellas no casan.
     */
    private int getFirstDataColumn(List<String> columnNames) {
        return !columnNames.isEmpty() && FilesConfigEnum.ROW_COLUMN.equals(columnNames.getFirst()) ? 1 : 0;
    }
}
