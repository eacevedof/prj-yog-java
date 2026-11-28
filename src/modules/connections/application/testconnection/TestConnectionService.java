package modules.connections.application.testconnection;

import java.io.File;
import java.sql.SQLException;
import java.util.List;

import modules.shared.domain.enums.SharedMessageEnum;
import modules.shared.application.Primitives;
import modules.shared.infrastructure.traits.ErrorStackTrait;
import modules.shared.infrastructure.exceptions.FieldsException;

import static core.config.Global.__;

import modules.connections.domain.enums.ConnectionsKeyEnum;
import modules.connections.domain.enums.ConnectionsMessageEnum;
import modules.connections.domain.enums.DbEngineEnum;
import modules.connections.domain.entities.ConnectionEntity;
import modules.connections.domain.services.ConnectionFieldsValidator;
import modules.connections.application.TunnelFormPrimitives;
import modules.connections.infrastructure.repositories.ConnectionsReaderSqliteRepository;
import modules.connections.infrastructure.repositories.TargetDbReaderJdbcRepository;
import modules.connections.domain.exceptions.ConnectionsException;

/**
 * Prueba los datos del formulario contra el servidor, esten guardados o no. Si la conexion ya existe y la password
 * no se ha tocado, usa la guardada. Hace red: la UI lo llama fuera del hilo de JavaFX.
 */
public final class TestConnectionService {

    private ConnectionEntity connectionEntity;

    private TestConnectionService() {
    }

    public static TestConnectionService getInstance() {
        return new TestConnectionService();
    }

    /**
     * @throws FieldsException
     * @throws SQLException si la BD falla: sube tal cual hasta el controller
     * @throws ConnectionsException
     */
    public TestConnectionResultDto invoke(TestConnectionDto testConnectionDto) throws SQLException {
        String dbEngine = testConnectionDto.getDbEngine();
        int port = testConnectionDto.getPort();
        connectionEntity = ConnectionEntity.fromPrimitives(Primitives.merge(Primitives.of(
            ConnectionsKeyEnum.CONNECTION_NAME, testConnectionDto.getConnectionName(),
            ConnectionsKeyEnum.DB_ENGINE, dbEngine.isEmpty() ? DbEngineEnum.DEFAULT : dbEngine,
            ConnectionsKeyEnum.HOST, testConnectionDto.getHost(),
            ConnectionsKeyEnum.PORT, port == 0 ? DbEngineEnum.getDefaultPort(dbEngine.isEmpty() ? DbEngineEnum.DEFAULT : dbEngine) : port,
            ConnectionsKeyEnum.USER_NAME, testConnectionDto.getUserName(),
            ConnectionsKeyEnum.USER_PASSWORD, getPassword(testConnectionDto),
            ConnectionsKeyEnum.DATABASE_NAME, testConnectionDto.getDatabaseName(),
            ConnectionsKeyEnum.COLOR_HEX, testConnectionDto.getColorHex()
        ), TunnelFormPrimitives.getEntityPrimitives(testConnectionDto.getTunnelForm(), getSavedConnection(testConnectionDto))));

        failIfWrongFields();

        // SQLite local con una carpeta: no hay fichero que probar todavia; se crea al guardar (CreateConnectionService)
        if (isLocalSqliteFolder()) {
            return TestConnectionResultDto.fromPrimitives(Primitives.of(
                "serverVersion", "",
                "message", __(ConnectionsMessageEnum.SQLITE_FOLDER_X_NEW_FILE_ON_SAVE, connectionEntity.getDatabaseName())
            ));
        }
        String connectionName = connectionEntity.getConnectionName();
        String serverVersion = TargetDbReaderJdbcRepository.getInstance().getServerVersion(connectionEntity);
        return TestConnectionResultDto.fromPrimitives(Primitives.of(
            "serverVersion", serverVersion,
            "message", __(ConnectionsMessageEnum.CONNECTION_X_OK_SERVER_X, connectionName, serverVersion)
        ));
    }

    private boolean isLocalSqliteFolder() {
        if (!DbEngineEnum.SQLITE.equals(connectionEntity.getDbEngine()) || connectionEntity.isHttpEnabled()) return false;
        return new File(connectionEntity.getDatabaseName()).isDirectory();
    }

    /**
     * La conexion guardada que se esta editando (de ella salen los secretos SSH no tocados); null en un alta.
     */
    private ConnectionEntity getSavedConnection(TestConnectionDto testConnectionDto) {
        if (testConnectionDto.getConnectionUuid().isEmpty()) return null;
        return ConnectionsReaderSqliteRepository.getInstance().getConnectionByUuid(testConnectionDto.getConnectionUuid());
    }

    private String getPassword(TestConnectionDto testConnectionDto) {
        if (testConnectionDto.isPasswordChanged() || testConnectionDto.getConnectionUuid().isEmpty()) {
            return testConnectionDto.getUserPassword();
        }
        ConnectionEntity savedConnectionEntity = ConnectionsReaderSqliteRepository.getInstance()
            .getConnectionByUuid(testConnectionDto.getConnectionUuid());
        return savedConnectionEntity == null ? testConnectionDto.getUserPassword() : savedConnectionEntity.getUserPassword();
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
}
