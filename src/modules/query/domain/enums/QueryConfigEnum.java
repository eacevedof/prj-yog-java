package modules.query.domain.enums;

/**
 * Tamaños y sufijos de las pestañas Query.
 */
public final class QueryConfigEnum {

    // la sesion propia con la que se guarda el resultado editable de una pestaña Query: "<sesion>:edit"
    public static final String EDIT_SESSION_SUFFIX = "edit";
    // separacion en pixeles entre los botones de la barra de edicion del resultado
    public static final int TOOLBAR_SPACING = 6;
    // nombre que propone "Exportar SQL" del historial: history-yyMMddHHmmss.sql
    public static final String HISTORY_EXPORT_FILE_NAME_X = "history-%s.sql";

    private QueryConfigEnum() {
    }
}
