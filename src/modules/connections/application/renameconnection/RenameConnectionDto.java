package modules.connections.application.renameconnection;

import java.util.Map;

import static modules.shared.application.Primitives.getTrimmedString;

public final class RenameConnectionDto {

    private final String connectionUuid;
    private final String connectionName;

    private RenameConnectionDto(Map<String, Object> primitives) {
        this.connectionUuid = getTrimmedString(primitives.get("connectionUuid"));
        this.connectionName = getTrimmedString(primitives.get("connectionName"));
    }

    public static RenameConnectionDto fromPrimitives(Map<String, Object> primitives) {
        return new RenameConnectionDto(primitives);
    }

    public String getConnectionUuid() {
        return connectionUuid;
    }

    public String getConnectionName() {
        return connectionName;
    }
}
