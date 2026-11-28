package modules.mongo.application.listdatabases;

import java.util.List;
import java.util.Map;

public final class ListMongoDatabasesResultDto {

    private final List<String> databaseNames;

    @SuppressWarnings("unchecked")
    private ListMongoDatabasesResultDto(Map<String, Object> primitives) {
        this.databaseNames = List.copyOf((List<String>) primitives.get("databaseNames"));
    }

    public static ListMongoDatabasesResultDto fromPrimitives(Map<String, Object> primitives) {
        return new ListMongoDatabasesResultDto(primitives);
    }

    public List<String> getDatabaseNames() {
        return databaseNames;
    }
}
