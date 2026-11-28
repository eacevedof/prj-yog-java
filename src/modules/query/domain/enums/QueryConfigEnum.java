package modules.query.domain.enums;

/**
 * Nombres fijos de las pestañas Query.
 */
public final class QueryConfigEnum {

    // nombre que propone "Exportar SQL" del historial: history-yyMMddHHmmss.sql
    public static final String HISTORY_EXPORT_FILE_NAME_X = "history-%s.sql";
    // prefijo de la sesion de cada "Ejecutar fichero SQL": "sqlfile-<uuid>:run"
    public static final String SQL_FILE_SESSION_PREFIX = "sqlfile-";

    private QueryConfigEnum() {
    }
}
