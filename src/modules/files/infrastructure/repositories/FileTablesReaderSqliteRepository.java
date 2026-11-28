package modules.files.infrastructure.repositories;

import java.util.List;
import java.util.Map;

import modules.shared.infrastructure.repositories.AbstractSqliteRepository;

/**
 * Lo que se sabe de cada fichero cargado de una conexion "Ficheros" (claves de FilesKeyEnum).
 */
public final class FileTablesReaderSqliteRepository extends AbstractSqliteRepository {

    private FileTablesReaderSqliteRepository() {
    }

    public static FileTablesReaderSqliteRepository getInstance() {
        return new FileTablesReaderSqliteRepository();
    }

    public List<Map<String, Object>> getFileTables(String connectionUuid) {
        String sql = """
            -- getFileTables
            SELECT connection_uuid, table_name, file_name, file_format, csv_delimiter, line_separator, has_bom,
                column_names, column_kinds, explicit_null_columns, file_mtime, file_size, rows_digest, loaded_at
            FROM `app_files_tables`
            WHERE 1=1
            AND connection_uuid = ?
            ORDER BY file_name
            """;
        return query(sql, connectionUuid);
    }
}
