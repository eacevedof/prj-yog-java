package modules.connections.application.deleteconnection;

import java.util.Map;

import static modules.shared.application.Primitives.getTrimmedString;

public final class DeleteConnectionResultDto {

    private final String connectionUuid;
    private final String connectionName;

    private DeleteConnectionResultDto(Map<String, Object> primitives) {
        this.connectionUuid = getTrimmedString(primitives.get("connectionUuid"));
        this.connectionName = getTrimmedString(primitives.get("connectionName"));
    }

    public static DeleteConnectionResultDto fromPrimitives(Map<String, Object> primitives) {
        return new DeleteConnectionResultDto(primitives);
    }

    public String getConnectionUuid() {
        return connectionUuid;
    }

    public String getConnectionName() {
        return connectionName;
    }
}
