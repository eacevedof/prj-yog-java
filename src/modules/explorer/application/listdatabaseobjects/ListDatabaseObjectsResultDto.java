package modules.explorer.application.listdatabaseobjects;

import java.util.List;
import java.util.Map;

import modules.explorer.domain.enums.ExplorerKeyEnum;

public final class ListDatabaseObjectsResultDto {

    private final List<String> tables;
    private final List<String> views;
    private final List<String> procedures;
    private final List<String> functions;
    private final List<String> triggers;

    private ListDatabaseObjectsResultDto(Map<String, Object> primitives) {
        this.tables = getNames(primitives, ExplorerKeyEnum.TABLES);
        this.views = getNames(primitives, ExplorerKeyEnum.VIEWS);
        this.procedures = getNames(primitives, ExplorerKeyEnum.PROCEDURES);
        this.functions = getNames(primitives, ExplorerKeyEnum.FUNCTIONS);
        this.triggers = getNames(primitives, ExplorerKeyEnum.TRIGGERS);
    }

    public static ListDatabaseObjectsResultDto fromPrimitives(Map<String, Object> primitives) {
        return new ListDatabaseObjectsResultDto(primitives);
    }

    @SuppressWarnings("unchecked")
    private static List<String> getNames(Map<String, Object> primitives, String key) {
        Object names = primitives.get(key);
        return names == null ? List.of() : List.copyOf((List<String>) names);
    }

    public List<String> getTables() {
        return tables;
    }

    public List<String> getViews() {
        return views;
    }

    public List<String> getProcedures() {
        return procedures;
    }

    public List<String> getFunctions() {
        return functions;
    }

    public List<String> getTriggers() {
        return triggers;
    }
}
