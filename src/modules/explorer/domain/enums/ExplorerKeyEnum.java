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
    public static final String INDEX_NAME = "index_name";
    public static final String INDEX_TYPE = "index_type";
    public static final String NON_UNIQUE = "non_unique";
    public static final String TABLE_ROWS = "table_rows";
    public static final String TABLE_TYPE = "table_type";

    // valores de MySQL que los repositorios de los demas motores imitan: tipo de tabla, column_key e indice de la PK
    public static final String TABLE_TYPE_VIEW = "VIEW";
    public static final String TABLE_TYPE_BASE_TABLE = "BASE TABLE";
    public static final String COLUMN_KEY_PRIMARY = "PRI";
    public static final String COLUMN_KEY_UNIQUE = "UNI";
    public static final String PRIMARY_INDEX_NAME = "PRIMARY";

    // el juego de caracteres y la collation por defecto del servidor (DatabaseOptionsReaderMysqlRepository)
    public static final String CHARACTER_SET = "characterSet";
    public static final String COLLATION = "collation";

    private ExplorerKeyEnum() {
    }
}
