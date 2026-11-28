package modules.explorer.application.listdatabases;

import java.util.Map;

import static modules.shared.application.Primitives.getTrimmedString;

public final class ListDatabasesDto {

    private final String connectionUuid;
    private final String sessionKey;

    private ListDatabasesDto(Map<String, Object> primitives) {
        this.connectionUuid = getTrimmedString(primitives.get("connectionUuid"));
        this.sessionKey = getTrimmedString(primitives.get("sessionKey"));
    }

    public static ListDatabasesDto fromPrimitives(Map<String, Object> primitives) {
        return new ListDatabasesDto(primitives);
    }

    public String getConnectionUuid() {
        return connectionUuid;
    }

    public String getSessionKey() {
        return sessionKey;
    }
}
