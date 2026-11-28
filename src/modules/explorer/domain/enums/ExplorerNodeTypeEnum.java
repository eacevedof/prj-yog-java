package modules.explorer.domain.enums;

/**
 * Los tipos de nodo del arbol de objetos: dicen que menu contextual lleva y que se carga al desplegarlo.
 */
public final class ExplorerNodeTypeEnum {

    public static final String DATABASE = "database";
    public static final String FOLDER = "folder";
    public static final String TABLE = "table";
    public static final String VIEW = "view";
    public static final String PROCEDURE = "procedure";
    public static final String FUNCTION = "function";
    public static final String TRIGGER = "trigger";
    public static final String COLUMN = "column";
    public static final String LOADING = "loading";
    public static final String ERROR = "error";

    private ExplorerNodeTypeEnum() {
    }
}
