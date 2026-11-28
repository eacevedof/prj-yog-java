package modules.explorer.application.listdatabases;

import java.util.List;
import java.util.Map;

public final class ListDatabasesResultDto {

    private final List<String> databaseNames;

    @SuppressWarnings("unchecked")
    private ListDatabasesResultDto(Map<String, Object> primitives) {
        this.databaseNames = List.copyOf((List<String>) primitives.get("databaseNames"));
    }

    public static ListDatabasesResultDto fromPrimitives(Map<String, Object> primitives) {
        return new ListDatabasesResultDto(primitives);
    }

    public List<String> getDatabaseNames() {
        return databaseNames;
    }
}
