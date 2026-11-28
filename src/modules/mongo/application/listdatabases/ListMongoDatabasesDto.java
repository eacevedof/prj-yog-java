package modules.mongo.application.listdatabases;

import java.util.Map;

import static modules.shared.application.Primitives.getTrimmedString;

public final class ListMongoDatabasesDto {

    private final String connectionUuid;
    private final String sessionKey;

    private ListMongoDatabasesDto(Map<String, Object> primitives) {
        this.connectionUuid = getTrimmedString(primitives.get("connectionUuid"));
        this.sessionKey = getTrimmedString(primitives.get("sessionKey"));
    }

    public static ListMongoDatabasesDto fromPrimitives(Map<String, Object> primitives) {
        return new ListMongoDatabasesDto(primitives);
    }

    public String getConnectionUuid() {
        return connectionUuid;
    }

    public String getSessionKey() {
        return sessionKey;
    }
}
