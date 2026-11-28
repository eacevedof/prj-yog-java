package modules.connections.application.getconnection;

import java.util.Map;

import static modules.shared.application.Primitives.getTrimmedString;

public final class GetConnectionDto {

    private final String connectionUuid;

    private GetConnectionDto(Map<String, Object> primitives) {
        this.connectionUuid = getTrimmedString(primitives.get("connectionUuid"));
    }

    public static GetConnectionDto fromPrimitives(Map<String, Object> primitives) {
        return new GetConnectionDto(primitives);
    }

    public String getConnectionUuid() {
        return connectionUuid;
    }
}
