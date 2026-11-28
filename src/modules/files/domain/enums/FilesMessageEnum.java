package modules.files.domain.enums;

public final class FilesMessageEnum {

    public static final String FIELD_IS_REQUIRED_X = "{0} is required";
    public static final String CONNECTION_X_NOT_FOUND = "Connection {0} not found";
    public static final String CONNECTION_X_IS_NOT_FILES = "Connection {0} is not a files connection";
    public static final String FOLDER_X_NOT_FOUND = "Folder or data file not found: {0}";
    public static final String JSON_X_NOT_ROWS = "{0} is not a list of objects (an array of objects or one object per line)";
    public static final String JSON_X_WRONG_X = "{0} is not valid JSON: {1}";
    public static final String JSON_X_WRONG_AT_LINE_X_X = "{0} is not valid JSON at line {1}: {2}";
    public static final String FILE_X_NOT_UTF8 = "{0} is not UTF-8 text (save it as UTF-8)";
    public static final String FILE_X_UNREADABLE_X = "Could not read {0}: {1}";

    private FilesMessageEnum() {
    }
}
