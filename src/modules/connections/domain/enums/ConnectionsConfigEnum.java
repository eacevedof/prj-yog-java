package modules.connections.domain.enums;

/**
 * Valores fijos de las conexiones: prefijo del uuid, valores por defecto y topes de los campos.
 */
public final class ConnectionsConfigEnum {

    public static final String CONNECTION_UUID_PREFIX = "conn-";
    public static final int DEFAULT_SSH_PORT = 22;
    public static final int DEFAULT_HTTP_TIMEOUT_S = 30;

    public static final int CONNECTION_NAME_MAX_LENGTH = 100;
    public static final int HOST_MAX_LENGTH = 255;
    public static final int USER_NAME_MAX_LENGTH = 100;
    public static final int DATABASE_NAME_MAX_LENGTH = 100;
    public static final int SQLITE_FILE_MAX_LENGTH = 1000;
    public static final int SSH_KEY_PATH_MAX_LENGTH = 500;
    public static final int HTTP_URL_MAX_LENGTH = 1000;
    public static final int HTTP_TIMEOUT_MIN_S = 1;
    public static final int HTTP_TIMEOUT_MAX_S = 3600;
    public static final int PORT_MIN = 1;
    public static final int PORT_MAX = 65535;
    public static final String COLOR_HEX_FORMAT = "#RRGGBB";

    // un host de MongoDB que empieza asi es una cadena de conexion (mongodb:// o mongodb+srv://), sin credenciales (@)
    public static final String MONGODB_URI_SCHEME = "mongodb";
    public static final String MONGODB_URI_PREFIX = "mongodb://";
    public static final String MONGODB_SRV_URI_PREFIX = "mongodb+srv://";
    public static final String URI_CREDENTIALS_SEPARATOR = "@";
    // la URL del tunel HTTP
    public static final String HTTP_URL_PREFIX = "http://";
    public static final String HTTPS_URL_PREFIX = "https://";

    private ConnectionsConfigEnum() {
    }
}
