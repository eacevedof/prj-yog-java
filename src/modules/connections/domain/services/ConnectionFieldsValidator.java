package modules.connections.domain.services;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import static core.config.Global.__;

import modules.connections.domain.enums.ConnectionsConfigEnum;
import modules.connections.domain.enums.ConnectionsKeyEnum;
import modules.connections.domain.enums.ConnectionsMessageEnum;
import modules.connections.domain.enums.DbEngineEnum;
import modules.connections.domain.entities.ConnectionEntity;

/**
 * Reglas de escritura de una conexion (alta, edicion, clonado). Recibe la entidad YA con los defaults aplicados.
 * Devuelve un error "campo: mensaje" por regla incumplida.
 */
public final class ConnectionFieldsValidator {

    private static final Pattern COLOR_HEX_PATTERN = Pattern.compile("^#[0-9a-fA-F]{6}$");
    private static final Pattern REDIS_DATABASE_PATTERN = Pattern.compile("^[0-9]{1,3}$");

    private ConnectionFieldsValidator() {
    }

    public static ConnectionFieldsValidator getInstance() {
        return new ConnectionFieldsValidator();
    }

    public List<String> getErrors(ConnectionEntity connectionEntity) {
        List<String> errors = new ArrayList<>();
        addRequiredWithMaxLengthError(errors, ConnectionsKeyEnum.CONNECTION_NAME, connectionEntity.getConnectionName(), ConnectionsConfigEnum.CONNECTION_NAME_MAX_LENGTH);

        if (!DbEngineEnum.VALUES.contains(connectionEntity.getDbEngine())) {
            errors.add(ConnectionsKeyEnum.DB_ENGINE + ": " + __(ConnectionsMessageEnum.ALLOWED_VALUES_ARE_X, String.join(", ", DbEngineEnum.VALUES)));
        }

        if (DbEngineEnum.SQLITE.equals(connectionEntity.getDbEngine())) {
            addSqliteErrors(errors, connectionEntity);
        }
        else if (DbEngineEnum.FILES.equals(connectionEntity.getDbEngine())) {
            addFilesErrors(errors, connectionEntity);
        }
        else {
            addServerErrors(errors, connectionEntity);
        }

        addSshTunnelErrors(errors, connectionEntity);
        addHttpTunnelErrors(errors, connectionEntity);

        String colorHex = connectionEntity.getColorHex();
        if (!colorHex.isEmpty() && !COLOR_HEX_PATTERN.matcher(colorHex).matches()) {
            errors.add(ConnectionsKeyEnum.COLOR_HEX + ": " + __(ConnectionsMessageEnum.INVALID_COLOR_X, ConnectionsConfigEnum.COLOR_HEX_FORMAT));
        }
        return errors;
    }

