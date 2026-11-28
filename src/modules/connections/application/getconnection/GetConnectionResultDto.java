package modules.connections.application.getconnection;

import java.util.Map;

public final class GetConnectionResultDto {

    private final Map<String, Object> connection;

    @SuppressWarnings("unchecked")
    private GetConnectionResultDto(Map<String, Object> primitives) {
        this.connection = (Map<String, Object>) primitives.get("connection");
    }

    public static GetConnectionResultDto fromPrimitives(Map<String, Object> primitives) {
        return new GetConnectionResultDto(primitives);
    }

    /**
     * La conexion, sin la password.
     */
    public Map<String, Object> getConnection() {
        return connection;
    }
}
