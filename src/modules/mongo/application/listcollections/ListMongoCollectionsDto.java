package modules.mongo.application.listcollections;

import java.util.Map;

import static modules.shared.application.Primitives.getTrimmedString;

public final class ListMongoCollectionsDto {

    private final String connectionUuid;
    private final String sessionKey;
    private final String databaseName;

    private ListMongoCollectionsDto(Map<String, Object> primitives) {
        this.connectionUuid = getTrimmedString(primitives.get("connectionUuid"));
        this.sessionKey = getTrimmedString(primitives.get("sessionKey"));
        this.databaseName = getTrimmedString(primitives.get("databaseName"));
    }

    public static ListMongoCollectionsDto fromPrimitives(Map<String, Object> primitives) {
        return new ListMongoCollectionsDto(primitives);
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
