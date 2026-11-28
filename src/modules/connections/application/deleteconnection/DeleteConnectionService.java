package modules.connections.application.deleteconnection;

import modules.shared.application.Primitives;

import static core.config.Global.__;

import modules.connections.domain.enums.ConnectionsMessageEnum;
import modules.connections.domain.entities.ConnectionEntity;
import modules.connections.infrastructure.repositories.ConnectionsReaderSqliteRepository;
import modules.connections.infrastructure.repositories.ConnectionsWriterSqliteRepository;
import modules.connections.domain.exceptions.ConnectionsException;

/**
 * La confirmacion ("seguro que...") la pide la UI antes de llamar.
 */
public final class DeleteConnectionService {

    private final ConnectionsReaderSqliteRepository connectionsReaderSqliteRepository;
    private final ConnectionsWriterSqliteRepository connectionsWriterSqliteRepository;

    private DeleteConnectionService() {
        this.connectionsReaderSqliteRepository = ConnectionsReaderSqliteRepository.getInstance();
        this.connectionsWriterSqliteRepository = ConnectionsWriterSqliteRepository.getInstance();
    }

    public static DeleteConnectionService getInstance() {
        return new DeleteConnectionService();
    }

    /**
     * @throws ConnectionsException
     */
    public DeleteConnectionResultDto invoke(DeleteConnectionDto deleteConnectionDto) {
        ConnectionEntity connectionEntity = connectionsReaderSqliteRepository.getConnectionByUuid(deleteConnectionDto.getConnectionUuid());
        if (connectionEntity == null) {
            throw ConnectionsException.notFoundCustom(__(ConnectionsMessageEnum.CONNECTION_X_NOT_FOUND, deleteConnectionDto.getConnectionUuid()));
        }

        connectionsWriterSqliteRepository.deleteConnectionById(connectionEntity);
        return DeleteConnectionResultDto.fromPrimitives(Primitives.of(
            "connectionUuid", connectionEntity.getConnectionUuid(),
            "connectionName", connectionEntity.getConnectionName()
        ));
    }
}
