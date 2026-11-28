package modules.connections.domain.enums;

/**
 * Las claves de los tuneles en el formulario de conexion (camelCase, las del dialogo y de los DTO de alta, edicion y
 * prueba), que TunnelFormPrimitives pasa a las columnas de ConnectionsKeyEnum.
 */
public final class TunnelFormKeyEnum {

    public static final String SSH_ENABLED = "sshEnabled";
    public static final String SSH_HOST = "sshHost";
    public static final String SSH_PORT = "sshPort";
    public static final String SSH_USER = "sshUser";
    public static final String SSH_PASSWORD = "sshPassword";
    public static final String SSH_PRIVATE_KEY_PATH = "sshPrivateKeyPath";
    public static final String SSH_PASSPHRASE = "sshPassphrase";
    // en edicion los secretos SSH solo cambian si se tocan
    public static final String IS_SSH_SECRETS_CHANGED = "isSshSecretsChanged";
    public static final String HTTP_ENABLED = "httpEnabled";
    public static final String HTTP_URL = "httpUrl";
    public static final String HTTP_TIMEOUT_S = "httpTimeoutS";
    // en edicion la URL del tunel (lleva el token) solo cambia si se toca
    public static final String IS_HTTP_URL_CHANGED = "isHttpUrlChanged";

    private TunnelFormKeyEnum() {
    }
}
