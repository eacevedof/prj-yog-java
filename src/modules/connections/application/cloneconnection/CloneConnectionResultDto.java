package modules.connections.application.cloneconnection;

import java.util.Map;

public final class CloneConnectionResultDto {

    private final Map<String, Object> connection;

    @SuppressWarnings("unchecked")
    private CloneConnectionResultDto(Map<String, Object> primitives) {
        this.connection = (Map<String, Object>) primitives.get("connection");
    }

    public static CloneConnectionResultDto fromPrimitives(Map<String, Object> primitives) {
        return new CloneConnectionResultDto(primitives);
    }

    /**
     * La conexion, sin la password.
     */
    public Map<String, Object> getConnection() {
        return connection;
    }
}
