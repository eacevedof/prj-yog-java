package modules.connections.application.listconnections;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import modules.shared.application.Primitives;

import modules.connections.domain.entities.ConnectionEntity;
import modules.connections.infrastructure.repositories.ConnectionsReaderSqliteRepository;

/**
 * Todas las conexiones guardadas, por nombre. Sin passwords.
 */
public final class ListConnectionsService {

    private ListConnectionsService() {
    }

    public static ListConnectionsService getInstance() {
        return new ListConnectionsService();
    }

    public ListConnectionsResultDto invoke(ListConnectionsDto listConnectionsDto) {
        List<Map<String, Object>> connections = new ArrayList<>();
        for (ConnectionEntity connectionEntity : ConnectionsReaderSqliteRepository.getInstance().getAllConnections()) {
            connections.add(connectionEntity.toPrimitives());
        }
        return ListConnectionsResultDto.fromPrimitives(Primitives.of("connections", connections));
    }
}
