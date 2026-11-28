package modules.explorer.domain.enums;

/**
 * Las claves de lo que devuelve el arbol: columnas de information_schema en snake_case.
 */
public final class ExplorerKeyEnum {

    public static final String DATABASE_NAME = "database_name";
    public static final String TABLES = "tables";
    public static final String VIEWS = "views";
    public static final String PROCEDURES = "procedures";
    public static final String FUNCTIONS = "functions";
    public static final String TRIGGERS = "triggers";
    public static final String COLUMN_NAME = "column_name";
    public static final String COLUMN_TYPE = "column_type";
    public static final String IS_NULLABLE = "is_nullable";
    public static final String COLUMN_KEY = "column_key";
    public static final String DATA_TYPE = "data_type";
    public static final String EXTRA = "extra";

    private ExplorerKeyEnum() {
    }
}
