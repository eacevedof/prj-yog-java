package modules.query.domain.enums;

public final class QueryMessageEnum {

    public static final String FIELD_IS_REQUIRED_X = "{0} is required";
    public static final String NOTHING_TO_EXECUTE = "Nothing to execute: the editor has no statements";
    public static final String ERROR_CODE_X_X = "Error Code: {0} - {1}";
    public static final String CANCELLED_BY_USER = "Cancelled by the user";
    public static final String PRODUCTION_X_CONFIRM_X = "{0} is a production connection. Confirm these statements:\n\n{1}";
    public static final String PRODUCTION_X_SAVE_X_CHANGES = "{0} is a production connection. Save {1} change(s)?";
    public static final String TABLE_X_WITHOUT_PRIMARY_KEY = "Table {0} has no primary key: its data cannot be edited";
    public static final String CHANGES_NOT_SAVED_X = "Changes not saved (rolled back): {0}";
    public static final String NO_CHANGES_TO_SAVE = "There are no changes to save";
    public static final String PRODUCTION_X_RUN_FILE_X = "{0} is a production connection: the whole file {1} will be executed";
    public static final String CANNOT_READ_X_X = "Cannot read {0}: {1}";

    private QueryMessageEnum() {
    }
}
