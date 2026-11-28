package modules.connections.application.cloneconnection;

import java.util.Map;

import static modules.shared.application.Primitives.getTrimmedString;

public final class CloneConnectionDto {

    private final String connectionUuid;

    private CloneConnectionDto(Map<String, Object> primitives) {
        this.connectionUuid = getTrimmedString(primitives.get("connectionUuid"));
    }

    public static CloneConnectionDto fromPrimitives(Map<String, Object> primitives) {
        return new CloneConnectionDto(primitives);
    }

    public String getConnectionUuid() {
        return connectionUuid;
    }
}
