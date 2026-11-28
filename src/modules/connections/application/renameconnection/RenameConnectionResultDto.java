package modules.connections.application.renameconnection;

import java.util.Map;

public final class RenameConnectionResultDto {

    private final Map<String, Object> connection;

    private RenameConnectionResultDto(Map<String, Object> primitives) {
        @SuppressWarnings("unchecked")
        Map<String, Object> savedConnection = (Map<String, Object>) primitives.get("connection");
        this.connection = savedConnection == null ? Map.of() : savedConnection;
    }

    public static RenameConnectionResultDto fromPrimitives(Map<String, Object> primitives) {
        return new RenameConnectionResultDto(primitives);
    }

    public Map<String, Object> getConnection() {
        return connection;
    }
}
