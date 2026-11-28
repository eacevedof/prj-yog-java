package modules.files.domain.enums;

/**
 * Las claves de lo que se sabe de cada fichero cargado (app_files_tables) y de un fichero de la carpeta.
 */
public final class FilesKeyEnum {

    public static final String CONNECTION_UUID = "connection_uuid";
    public static final String TABLE_NAME = "table_name";
    public static final String FILE_NAME = "file_name";
    public static final String FILE_FORMAT = "file_format";
    public static final String CSV_DELIMITER = "csv_delimiter";
    public static final String LINE_SEPARATOR = "line_separator";
    public static final String HAS_BOM = "has_bom";
    public static final String COLUMN_NAMES = "column_names";
    public static final String COLUMN_KINDS = "column_kinds";
    public static final String EXPLICIT_NULL_COLUMNS = "explicit_null_columns";
    public static final String FILE_MTIME = "file_mtime";
    public static final String FILE_SIZE = "file_size";
    public static final String ROWS_DIGEST = "rows_digest";
    public static final String LOADED_AT = "loaded_at";
    public static final String ROWS = "rows";

    private FilesKeyEnum() {
    }
}
