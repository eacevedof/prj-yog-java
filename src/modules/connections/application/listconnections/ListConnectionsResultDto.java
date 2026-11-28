package modules.connections.application.listconnections;

import java.util.List;
import java.util.Map;

public final class ListConnectionsResultDto {

    private final List<Map<String, Object>> connections;

    @SuppressWarnings("unchecked")
    private ListConnectionsResultDto(Map<String, Object> primitives) {
        this.connections = List.copyOf((List<Map<String, Object>>) primitives.get("connections"));
    }

    public static ListConnectionsResultDto fromPrimitives(Map<String, Object> primitives) {
        return new ListConnectionsResultDto(primitives);
    }

    public List<Map<String, Object>> getConnections() {
        return connections;
    }
}
