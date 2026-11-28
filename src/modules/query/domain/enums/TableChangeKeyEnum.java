package modules.query.domain.enums;

/**
 * Un cambio de la rejilla editable (feature 6): tipo, valores de la clave primaria ORIGINALES (para el WHERE) y
 * valores nuevos (columna => valor; null = NULL).
 */
public final class TableChangeKeyEnum {

    public static final String CHANGE_TYPE = "change_type";
    public static final String KEY_VALUES = "key_values";
    public static final String NEW_VALUES = "new_values";
    // columnas que se ponen a su DEFAULT (menu "Poner DEFAULT"): van como DEFAULT en el SQL, sin parametro
    public static final String DEFAULT_COLUMNS = "default_columns";

    public static final String TYPE_INSERT = "insert";
    public static final String TYPE_UPDATE = "update";
    public static final String TYPE_DELETE = "delete";

    private TableChangeKeyEnum() {
    }
}
