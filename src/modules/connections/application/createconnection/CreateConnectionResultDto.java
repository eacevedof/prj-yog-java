package modules.connections.application.createconnection;

import java.util.Map;

import static modules.shared.application.Primitives.getTrimmedString;

public final class CreateConnectionResultDto {

    private final Map<String, Object> connection;
    private final String connectionUuid;

    @SuppressWarnings("unchecked")
    private CreateConnectionResultDto(Map<String, Object> primitives) {
        this.connection = (Map<String, Object>) primitives.get("connection");
        this.connectionUuid = getTrimmedString(primitives.get("connectionUuid"));
    }

    public static CreateConnectionResultDto fromPrimitives(Map<String, Object> primitives) {
        return new CreateConnectionResultDto(primitives);
    }

    /**
     * La conexion creada, sin la password (ConnectionEntity.toPrimitives).
     */
    public Map<String, Object> getConnection() {
        return connection;
    }

    public String getConnectionUuid() {
        return connectionUuid;
    }
}
