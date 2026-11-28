package modules.query.domain.enums;

/**
 * El estado de una fila de la rejilla editable hasta que se guarda (TableEditSession).
 */
public final class TableEditRowStateEnum {

    public static final String STATE_NEW = "new";
    public static final String STATE_MODIFIED = "modified";
    public static final String STATE_DELETED = "deleted";

    private TableEditRowStateEnum() {
    }
}
