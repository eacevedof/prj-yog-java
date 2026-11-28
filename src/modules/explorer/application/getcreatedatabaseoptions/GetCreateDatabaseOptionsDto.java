package modules.explorer.application.getcreatedatabaseoptions;

import java.util.Map;

import static modules.shared.application.Primitives.getTrimmedString;

public final class GetCreateDatabaseOptionsDto {

    private final String connectionUuid;
    private final String sessionKey;

    private GetCreateDatabaseOptionsDto(Map<String, Object> primitives) {
        this.connectionUuid = getTrimmedString(primitives.get("connectionUuid"));
        this.sessionKey = getTrimmedString(primitives.get("sessionKey"));
    }

    public static GetCreateDatabaseOptionsDto fromPrimitives(Map<String, Object> primitives) {
        return new GetCreateDatabaseOptionsDto(primitives);
    }

    public String getConnectionUuid() {
        return connectionUuid;
    }

    public String getSessionKey() {
        return sessionKey;
    }
}
