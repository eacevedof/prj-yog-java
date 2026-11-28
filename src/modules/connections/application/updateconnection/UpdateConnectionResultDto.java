package modules.connections.application.updateconnection;

import java.util.Map;

public final class UpdateConnectionResultDto {

    private final Map<String, Object> connection;

    @SuppressWarnings("unchecked")
    private UpdateConnectionResultDto(Map<String, Object> primitives) {
        this.connection = (Map<String, Object>) primitives.get("connection");
    }

    public static UpdateConnectionResultDto fromPrimitives(Map<String, Object> primitives) {
        return new UpdateConnectionResultDto(primitives);
    }

    /**
     * La conexion ya guardada, sin la password.
     */
    public Map<String, Object> getConnection() {
        return connection;
    }
}
