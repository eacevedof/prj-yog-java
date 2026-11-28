package modules.connections.domain.enums;

/**
 * Las claves de una conexion: las columnas de app_connections (snake_case, contrato con la BD).
 */
public final class ConnectionsKeyEnum {

    public static final String ID = "id";
    public static final String CONNECTION_UUID = "connection_uuid";
    public static final String CONNECTION_NAME = "connection_name";
    public static final String DB_ENGINE = "db_engine";
    public static final String HOST = "host";
    public static final String PORT = "port";
    public static final String USER_NAME = "user_name";
    public static final String USER_PASSWORD = "user_password";
    public static final String DATABASE_NAME = "database_name";
    public static final String COLOR_HEX = "color_hex";
    public static final String IS_PRODUCTION = "is_production";
    public static final String SSH_ENABLED = "ssh_enabled";
    public static final String SSH_HOST = "ssh_host";
    public static final String SSH_PORT = "ssh_port";
    public static final String SSH_USER = "ssh_user";
    public static final String SSH_PASSWORD = "ssh_password";
    public static final String SSH_PRIVATE_KEY_PATH = "ssh_private_key_path";
    public static final String SSH_PASSPHRASE = "ssh_passphrase";
    public static final String HTTP_ENABLED = "http_enabled";
    public static final String HTTP_URL = "http_url";
    public static final String HTTP_TIMEOUT_S = "http_timeout_s";
    public static final String CREATED_AT = "created_at";
    public static final String UPDATED_AT = "updated_at";
    // las columnas de cada tunel empiezan asi (los errores de validacion llevan delante la columna)
    public static final String SSH_PREFIX = "ssh_";
    public static final String HTTP_PREFIX = "http_";

    private ConnectionsKeyEnum() {
    }
}
