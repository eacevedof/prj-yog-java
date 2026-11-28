package modules.explorer.application.getcreatetableoptions;

import java.util.Map;

import static modules.shared.application.Primitives.getTrimmedString;

public final class GetCreateTableOptionsDto {

    private final String connectionUuid;
    private final String sessionKey;
    private final String databaseName;

    private GetCreateTableOptionsDto(Map<String, Object> primitives) {
        this.connectionUuid = getTrimmedString(primitives.get("connectionUuid"));
        this.sessionKey = getTrimmedString(primitives.get("sessionKey"));
        this.databaseName = getTrimmedString(primitives.get("databaseName"));
    }

    public static GetCreateTableOptionsDto fromPrimitives(Map<String, Object> primitives) {
        return new GetCreateTableOptionsDto(primitives);
    }

    public String getConnectionUuid() {
        return connectionUuid;
    }

    public String getSessionKey() {
        return sessionKey;
    }

    public String getDatabaseName() {
        return databaseName;
    }
}
