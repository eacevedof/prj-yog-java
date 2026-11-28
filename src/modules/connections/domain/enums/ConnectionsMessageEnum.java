package modules.connections.domain.enums;

public final class ConnectionsMessageEnum {

    public static final String FIELD_IS_REQUIRED = "field is required";
    public static final String MAX_LENGTH_IS_X = "max length is {0}";
    public static final String ALLOWED_VALUES_ARE_X = "allowed values are {0}";
    public static final String VALUE_BETWEEN_X_AND_X = "value must be between {0} and {1}";
    public static final String INVALID_COLOR_X = "invalid color, expected format {0}";
    public static final String CONNECTION_X_NOT_FOUND = "Connection {0} not found";
    public static final String CONNECTION_NAME_X_ALREADY_EXISTS = "Connection name {0} already exists";
    public static final String CONNECTION_X_FAILED_X = "Connection {0} failed: {1}";
    public static final String CONNECTION_X_OK_SERVER_X = "Connection {0} OK. Server: {1}";
    public static final String SQLITE_FOLDER_X_NEW_FILE_ON_SAVE = "{0} is a folder: an empty .sqlite file will be created in it when the connection is saved";
    public static final String COPY_OF_X = "{0} (copy)";
    public static final String SSH_PASSWORD_OR_KEY_REQUIRED = "an SSH password or a private key is required";
    public static final String SSH_TUNNEL_FAILED_X = "SSH tunnel failed: {0}";
    public static final String HTTP_TUNNEL_FAILED_X = "HTTP tunnel failed: {0}";
    public static final String HTTP_URL_INVALID = "must start with http:// or https://";
    public static final String ONLY_ONE_TUNNEL = "use either the SSH tunnel or the HTTP tunnel, not both";
    public static final String SSH_NOT_FOR_SQLITE = "an SSH tunnel does not apply to SQLite (use the HTTP tunnel for a remote file)";
    public static final String SQLITE_FILE_X_NOT_FOUND = "SQLite file not found: {0}";
    public static final String NO_TUNNEL_FOR_FILES = "a files connection reads a local folder: no SSH or HTTP tunnel";
    public static final String FILES_FOLDER_X_NOT_FOUND = "Folder or data file not found: {0}";
    public static final String FILES_FOLDER_X_WITH_X_FILES = "Folder {0}: {1} CSV or JSON file(s)";
    public static final String FILES_FILE_X_OK = "File {0}: one table";
    public static final String HTTP_TUNNEL_NOT_FOR_X = "the HTTP tunnel (SQLyog PHP) does not support {0}";
    public static final String MONGODB_URI_WITHOUT_CREDENTIALS = "write user and password in their own fields, not in the connection string";
    public static final String MONGODB_URI_WITHOUT_SSH = "a connection string brings its own hosts: it cannot go through the SSH tunnel";
    public static final String MONGODB_URI_WITHOUT_HTTP = "a connection string brings its own hosts: it cannot go through the HTTP tunnel (it takes host and port)";

    private ConnectionsMessageEnum() {
    }
}
