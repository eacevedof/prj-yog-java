package modules.mongo.application.finddocuments;

import java.util.Map;

import static modules.shared.application.Primitives.getInt;
import static modules.shared.application.Primitives.getTrimmedString;

public final class FindMongoDocumentsDto {

    public static final int DEFAULT_LIMIT = 100;

    private final String connectionUuid;
    private final String sessionKey;
    private final String databaseName;
    private final String collectionName;
    private final String filterJson;
    private final String sortJson;
    private final int skip;
    private final int limit;

    private FindMongoDocumentsDto(Map<String, Object> primitives) {
        this.connectionUuid = getTrimmedString(primitives.get("connectionUuid"));
        this.sessionKey = getTrimmedString(primitives.get("sessionKey"));
        this.databaseName = getTrimmedString(primitives.get("databaseName"));
        this.collectionName = getTrimmedString(primitives.get("collectionName"));
        this.filterJson = getTrimmedString(primitives.get("filterJson"));
        this.sortJson = getTrimmedString(primitives.get("sortJson"));
        this.skip = Math.max(0, getInt(primitives.get("skip")));
        int limitValue = getInt(primitives.get("limit"));
        this.limit = limitValue <= 0 ? DEFAULT_LIMIT : limitValue;
    }

    public static FindMongoDocumentsDto fromPrimitives(Map<String, Object> primitives) {
        return new FindMongoDocumentsDto(primitives);
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

    public String getCollectionName() {
        return collectionName;
    }

    /**
     * {age: {$gt: 18}}; vacio = todos.
     */
    public String getFilterJson() {
        return filterJson;
    }

    /**
     * {age: -1}; vacio = orden natural.
     */
    public String getSortJson() {
        return sortJson;
    }

    public int getSkip() {
        return skip;
    }

    public int getLimit() {
        return limit;
    }
}
