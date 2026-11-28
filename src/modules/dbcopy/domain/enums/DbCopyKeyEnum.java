package modules.dbcopy.domain.enums;

/**
 * Las claves de lo que devuelve la copia: objetos por tipo, columnas y juego de caracteres del origen y el resumen por
 * objeto.
 */
public final class DbCopyKeyEnum {

    public static final String TABLES = "tables";
    public static final String VIEWS = "views";
    public static final String PROCEDURES = "procedures";
    public static final String FUNCTIONS = "functions";
    public static final String TRIGGERS = "triggers";
    public static final String EVENTS = "events";
    // trigger => su tabla, para "Copiar triggers asociados"
    public static final String TRIGGER_TABLES = "trigger_tables";

    public static final String CHARACTER_SET = "character_set";
    public static final String COLLATION = "collation";
    public static final String COLUMN_NAME = "column_name";
    public static final String DATA_TYPE = "data_type";

    public static final String OBJECT_TYPE = "object_type";
    public static final String OBJECT_NAME = "object_name";
    public static final String IS_OK = "is_ok";
    public static final String COPIED_ROWS = "copied_rows";
    public static final String MESSAGE = "message";

    private DbCopyKeyEnum() {
    }
}
