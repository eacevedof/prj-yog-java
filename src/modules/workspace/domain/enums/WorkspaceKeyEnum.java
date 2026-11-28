package modules.workspace.domain.enums;

import java.util.List;

/**
 * Lo que se guarda del espacio de trabajo para recuperarlo al reiniciar: las claves de app_workspace_state y las
 * columnas de app_query_tabs (snake_case, contrato con la BD).
 */
public final class WorkspaceKeyEnum {

    // app_workspace_state: los filtros del panel de conexiones
    public static final String DATABASE_FILTER = "panel.database_filter";
    public static final String OBJECT_FILTER = "panel.object_filter";
    // app_workspace_state: la BD activa de una conexion (la clave lleva detras el uuid de la conexion)
    public static final String ACTIVE_DATABASE_PREFIX = "connection.active_database.";
    // app_workspace_state: la ultima carpeta elegida en cada dialogo de ficheros (manda la ultima)
    public static final String SQL_DUMP_DIRECTORY = "directory.sql_dump";
    public static final String RESULT_EXPORT_DIRECTORY = "directory.result_export";
    public static final String SQL_FILE_DIRECTORY = "directory.sql_file";
    // app_workspace_state: el ultimo fichero ejecutado con "Ejecutar fichero SQL" (la ruta entera)
    public static final String SQL_FILE_LAST_FILE = "file.sql_file_last";

    public static final List<String> STATE_KEYS = List.of(
        DATABASE_FILTER, OBJECT_FILTER, SQL_DUMP_DIRECTORY, RESULT_EXPORT_DIRECTORY, SQL_FILE_DIRECTORY, SQL_FILE_LAST_FILE
    );
    public static final List<String> STATE_KEY_PREFIXES = List.of(ACTIVE_DATABASE_PREFIX);

    // app_query_tabs: una fila por pestaña de query de una conexion
    public static final String CONNECTION_UUID = "connection_uuid";
    public static final String TAB_POSITION = "tab_position";
    public static final String TAB_TITLE = "tab_title";
    public static final String SQL_TEXT = "sql_text";
    public static final String IS_SELECTED = "is_selected";

    /**
     * Una clave de app_workspace_state que existe: una de STATE_KEYS o un prefijo con algo detras.
     */
    public static boolean isStateKey(String stateKey) {
        if (stateKey == null) return false;
        if (STATE_KEYS.contains(stateKey)) return true;
        for (String keyPrefix : STATE_KEY_PREFIXES) {
            if (stateKey.startsWith(keyPrefix) && stateKey.length() > keyPrefix.length()) return true;
        }
        return false;
    }

    private WorkspaceKeyEnum() {
    }
}
