package modules.query.domain.enums;

/**
 * Las claves del resultado de una sentencia: lo que viaja del repositorio a la UI (rejilla, texto y mensajes).
 */
public final class QueryKeyEnum {

    public static final String SQL = "sql";
    public static final String HAS_RESULT_SET = "has_result_set";
    public static final String COLUMN_NAMES = "column_names";
    public static final String ROWS = "rows";
    public static final String AFFECTED_ROWS = "affected_rows";
    public static final String IS_TRUNCATED = "is_truncated";
    public static final String ELAPSED_MS = "elapsed_ms";
    public static final String ERROR_MESSAGE = "error_message";
    // cada error de "Ejecutar fichero SQL": la sentencia (recortada) y su mensaje
    public static final String MESSAGE = "message";

    private QueryKeyEnum() {
    }
}
