package modules.connections.application;

import java.util.Map;

import modules.shared.application.Primitives;
import static modules.shared.application.Primitives.getBool;
import static modules.shared.application.Primitives.getInt;
import static modules.shared.application.Primitives.getTrimmedString;

import modules.connections.domain.enums.ConnectionsConfigEnum;
import modules.connections.domain.enums.ConnectionsKeyEnum;
import modules.connections.domain.enums.TunnelFormKeyEnum;
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

    public static TunnelFormPrimitives getInstance() {
        return new TunnelFormPrimitives();
    }

    /**
     * Lo del formulario que es de los tuneles, ya tipado (lo guarda cada DTO).
     */
    public Map<String, Object> getFormValues(Map<String, Object> primitives) {
        Object sshPassword = primitives.get(TunnelFormKeyEnum.SSH_PASSWORD);
        Object sshPassphrase = primitives.get(TunnelFormKeyEnum.SSH_PASSPHRASE);
        return Primitives.of(
            TunnelFormKeyEnum.SSH_ENABLED, getBool(primitives.get(TunnelFormKeyEnum.SSH_ENABLED)),
            TunnelFormKeyEnum.SSH_HOST, getTrimmedString(primitives.get(TunnelFormKeyEnum.SSH_HOST)),
            TunnelFormKeyEnum.SSH_PORT, getInt(primitives.get(TunnelFormKeyEnum.SSH_PORT)),
            TunnelFormKeyEnum.SSH_USER, getTrimmedString(primitives.get(TunnelFormKeyEnum.SSH_USER)),
            TunnelFormKeyEnum.SSH_PASSWORD, sshPassword == null ? "" : String.valueOf(sshPassword),
            TunnelFormKeyEnum.SSH_PRIVATE_KEY_PATH, getTrimmedString(primitives.get(TunnelFormKeyEnum.SSH_PRIVATE_KEY_PATH)),
            TunnelFormKeyEnum.SSH_PASSPHRASE, sshPassphrase == null ? "" : String.valueOf(sshPassphrase),
            // en un alta no hay secretos guardados: lo escrito es lo que vale
            TunnelFormKeyEnum.IS_SSH_SECRETS_CHANGED, !primitives.containsKey(TunnelFormKeyEnum.IS_SSH_SECRETS_CHANGED) || getBool(primitives.get(TunnelFormKeyEnum.IS_SSH_SECRETS_CHANGED)),
            TunnelFormKeyEnum.HTTP_ENABLED, getBool(primitives.get(TunnelFormKeyEnum.HTTP_ENABLED)),
            TunnelFormKeyEnum.HTTP_URL, getTrimmedString(primitives.get(TunnelFormKeyEnum.HTTP_URL)),
            TunnelFormKeyEnum.HTTP_TIMEOUT_S, getInt(primitives.get(TunnelFormKeyEnum.HTTP_TIMEOUT_S)),
            TunnelFormKeyEnum.IS_HTTP_URL_CHANGED, !primitives.containsKey(TunnelFormKeyEnum.IS_HTTP_URL_CHANGED) || getBool(primitives.get(TunnelFormKeyEnum.IS_HTTP_URL_CHANGED))
        );
    }

    /**
     * Las claves ssh_* y http_* de la entidad. savedConnectionEntity: la conexion guardada (edicion, prueba de una
     * guardada) o null (alta); de ella salen los secretos si no se han tocado.
     */
    public Map<String, Object> getEntityPrimitives(Map<String, Object> formValues, ConnectionEntity savedConnectionEntity) {
        boolean isKeepingSavedSecrets = savedConnectionEntity != null && !getBool(formValues.get(TunnelFormKeyEnum.IS_SSH_SECRETS_CHANGED));
        boolean isKeepingSavedHttpUrl = savedConnectionEntity != null && !getBool(formValues.get(TunnelFormKeyEnum.IS_HTTP_URL_CHANGED));
        int sshPort = getInt(formValues.get(TunnelFormKeyEnum.SSH_PORT));
        int httpTimeoutS = getInt(formValues.get(TunnelFormKeyEnum.HTTP_TIMEOUT_S));
        return Primitives.of(
            ConnectionsKeyEnum.SSH_ENABLED, getBool(formValues.get(TunnelFormKeyEnum.SSH_ENABLED)),
            ConnectionsKeyEnum.SSH_HOST, getTrimmedString(formValues.get(TunnelFormKeyEnum.SSH_HOST)),
            ConnectionsKeyEnum.SSH_PORT, sshPort == 0 ? ConnectionsConfigEnum.DEFAULT_SSH_PORT : sshPort,
            ConnectionsKeyEnum.SSH_USER, getTrimmedString(formValues.get(TunnelFormKeyEnum.SSH_USER)),
            ConnectionsKeyEnum.SSH_PASSWORD, isKeepingSavedSecrets ? savedConnectionEntity.getSshPassword() : formValues.get(TunnelFormKeyEnum.SSH_PASSWORD),
            ConnectionsKeyEnum.SSH_PRIVATE_KEY_PATH, getTrimmedString(formValues.get(TunnelFormKeyEnum.SSH_PRIVATE_KEY_PATH)),
            ConnectionsKeyEnum.SSH_PASSPHRASE, isKeepingSavedSecrets ? savedConnectionEntity.getSshPassphrase() : formValues.get(TunnelFormKeyEnum.SSH_PASSPHRASE),
            ConnectionsKeyEnum.HTTP_ENABLED, getBool(formValues.get(TunnelFormKeyEnum.HTTP_ENABLED)),
            ConnectionsKeyEnum.HTTP_URL, isKeepingSavedHttpUrl ? savedConnectionEntity.getHttpUrl() : getTrimmedString(formValues.get(TunnelFormKeyEnum.HTTP_URL)),
            ConnectionsKeyEnum.HTTP_TIMEOUT_S, httpTimeoutS == 0 ? ConnectionsConfigEnum.DEFAULT_HTTP_TIMEOUT_S : httpTimeoutS
        );
    }
}
