package modules.connections.application.listconnections;

import java.util.Map;

/**
 * Sin datos de entrada: se listan todas las conexiones guardadas.
 */
public final class ListConnectionsDto {

    private ListConnectionsDto(Map<String, Object> primitives) {
    }

    public static ListConnectionsDto fromPrimitives(Map<String, Object> primitives) {
        return new ListConnectionsDto(primitives);
    }
}
