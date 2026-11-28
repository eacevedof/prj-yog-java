package modules.connections.application.createconnection;

import java.util.List;

import modules.shared.domain.enums.SharedMessageEnum;
import modules.shared.domain.services.ErrorStackFormatter;
import modules.shared.application.Primitives;
import modules.shared.infrastructure.components.date.DateTimer;
import modules.shared.infrastructure.components.uuid.UuidGenerator;
import modules.shared.infrastructure.exceptions.FieldsException;

import static core.config.Global.__;

import modules.connections.domain.enums.ConnectionsConfigEnum;
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
 * db_engine vacio -> mysql; puerto 0 (vacio) -> el del motor.
 */
public final class CreateConnectionService {

    private final ConnectionsReaderSqliteRepository connectionsReaderSqliteRepository;
    private final ConnectionsWriterSqliteRepository connectionsWriterSqliteRepository;

    private ConnectionEntity connectionEntity;

    private CreateConnectionService() {
        this.connectionsReaderSqliteRepository = ConnectionsReaderSqliteRepository.getInstance();
        this.connectionsWriterSqliteRepository = ConnectionsWriterSqliteRepository.getInstance();
    }

    public static CreateConnectionService getInstance() {
        return new CreateConnectionService();
    }

    /**
     * @throws FieldsException
     * @throws ConnectionsException
     */
    public CreateConnectionResultDto invoke(CreateConnectionDto createConnectionDto) {
        String dbEngine = createConnectionDto.getDbEngine();
        int port = createConnectionDto.getPort();
        connectionEntity = ConnectionEntity.fromPrimitives(Primitives.merge(Primitives.of(
            ConnectionsKeyEnum.CONNECTION_UUID, UuidGenerator.getInstance().getUniqueIdWithPrefix(ConnectionsConfigEnum.CONNECTION_UUID_PREFIX),
            ConnectionsKeyEnum.CONNECTION_NAME, createConnectionDto.getConnectionName(),
            ConnectionsKeyEnum.DB_ENGINE, dbEngine.isEmpty() ? DbEngineEnum.DEFAULT : dbEngine,
            ConnectionsKeyEnum.HOST, createConnectionDto.getHost(),
            ConnectionsKeyEnum.PORT, port == 0 ? DbEngineEnum.getDefaultPort(dbEngine.isEmpty() ? DbEngineEnum.DEFAULT : dbEngine) : port,
            ConnectionsKeyEnum.USER_NAME, createConnectionDto.getUserName(),
            ConnectionsKeyEnum.USER_PASSWORD, createConnectionDto.getUserPassword(),
            ConnectionsKeyEnum.DATABASE_NAME, createConnectionDto.getDatabaseName(),
            ConnectionsKeyEnum.COLOR_HEX, createConnectionDto.getColorHex(),
            ConnectionsKeyEnum.IS_PRODUCTION, createConnectionDto.isProduction(),
            ConnectionsKeyEnum.CREATED_AT, DateTimer.getInstance().getNowDt()
        ), TunnelFormPrimitives.getInstance().getEntityPrimitives(createConnectionDto.getTunnelForm(), null)));

        failIfWrongFields();
        failIfNameExists();
        loadSqliteFileIfFolder();

        int connectionId = connectionsWriterSqliteRepository.insertConnection(connectionEntity);
        ConnectionEntity createdConnectionEntity = connectionsReaderSqliteRepository.getConnectionById(connectionId);
        return CreateConnectionResultDto.fromPrimitives(Primitives.of(
            "connection", createdConnectionEntity.toPrimitives(),
            "connectionUuid", createdConnectionEntity.getConnectionUuid()
        ));
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
                ErrorStackFormatter.getInstance().getErrorStackMessage(__(SharedMessageEnum.FIELDS_VALIDATION_ERRORS), errors),
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
}
