package modules.connections.application.updateconnection;

import java.util.List;
import java.util.Map;

import modules.shared.domain.enums.SharedMessageEnum;
import modules.shared.application.Primitives;
import modules.shared.infrastructure.components.date.DateTimer;
import modules.shared.infrastructure.traits.ErrorStackTrait;
import modules.shared.infrastructure.exceptions.FieldsException;
import static modules.shared.application.Primitives.getBool;

import static core.config.Global.__;

import modules.connections.domain.enums.ConnectionsKeyEnum;
import modules.connections.domain.enums.ConnectionsMessageEnum;
import modules.connections.domain.enums.DbEngineEnum;
import modules.connections.domain.entities.ConnectionEntity;
import modules.connections.domain.services.ConnectionFieldsValidator;
import modules.connections.application.TunnelFormPrimitives;
import modules.connections.infrastructure.repositories.ConnectionsReaderSqliteRepository;
import modules.connections.infrastructure.repositories.ConnectionsWriterSqliteRepository;
import modules.connections.infrastructure.repositories.SqliteFilesWriterDiskRepository;
import modules.connections.domain.exceptions.ConnectionsException;

/**
 * Sirve tambien para renombrar: el nombre es un campo mas. La password solo cambia si isPasswordChanged.
 */
public final class UpdateConnectionService {

    private final ConnectionsReaderSqliteRepository connectionsReaderSqliteRepository;
    private final ConnectionsWriterSqliteRepository connectionsWriterSqliteRepository;

    private ConnectionEntity connectionEntity;

    private UpdateConnectionService() {
        this.connectionsReaderSqliteRepository = ConnectionsReaderSqliteRepository.getInstance();
        this.connectionsWriterSqliteRepository = ConnectionsWriterSqliteRepository.getInstance();
    }

    public static UpdateConnectionService getInstance() {
        return new UpdateConnectionService();
    }

    /**
     * @throws FieldsException
     * @throws ConnectionsException
     */
    public UpdateConnectionResultDto invoke(UpdateConnectionDto updateConnectionDto) {
        failIfWrongInput(updateConnectionDto);

        ConnectionEntity savedConnectionEntity = connectionsReaderSqliteRepository.getConnectionByUuid(updateConnectionDto.getConnectionUuid());
        if (savedConnectionEntity == null) {
            throw ConnectionsException.notFoundCustom(__(ConnectionsMessageEnum.CONNECTION_X_NOT_FOUND, updateConnectionDto.getConnectionUuid()));
        }

        String dbEngine = updateConnectionDto.getDbEngine();
        int port = updateConnectionDto.getPort();
        connectionEntity = ConnectionEntity.fromPrimitives(Primitives.merge(savedConnectionEntity.toPrimitivesWithPassword(), Primitives.of(
            ConnectionsKeyEnum.CONNECTION_NAME, updateConnectionDto.getConnectionName(),
            ConnectionsKeyEnum.DB_ENGINE, dbEngine.isEmpty() ? DbEngineEnum.DEFAULT : dbEngine,
            ConnectionsKeyEnum.HOST, updateConnectionDto.getHost(),
            ConnectionsKeyEnum.PORT, port == 0 ? DbEngineEnum.getDefaultPort(dbEngine.isEmpty() ? DbEngineEnum.DEFAULT : dbEngine) : port,
            ConnectionsKeyEnum.USER_NAME, updateConnectionDto.getUserName(),
            ConnectionsKeyEnum.USER_PASSWORD, updateConnectionDto.isPasswordChanged()
                ? updateConnectionDto.getUserPassword()
                : savedConnectionEntity.getUserPassword(),
            ConnectionsKeyEnum.DATABASE_NAME, updateConnectionDto.getDatabaseName(),
            ConnectionsKeyEnum.COLOR_HEX, updateConnectionDto.getColorHex(),
            ConnectionsKeyEnum.IS_PRODUCTION, updateConnectionDto.isProduction(),
            ConnectionsKeyEnum.UPDATED_AT, DateTimer.getInstance().getNowDt()
        ), TunnelFormPrimitives.getEntityPrimitives(updateConnectionDto.getTunnelForm(), savedConnectionEntity)));

        failIfWrongFields();
        failIfNameExists();
        loadSqliteFileIfFolder();

        updateConnectionAndChangedSecrets(updateConnectionDto);
        ConnectionEntity updatedConnectionEntity = connectionsReaderSqliteRepository.getConnectionById(connectionEntity.getId());
        return UpdateConnectionResultDto.fromPrimitives(Primitives.of(
            "connection", updatedConnectionEntity.toPrimitives()
        ));
    }

    /**
     * Los campos y solo los secretos que se tocaron en el dialogo, en una transaccion: uno guardado que no se pudo
     * descifrar (BD de otro PC) llega vacio y, si no se reescribe, se queda cifrado como estaba en vez de perderse al
     * editar otro campo.
     */
    private void updateConnectionAndChangedSecrets(UpdateConnectionDto updateConnectionDto) {
        Map<String, Object> tunnelForm = updateConnectionDto.getTunnelForm();
        connectionsWriterSqliteRepository.updateConnectionAndChangedSecretsById(
            connectionEntity,
            updateConnectionDto.isPasswordChanged(),
            getBool(tunnelForm.get("isSshSecretsChanged")),
            getBool(tunnelForm.get("isHttpUrlChanged"))
        );
    }

    /**
     * SQLite local con una carpeta en Fichero: se crea dentro un .sqlite vacio y la conexion apunta a el (asi se puede
     * empezar a crear tablas sin tener el fichero).
     */
    private void loadSqliteFileIfFolder() {
        if (!DbEngineEnum.SQLITE.equals(connectionEntity.getDbEngine()) || connectionEntity.isHttpEnabled()) return;
        String sqlitePath = SqliteFilesWriterDiskRepository.getInstance().createEmptyFileIfFolder(
            connectionEntity.getDatabaseName(), connectionEntity.getConnectionName()
        );
        if (sqlitePath.equals(connectionEntity.getDatabaseName())) return;
        connectionEntity = ConnectionEntity.fromPrimitives(Primitives.merge(
            connectionEntity.toPrimitivesWithPassword(), Primitives.of(ConnectionsKeyEnum.DATABASE_NAME, sqlitePath)
        ));
    }

    /**
     * @throws FieldsException
     */
    private void failIfWrongFields() {
        List<String> errors = ConnectionFieldsValidator.getInstance().getErrors(connectionEntity);
        if (!errors.isEmpty()) {
            throw FieldsException.badRequestCustom(
                ErrorStackTrait.getInstance().getErrorStackMessage(__(SharedMessageEnum.FIELDS_VALIDATION_ERRORS), errors),
                errors
            );
        }
    }

    /**
     * @throws ConnectionsException
     */
    private void failIfNameExists() {
        String connectionName = connectionEntity.getConnectionName();
        if (connectionsReaderSqliteRepository.doesConnectionNameExist(connectionName, connectionEntity.getConnectionUuid())) {
            throw ConnectionsException.conflictCustom(__(ConnectionsMessageEnum.CONNECTION_NAME_X_ALREADY_EXISTS, connectionName));
        }
    }

    /**
     * @throws ConnectionsException
     */
    private void failIfWrongInput(UpdateConnectionDto updateConnectionDto) {
        if (updateConnectionDto.getConnectionUuid().isEmpty()) {
            throw ConnectionsException.badRequestCustom(__(ConnectionsMessageEnum.FIELD_IS_REQUIRED_X, "connectionUuid"));
        }
    }
}
