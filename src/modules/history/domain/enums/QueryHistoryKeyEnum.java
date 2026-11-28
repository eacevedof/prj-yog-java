package modules.history.domain.enums;

/**
 * Las claves del historial: las columnas de app_query_history (snake_case, contrato con la BD).
 */
public final class QueryHistoryKeyEnum {

    public static final String ID = "id";
    public static final String CONNECTION_UUID = "connection_uuid";
    public static final String CONNECTION_NAME = "connection_name";
    public static final String DATABASE_NAME = "database_name";
    public static final String SQL_QUERY = "sql_query";
    public static final String EXECUTED_AT = "executed_at";
    public static final String ELAPSED_MS = "elapsed_ms";
    public static final String ROWS_COUNT = "rows_count";
    public static final String HAS_ERROR = "has_error";

    private QueryHistoryKeyEnum() {
    }
}