    /**
     * Un servidor (MySQL, PostgreSQL, Redis...): host y puerto validos; usuario, salvo en Redis (sin ACL no hay). En
     * Redis la "BD" es su indice (0, 1...) y el tunel PHP no existe.
     */
    private void addServerErrors(List<String> errors, ConnectionEntity connectionEntity) {
        String dbEngine = connectionEntity.getDbEngine();
        addRequiredWithMaxLengthError(errors, ConnectionsKeyEnum.HOST, connectionEntity.getHost(), ConnectionsConfigEnum.HOST_MAX_LENGTH);
        if (!DbEngineEnum.isUserOptional(dbEngine)) {
            addRequiredWithMaxLengthError(errors, ConnectionsKeyEnum.USER_NAME, connectionEntity.getUserName(), ConnectionsConfigEnum.USER_NAME_MAX_LENGTH);
        }
        else if (connectionEntity.getUserName().length() > ConnectionsConfigEnum.USER_NAME_MAX_LENGTH) {
            errors.add(ConnectionsKeyEnum.USER_NAME + ": " + __(ConnectionsMessageEnum.MAX_LENGTH_IS_X, ConnectionsConfigEnum.USER_NAME_MAX_LENGTH));
        }
        int port = connectionEntity.getPort();
        if (port < ConnectionsConfigEnum.PORT_MIN || port > ConnectionsConfigEnum.PORT_MAX) {
            errors.add(ConnectionsKeyEnum.PORT + ": " + __(ConnectionsMessageEnum.VALUE_BETWEEN_X_AND_X, ConnectionsConfigEnum.PORT_MIN, ConnectionsConfigEnum.PORT_MAX));
        }
        String databaseName = connectionEntity.getDatabaseName();
        if (DbEngineEnum.REDIS.equals(dbEngine)) {
            if (!databaseName.isEmpty() && !REDIS_DATABASE_PATTERN.matcher(databaseName).matches()) {
                errors.add(ConnectionsKeyEnum.DATABASE_NAME + ": " + __(ConnectionsMessageEnum.VALUE_BETWEEN_X_AND_X, 0, DbEngineEnum.REDIS_MAX_DATABASE_INDEX));
            }
        }
        else if (databaseName.length() > ConnectionsConfigEnum.DATABASE_NAME_MAX_LENGTH) {
            errors.add(ConnectionsKeyEnum.DATABASE_NAME + ": " + __(ConnectionsMessageEnum.MAX_LENGTH_IS_X, ConnectionsConfigEnum.DATABASE_NAME_MAX_LENGTH));
        }
        if (DbEngineEnum.MONGODB.equals(dbEngine) && connectionEntity.getHost().startsWith(ConnectionsConfigEnum.MONGODB_URI_SCHEME)) {
            // la cadena de conexion se guarda en claro: sin credenciales (van en sus campos, cifrados) y sin tunel SSH
            if (connectionEntity.getHost().contains(ConnectionsConfigEnum.URI_CREDENTIALS_SEPARATOR)) {
                errors.add(ConnectionsKeyEnum.HOST + ": " + __(ConnectionsMessageEnum.MONGODB_URI_WITHOUT_CREDENTIALS));
            }
            if (connectionEntity.isSshEnabled()) {
                errors.add(ConnectionsKeyEnum.SSH_ENABLED + ": " + __(ConnectionsMessageEnum.MONGODB_URI_WITHOUT_SSH));
            }
            if (connectionEntity.isHttpEnabled()) {
                errors.add(ConnectionsKeyEnum.HTTP_ENABLED + ": " + __(ConnectionsMessageEnum.MONGODB_URI_WITHOUT_HTTP));
            }
        }
        if (connectionEntity.isHttpEnabled() && !DbEngineEnum.isHttpTunnelSupported(dbEngine)) {
            errors.add(ConnectionsKeyEnum.HTTP_ENABLED + ": " + __(ConnectionsMessageEnum.HTTP_TUNNEL_NOT_FOR_X, DbEngineEnum.LABELS.get(dbEngine)));
        }
    }

    /**
     * SQLite: no hay servidor. La "BD" es la ruta del fichero (o, por el tunel PHP, la clave de su lista de BDs) y es
     * obligatoria; host, puerto y usuario no se usan. Un tunel SSH no tiene sentido (no hay puerto al que llegar).
     */
    private void addSqliteErrors(List<String> errors, ConnectionEntity connectionEntity) {
        addRequiredWithMaxLengthError(errors, ConnectionsKeyEnum.DATABASE_NAME, connectionEntity.getDatabaseName(), ConnectionsConfigEnum.SQLITE_FILE_MAX_LENGTH);
        if (connectionEntity.isSshEnabled()) {
            errors.add(ConnectionsKeyEnum.SSH_ENABLED + ": " + __(ConnectionsMessageEnum.SSH_NOT_FOR_SQLITE));
        }
    }

    /**
     * Ficheros: la "BD" es la carpeta con los CSV y JSON, obligatoria; ni servidor ni tuneles (los ficheros son locales).
     */
    private void addFilesErrors(List<String> errors, ConnectionEntity connectionEntity) {
        addRequiredWithMaxLengthError(errors, ConnectionsKeyEnum.DATABASE_NAME, connectionEntity.getDatabaseName(), ConnectionsConfigEnum.SQLITE_FILE_MAX_LENGTH);
        // cada error con la clave de su tunel: el dialogo abre la pestaña por la clave (con ssh_ abria SSH para un error de HTTP)
        if (connectionEntity.isSshEnabled()) {
            errors.add(ConnectionsKeyEnum.SSH_ENABLED + ": " + __(ConnectionsMessageEnum.NO_TUNNEL_FOR_FILES));
        }
        if (connectionEntity.isHttpEnabled()) {
            errors.add(ConnectionsKeyEnum.HTTP_ENABLED + ": " + __(ConnectionsMessageEnum.NO_TUNNEL_FOR_FILES));
        }
    }

