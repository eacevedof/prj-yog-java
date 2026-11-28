package modules.files.infrastructure.repositories;

import java.util.Map;

import modules.shared.infrastructure.repositories.AbstractSqliteRepository;

import modules.files.domain.enums.FilesKeyEnum;

public final class FileTablesWriterSqliteRepository extends AbstractSqliteRepository {

    private FileTablesWriterSqliteRepository() {
    }

    public static FileTablesWriterSqliteRepository getInstance() {
        return new FileTablesWriterSqliteRepository();
    }

    /**
     * Alta o cambio de un fichero cargado (claves de FilesKeyEnum).
     */
    public void saveFileTable(Map<String, Object> fileTable) {
        String sql = """
            -- saveFileTable
            INSERT OR REPLACE INTO `app_files_tables`
            (connection_uuid, table_name, file_name, file_format, csv_delimiter, line_separator, has_bom,
                column_names, column_kinds, explicit_null_columns, file_mtime, file_size, rows_digest, loaded_at)
            VALUES
            (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        execute(sql,
            fileTable.get(FilesKeyEnum.CONNECTION_UUID),
            fileTable.get(FilesKeyEnum.TABLE_NAME),
            fileTable.get(FilesKeyEnum.FILE_NAME),
            fileTable.get(FilesKeyEnum.FILE_FORMAT),
            fileTable.get(FilesKeyEnum.CSV_DELIMITER),
            fileTable.get(FilesKeyEnum.LINE_SEPARATOR),
            fileTable.get(FilesKeyEnum.HAS_BOM),
            fileTable.get(FilesKeyEnum.COLUMN_NAMES),
            fileTable.get(FilesKeyEnum.COLUMN_KINDS),
            fileTable.get(FilesKeyEnum.EXPLICIT_NULL_COLUMNS),
            fileTable.get(FilesKeyEnum.FILE_MTIME),
            fileTable.get(FilesKeyEnum.FILE_SIZE),
            fileTable.get(FilesKeyEnum.ROWS_DIGEST),
            fileTable.get(FilesKeyEnum.LOADED_AT)
        );
    }

    public void deleteFileTable(String connectionUuid, String tableName) {
        String sql = """
            -- deleteFileTable
            DELETE FROM `app_files_tables`
            WHERE 1=1
            AND connection_uuid = ?
            AND table_name = ?
            """;
        execute(sql, connectionUuid, tableName);
    }
}
