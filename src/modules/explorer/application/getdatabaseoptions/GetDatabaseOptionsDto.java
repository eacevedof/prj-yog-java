package modules.explorer.application.getdatabaseoptions;

import java.util.Map;

import static modules.shared.application.Primitives.getTrimmedString;

public final class GetDatabaseOptionsDto {

    private final String connectionUuid;
    private final String sessionKey;
    private final String databaseName;

    private GetDatabaseOptionsDto(Map<String, Object> primitives) {
        this.connectionUuid = getTrimmedString(primitives.get("connectionUuid"));
        this.sessionKey = getTrimmedString(primitives.get("sessionKey"));
        this.databaseName = getTrimmedString(primitives.get("databaseName"));
    }

    public static GetDatabaseOptionsDto fromPrimitives(Map<String, Object> primitives) {
        return new GetDatabaseOptionsDto(primitives);
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
