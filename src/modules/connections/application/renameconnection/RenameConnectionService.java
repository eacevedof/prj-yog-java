package modules.connections.application.renameconnection;

import java.util.List;

import modules.shared.domain.enums.SharedMessageEnum;
import modules.shared.domain.services.ErrorStackFormatter;
import modules.shared.application.Primitives;
import modules.shared.infrastructure.components.date.DateTimer;
import modules.shared.infrastructure.exceptions.FieldsException;

import static core.config.Global.__;

import modules.connections.domain.enums.ConnectionsKeyEnum;
import modules.connections.domain.enums.ConnectionsMessageEnum;
import modules.connections.domain.entities.ConnectionEntity;
import modules.connections.domain.services.ConnectionFieldsValidator;
import modules.connections.infrastructure.repositories.ConnectionsReaderSqliteRepository;
import modules.connections.infrastructure.repositories.ConnectionsWriterSqliteRepository;
import modules.connections.domain.exceptions.ConnectionsException;

/**
 * "Renombrar" del menu de la conexion en el panel: solo cambia el nombre (el resto, tal cual esta guardado).
 */
public final class RenameConnectionService {

    private final ConnectionsReaderSqliteRepository connectionsReaderSqliteRepository;
    private final ConnectionsWriterSqliteRepository connectionsWriterSqliteRepository;

    private RenameConnectionService() {
        this.connectionsReaderSqliteRepository = ConnectionsReaderSqliteRepository.getInstance();
        this.connectionsWriterSqliteRepository = ConnectionsWriterSqliteRepository.getInstance();
    }

    public static RenameConnectionService getInstance() {
        return new RenameConnectionService();
    }

    /**
     * @throws FieldsException
     * @throws ConnectionsException
     */
    public RenameConnectionResultDto invoke(RenameConnectionDto renameConnectionDto) {
        failIfWrongInput(renameConnectionDto);

        ConnectionEntity savedConnectionEntity = connectionsReaderSqliteRepository.getConnectionByUuid(renameConnectionDto.getConnectionUuid());
        if (savedConnectionEntity == null) {
            throw ConnectionsException.notFoundCustom(__(ConnectionsMessageEnum.CONNECTION_X_NOT_FOUND, renameConnectionDto.getConnectionUuid()));
        }
        ConnectionEntity connectionEntity = ConnectionEntity.fromPrimitives(Primitives.merge(savedConnectionEntity.toPrimitivesWithPassword(), Primitives.of(
            ConnectionsKeyEnum.CONNECTION_NAME, renameConnectionDto.getConnectionName(),
            ConnectionsKeyEnum.UPDATED_AT, DateTimer.getInstance().getNowDt()
        )));
        failIfWrongFields(connectionEntity);
        failIfNameExists(connectionEntity);

        connectionsWriterSqliteRepository.updateConnectionById(connectionEntity);
        ConnectionEntity renamedConnectionEntity = connectionsReaderSqliteRepository.getConnectionById(connectionEntity.getId());
        return RenameConnectionResultDto.fromPrimitives(Primitives.of("connection", renamedConnectionEntity.toPrimitives()));
    }

    /**
     * @throws FieldsException
     */
    private void failIfWrongFields(ConnectionEntity connectionEntity) {
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
    private void failIfNameExists(ConnectionEntity connectionEntity) {
        String connectionName = connectionEntity.getConnectionName();
        if (connectionsReaderSqliteRepository.doesConnectionNameExist(connectionName, connectionEntity.getConnectionUuid())) {
            throw ConnectionsException.conflictCustom(__(ConnectionsMessageEnum.CONNECTION_NAME_X_ALREADY_EXISTS, connectionName));
        }
    }

    /**
     * @throws ConnectionsException
     */
    private void failIfWrongInput(RenameConnectionDto renameConnectionDto) {
        if (renameConnectionDto.getConnectionUuid().isEmpty()) {
            throw ConnectionsException.badRequestCustom(__(ConnectionsMessageEnum.FIELD_IS_REQUIRED_X, "connectionUuid"));
        }
    }
}