    /**
     * Feature 12: con tunel, host y usuario SSH obligatorios, puerto valido y una forma de entrar (password o clave).
     */
    private void addSshTunnelErrors(List<String> errors, ConnectionEntity connectionEntity) {
        if (!connectionEntity.isSshEnabled()) return;
        addRequiredWithMaxLengthError(errors, ConnectionsKeyEnum.SSH_HOST, connectionEntity.getSshHost(), ConnectionsConfigEnum.HOST_MAX_LENGTH);
        addRequiredWithMaxLengthError(errors, ConnectionsKeyEnum.SSH_USER, connectionEntity.getSshUser(), ConnectionsConfigEnum.USER_NAME_MAX_LENGTH);
        int sshPort = connectionEntity.getSshPort();
        if (sshPort < ConnectionsConfigEnum.PORT_MIN || sshPort > ConnectionsConfigEnum.PORT_MAX) {
            errors.add(ConnectionsKeyEnum.SSH_PORT + ": " + __(ConnectionsMessageEnum.VALUE_BETWEEN_X_AND_X, ConnectionsConfigEnum.PORT_MIN, ConnectionsConfigEnum.PORT_MAX));
        }
        if (connectionEntity.getSshPassword().isEmpty() && connectionEntity.getSshPrivateKeyPath().isEmpty()) {
            errors.add(ConnectionsKeyEnum.SSH_PASSWORD + ": " + __(ConnectionsMessageEnum.SSH_PASSWORD_OR_KEY_REQUIRED));
        }
        if (connectionEntity.getSshPrivateKeyPath().length() > ConnectionsConfigEnum.SSH_KEY_PATH_MAX_LENGTH) {
            errors.add(ConnectionsKeyEnum.SSH_PRIVATE_KEY_PATH + ": " + __(ConnectionsMessageEnum.MAX_LENGTH_IS_X, ConnectionsConfigEnum.SSH_KEY_PATH_MAX_LENGTH));
        }
    }

    /**
     * Feature 13: con tunel HTTP, URL http(s) obligatoria y timeout razonable. Un tunel u otro, no los dos.
     */
    private void addHttpTunnelErrors(List<String> errors, ConnectionEntity connectionEntity) {
        if (!connectionEntity.isHttpEnabled()) return;
        if (connectionEntity.isSshEnabled()) {
            errors.add(ConnectionsKeyEnum.HTTP_ENABLED + ": " + __(ConnectionsMessageEnum.ONLY_ONE_TUNNEL));
        }
        String httpUrl = connectionEntity.getHttpUrl();
        addRequiredWithMaxLengthError(errors, ConnectionsKeyEnum.HTTP_URL, httpUrl, ConnectionsConfigEnum.HTTP_URL_MAX_LENGTH);
        String lowerHttpUrl = httpUrl.toLowerCase(Locale.ROOT);
        if (!httpUrl.isEmpty() && !lowerHttpUrl.startsWith(ConnectionsConfigEnum.HTTP_URL_PREFIX) && !lowerHttpUrl.startsWith(ConnectionsConfigEnum.HTTPS_URL_PREFIX)) {
            errors.add(ConnectionsKeyEnum.HTTP_URL + ": " + __(ConnectionsMessageEnum.HTTP_URL_INVALID));
        }
        int httpTimeoutS = connectionEntity.getHttpTimeoutS();
        if (httpTimeoutS < ConnectionsConfigEnum.HTTP_TIMEOUT_MIN_S || httpTimeoutS > ConnectionsConfigEnum.HTTP_TIMEOUT_MAX_S) {
            errors.add(ConnectionsKeyEnum.HTTP_TIMEOUT_S + ": " + __(ConnectionsMessageEnum.VALUE_BETWEEN_X_AND_X, ConnectionsConfigEnum.HTTP_TIMEOUT_MIN_S, ConnectionsConfigEnum.HTTP_TIMEOUT_MAX_S));
        }
    }

    private void addRequiredWithMaxLengthError(List<String> errors, String field, String value, int maxLength) {
        if (value.isEmpty()) {
            errors.add(field + ": " + __(ConnectionsMessageEnum.FIELD_IS_REQUIRED));
            return;
        }
        if (value.codePointCount(0, value.length()) > maxLength) {
            errors.add(field + ": " + __(ConnectionsMessageEnum.MAX_LENGTH_IS_X, maxLength));
        }
    }
}
