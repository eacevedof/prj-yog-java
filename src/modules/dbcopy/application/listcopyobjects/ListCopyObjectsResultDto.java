package modules.dbcopy.application.listcopyobjects;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static modules.shared.application.Primitives.getTrimmedString;

import modules.dbcopy.domain.enums.DbCopyKeyEnum;
import modules.dbcopy.domain.enums.DbCopyObjectTypeEnum;

public final class ListCopyObjectsResultDto {

    private final Map<String, List<String>> namesByType = new LinkedHashMap<>();
    private final Map<String, String> triggerTables = new LinkedHashMap<>();

    private ListCopyObjectsResultDto(Map<String, Object> primitives) {
        namesByType.put(DbCopyObjectTypeEnum.TABLE, getNamesFromPrimitives(primitives, DbCopyKeyEnum.TABLES));
        namesByType.put(DbCopyObjectTypeEnum.VIEW, getNamesFromPrimitives(primitives, DbCopyKeyEnum.VIEWS));
        namesByType.put(DbCopyObjectTypeEnum.PROCEDURE, getNamesFromPrimitives(primitives, DbCopyKeyEnum.PROCEDURES));
        namesByType.put(DbCopyObjectTypeEnum.FUNCTION, getNamesFromPrimitives(primitives, DbCopyKeyEnum.FUNCTIONS));
        namesByType.put(DbCopyObjectTypeEnum.TRIGGER, getNamesFromPrimitives(primitives, DbCopyKeyEnum.TRIGGERS));
        namesByType.put(DbCopyObjectTypeEnum.EVENT, getNamesFromPrimitives(primitives, DbCopyKeyEnum.EVENTS));
        if (primitives.get(DbCopyKeyEnum.TRIGGER_TABLES) instanceof Map<?, ?> tables) {
            tables.forEach((trigger, table) -> triggerTables.put(getTrimmedString(trigger), getTrimmedString(table)));
        }
    }

    public static ListCopyObjectsResultDto fromPrimitives(Map<String, Object> primitives) {
        return new ListCopyObjectsResultDto(primitives);
    }

    @SuppressWarnings("unchecked")
    private List<String> getNamesFromPrimitives(Map<String, Object> primitives, String key) {
        Object names = primitives.get(key);
        return names == null ? List.of() : List.copyOf((List<String>) names);
    }

    /**
     * Tipo (DbCopyObjectTypeEnum) => nombres, en el orden de creacion.
     */
    public Map<String, List<String>> getNamesByType() {
        return namesByType;
    }

    public List<String> getNames(String objectType) {
        return namesByType.getOrDefault(objectType, List.of());
    }

    /**
     * Trigger => la tabla en la que salta.
     */
    public Map<String, String> getTriggerTables() {
        return triggerTables;
    }
}
