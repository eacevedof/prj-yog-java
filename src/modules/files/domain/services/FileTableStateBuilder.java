package modules.files.domain.services;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import modules.shared.application.Primitives;

import modules.files.domain.enums.FilesConfigEnum;
import modules.files.domain.enums.FilesKeyEnum;
import modules.files.domain.entities.FileTableEntity;

/**
 * Lo que se guarda de un fichero cargado (app_files_tables) y lo que se recupera de ello para escribirlo igual.
 */
public final class FileTableStateBuilder {

    private FileTableStateBuilder() {
    }

    public static FileTableStateBuilder getInstance() {
        return new FileTableStateBuilder();
    }

    /**
     * @param fileState FILE_MTIME y FILE_SIZE del fichero tal como queda en disco
     */
    public Map<String, Object> getFileTableState(
        String connectionUuid, String tableName, String fileName, FileTableEntity fileTableEntity, Map<String, Object> fileState, String rowsDigest, String loadedAt
    ) {
        return Primitives.of(
            FilesKeyEnum.CONNECTION_UUID, connectionUuid,
            FilesKeyEnum.TABLE_NAME, tableName,
            FilesKeyEnum.FILE_NAME, fileName,
            FilesKeyEnum.FILE_FORMAT, fileTableEntity.getFormat(),
            FilesKeyEnum.CSV_DELIMITER, fileTableEntity.getCsvDelimiter(),
            FilesKeyEnum.LINE_SEPARATOR, fileTableEntity.getLineSeparator(),
            FilesKeyEnum.HAS_BOM, fileTableEntity.hasBom() ? 1 : 0,
            FilesKeyEnum.COLUMN_NAMES, String.join(FilesConfigEnum.LIST_SEPARATOR, fileTableEntity.getColumnNames()),
            FilesKeyEnum.COLUMN_KINDS, String.join(FilesConfigEnum.LIST_SEPARATOR, fileTableEntity.getColumnKinds()),
            FilesKeyEnum.EXPLICIT_NULL_COLUMNS, String.join(FilesConfigEnum.LIST_SEPARATOR, fileTableEntity.getExplicitNullColumns()),
            FilesKeyEnum.FILE_MTIME, fileState.get(FilesKeyEnum.FILE_MTIME),
            FilesKeyEnum.FILE_SIZE, fileState.get(FilesKeyEnum.FILE_SIZE),
            FilesKeyEnum.ROWS_DIGEST, rowsDigest,
            FilesKeyEnum.LOADED_AT, loadedAt
        );
    }

    /**
     * Una lista guardada (nombres o tipos de columna); vacia si no habia columnas.
     */
    public List<String> getList(Object storedList) {
        String text = storedList == null ? "" : String.valueOf(storedList);
        return text.isEmpty() ? List.of() : Arrays.asList(text.split(FilesConfigEnum.LIST_SEPARATOR, -1));
    }
}
