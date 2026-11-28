package modules.connections.application.cloneconnection;

import modules.shared.application.Primitives;
import modules.shared.infrastructure.components.date.DateTimer;
import modules.shared.infrastructure.components.uuid.UuidGenerator;

import static core.config.Global.__;

import modules.connections.domain.enums.ConnectionsKeyEnum;
import modules.connections.domain.enums.ConnectionsMessageEnum;
import modules.connections.domain.entities.ConnectionEntity;
import modules.connections.infrastructure.repositories.ConnectionsReaderSqliteRepository;
import modules.connections.infrastructure.repositories.ConnectionsWriterSqliteRepository;
import modules.connections.domain.exceptions.ConnectionsException;

/**
 * Copia todo (password incluida) con uuid nuevo y nombre "X (copy)", "X (copy) (copy)"... hasta que no choque. El
 * clon nunca hereda la marca de produccion: hay que ponerla a mano, a proposito.
 */
public final class CloneConnectionService {

    private final ConnectionsReaderSqliteRepository connectionsReaderSqliteRepository;
    private final ConnectionsWriterSqliteRepository connectionsWriterSqliteRepository;

    private CloneConnectionService() {
        this.connectionsReaderSqliteRepository = ConnectionsReaderSqliteRepository.getInstance();
        this.connectionsWriterSqliteRepository = ConnectionsWriterSqliteRepository.getInstance();
    }

    public static CloneConnectionService getInstance() {
        return new CloneConnectionService();
    }

    /**
     * @throws ConnectionsException
     */
    public CloneConnectionResultDto invoke(CloneConnectionDto cloneConnectionDto) {
        ConnectionEntity sourceConnectionEntity = connectionsReaderSqliteRepository.getConnectionByUuid(cloneConnectionDto.getConnectionUuid());
        if (sourceConnectionEntity == null) {
            throw ConnectionsException.notFoundCustom(__(ConnectionsMessageEnum.CONNECTION_X_NOT_FOUND, cloneConnectionDto.getConnectionUuid()));
        }

        String newConnectionUuid = UuidGenerator.getInstance().getUniqueIdWithPrefix(ConnectionEntity.CONNECTION_UUID_PREFIX);
        ConnectionEntity clonedConnectionEntity = ConnectionEntity.fromPrimitives(Primitives.merge(sourceConnectionEntity.toPrimitivesWithPassword(), Primitives.of(
            ConnectionsKeyEnum.ID, null,
            ConnectionsKeyEnum.CONNECTION_UUID, newConnectionUuid,
            ConnectionsKeyEnum.CONNECTION_NAME, getFreeCopyName(sourceConnectionEntity.getConnectionName(), newConnectionUuid),
            ConnectionsKeyEnum.IS_PRODUCTION, false,
            ConnectionsKeyEnum.CREATED_AT, DateTimer.getInstance().getNowDt()
        )));

        int connectionId = connectionsWriterSqliteRepository.insertConnection(clonedConnectionEntity);
        return CloneConnectionResultDto.fromPrimitives(Primitives.of(
            "connection", connectionsReaderSqliteRepository.getConnectionById(connectionId).toPrimitives()
        ));
    }

    private String getFreeCopyName(String connectionName, String newConnectionUuid) {
        String copyName = __(ConnectionsMessageEnum.COPY_OF_X, connectionName);
        while (connectionsReaderSqliteRepository.doesConnectionNameExist(copyName, newConnectionUuid)) {
            copyName = __(ConnectionsMessageEnum.COPY_OF_X, copyName);
        }
        return copyName;
    }
}
