package modules.connections.application.deleteconnection;

import java.util.Map;

import static modules.shared.application.Primitives.getTrimmedString;

public final class DeleteConnectionDto {

    private final String connectionUuid;

    private DeleteConnectionDto(Map<String, Object> primitives) {
        this.connectionUuid = getTrimmedString(primitives.get("connectionUuid"));
    }

    public static DeleteConnectionDto fromPrimitives(Map<String, Object> primitives) {
        return new DeleteConnectionDto(primitives);
    }

    public String getConnectionUuid() {
        return connectionUuid;
    }
}
