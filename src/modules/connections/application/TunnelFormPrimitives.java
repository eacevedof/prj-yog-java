package modules.connections.application;

import java.util.Map;

import modules.shared.application.Primitives;
import static modules.shared.application.Primitives.getBool;
import static modules.shared.application.Primitives.getInt;
import static modules.shared.application.Primitives.getTrimmedString;

import modules.connections.domain.enums.ConnectionsKeyEnum;
import modules.connections.domain.entities.ConnectionEntity;

/**
 * Los tuneles del formulario de conexion (SSH, feature 12; HTTP, feature 13), compartidos por alta, edicion y prueba:
 * los campos del formulario (camelCase) pasan a las claves de la entidad (snake_case). Como la password de la BD, los
 * secretos no se muestran en edicion y solo cambian si se tocan: password y passphrase SSH (isSshSecretsChanged) y la
 * URL del tunel HTTP, que lleva el token de acceso (isHttpUrlChanged).
 */
public final class TunnelFormPrimitives {

    private TunnelFormPrimitives() {
    }

    /**
     * Lo del formulario que es de los tuneles, ya tipado (lo guarda cada DTO).
     */
    public static Map<String, Object> getFormValues(Map<String, Object> primitives) {
        Object sshPassword = primitives.get("sshPassword");
        Object sshPassphrase = primitives.get("sshPassphrase");
        return Primitives.of(
            "sshEnabled", getBool(primitives.get("sshEnabled")),
            "sshHost", getTrimmedString(primitives.get("sshHost")),
            "sshPort", getInt(primitives.get("sshPort")),
            "sshUser", getTrimmedString(primitives.get("sshUser")),
            "sshPassword", sshPassword == null ? "" : String.valueOf(sshPassword),
            "sshPrivateKeyPath", getTrimmedString(primitives.get("sshPrivateKeyPath")),
            "sshPassphrase", sshPassphrase == null ? "" : String.valueOf(sshPassphrase),
            // en un alta no hay secretos guardados: lo escrito es lo que vale
            "isSshSecretsChanged", !primitives.containsKey("isSshSecretsChanged") || getBool(primitives.get("isSshSecretsChanged")),
            "httpEnabled", getBool(primitives.get("httpEnabled")),
            "httpUrl", getTrimmedString(primitives.get("httpUrl")),
            "httpTimeoutS", getInt(primitives.get("httpTimeoutS")),
            "isHttpUrlChanged", !primitives.containsKey("isHttpUrlChanged") || getBool(primitives.get("isHttpUrlChanged"))
        );
    }

    /**
     * Las claves ssh_* y http_* de la entidad. savedConnectionEntity: la conexion guardada (edicion, prueba de una
     * guardada) o null (alta); de ella salen los secretos si no se han tocado.
     */
    public static Map<String, Object> getEntityPrimitives(Map<String, Object> formValues, ConnectionEntity savedConnectionEntity) {
        boolean isKeepingSavedSecrets = savedConnectionEntity != null && !getBool(formValues.get("isSshSecretsChanged"));
        boolean isKeepingSavedHttpUrl = savedConnectionEntity != null && !getBool(formValues.get("isHttpUrlChanged"));
        int sshPort = getInt(formValues.get("sshPort"));
        int httpTimeoutS = getInt(formValues.get("httpTimeoutS"));
        return Primitives.of(
            ConnectionsKeyEnum.SSH_ENABLED, getBool(formValues.get("sshEnabled")),
            ConnectionsKeyEnum.SSH_HOST, getTrimmedString(formValues.get("sshHost")),
            ConnectionsKeyEnum.SSH_PORT, sshPort == 0 ? ConnectionEntity.DEFAULT_SSH_PORT : sshPort,
            ConnectionsKeyEnum.SSH_USER, getTrimmedString(formValues.get("sshUser")),
            ConnectionsKeyEnum.SSH_PASSWORD, isKeepingSavedSecrets ? savedConnectionEntity.getSshPassword() : formValues.get("sshPassword"),
            ConnectionsKeyEnum.SSH_PRIVATE_KEY_PATH, getTrimmedString(formValues.get("sshPrivateKeyPath")),
            ConnectionsKeyEnum.SSH_PASSPHRASE, isKeepingSavedSecrets ? savedConnectionEntity.getSshPassphrase() : formValues.get("sshPassphrase"),
            ConnectionsKeyEnum.HTTP_ENABLED, getBool(formValues.get("httpEnabled")),
            ConnectionsKeyEnum.HTTP_URL, isKeepingSavedHttpUrl ? savedConnectionEntity.getHttpUrl() : getTrimmedString(formValues.get("httpUrl")),
            ConnectionsKeyEnum.HTTP_TIMEOUT_S, httpTimeoutS == 0 ? ConnectionEntity.DEFAULT_HTTP_TIMEOUT_S : httpTimeoutS
        );
    }
}
