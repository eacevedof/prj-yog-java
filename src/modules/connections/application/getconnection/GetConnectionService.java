package modules.connections.application.getconnection;

import modules.shared.application.Primitives;

import static core.config.Global.__;

import modules.connections.domain.enums.ConnectionsMessageEnum;
import modules.connections.domain.entities.ConnectionEntity;
import modules.connections.infrastructure.repositories.ConnectionsReaderSqliteRepository;
import modules.connections.domain.exceptions.ConnectionsException;

public final class GetConnectionService {

    private GetConnectionService() {
    }

    public static GetConnectionService getInstance() {
        return new GetConnectionService();
    }

    /**
     * @throws ConnectionsException
     */
    public GetConnectionResultDto invoke(GetConnectionDto getConnectionDto) {
        ConnectionEntity connectionEntity = ConnectionsReaderSqliteRepository.getInstance()
            .getConnectionByUuid(getConnectionDto.getConnectionUuid());
        if (connectionEntity == null) {
            throw ConnectionsException.notFoundCustom(__(ConnectionsMessageEnum.CONNECTION_X_NOT_FOUND, getConnectionDto.getConnectionUuid()));
        }
        return GetConnectionResultDto.fromPrimitives(Primitives.of("connection", connectionEntity.toPrimitives()));
    }
}
