package modules.dbcopy.application.copydatabase;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static modules.shared.application.Primitives.getBool;
import static modules.shared.application.Primitives.getTrimmedString;

import modules.dbcopy.domain.enums.DbCopyObjectTypeEnum;

/**
 * Lo que se copia y como, las opciones del dialogo de SQLyog (y el id de la copia para su progreso): objetos marcados por tipo ("objects" => [tipo =>
 * [nombres]]), estructura y datos o solo estructura, borrar en destino, INSERT en bloque, sin DEFINER y triggers
 * asociados a las tablas marcadas.
 */
public final class CopyDatabaseDto {

    private final String copyId;
    private final String sourceConnectionUuid;
    private final String sourceDatabase;
    private final String targetConnectionUuid;
    private final String targetDatabase;
    private final Map<String, List<String>> objectNamesByType = new LinkedHashMap<>();
    private final boolean isStructureOnly;
    private final boolean isDropIfExists;
    private final boolean isBulkInsert;
    private final boolean isIgnoreDefiner;
    private final boolean isCopyAssociatedTriggers;
    private final boolean isConfirmed;

    private CopyDatabaseDto(Map<String, Object> primitives) {
        this.copyId = getTrimmedString(primitives.get("copyId"));
        this.sourceConnectionUuid = getTrimmedString(primitives.get("sourceConnectionUuid"));
        this.sourceDatabase = getTrimmedString(primitives.get("sourceDatabase"));
        this.targetConnectionUuid = getTrimmedString(primitives.get("targetConnectionUuid"));
        this.targetDatabase = getTrimmedString(primitives.get("targetDatabase"));
        this.isStructureOnly = getBool(primitives.get("isStructureOnly"));
        this.isDropIfExists = getBool(primitives.get("isDropIfExists"));
        this.isBulkInsert = getBool(primitives.get("isBulkInsert"));
        this.isIgnoreDefiner = getBool(primitives.get("isIgnoreDefiner"));
        this.isCopyAssociatedTriggers = getBool(primitives.get("isCopyAssociatedTriggers"));
        this.isConfirmed = getBool(primitives.get("isConfirmed"));
        Object objects = primitives.get("objects");
        for (String objectType : DbCopyObjectTypeEnum.VALUES) {
            List<String> names = new ArrayList<>();
            if (objects instanceof Map<?, ?> objectsByType && objectsByType.get(objectType) instanceof List<?> rawNames) {
                for (Object rawName : rawNames) {
                    names.add(getTrimmedString(rawName));
                }
            }
            objectNamesByType.put(objectType, List.copyOf(names));
        }
    }

    public static CopyDatabaseDto fromPrimitives(Map<String, Object> primitives) {
        return new CopyDatabaseDto(primitives);
    }

    /**
     * El id con el que la UI registra su listener en DbCopyProgressTracker; vacio = copia sin progreso ni Parar.
     */
    public String getCopyId() {
        return copyId;
    }

    public String getSourceConnectionUuid() {
        return sourceConnectionUuid;
    }

    public String getSourceDatabase() {
        return sourceDatabase;
    }

    public String getTargetConnectionUuid() {
        return targetConnectionUuid;
    }

    public String getTargetDatabase() {
        return targetDatabase;
    }

    /**
     * Los nombres marcados de un tipo (DbCopyObjectTypeEnum), tal como llegan.
     */
    public List<String> getObjectNames(String objectType) {
        return objectNamesByType.getOrDefault(objectType, List.of());
    }

    public boolean isStructureOnly() {
        return isStructureOnly;
    }

    public boolean isDropIfExists() {
        return isDropIfExists;
    }

    public boolean isBulkInsert() {
        return isBulkInsert;
    }

    public boolean isIgnoreDefiner() {
        return isIgnoreDefiner;
    }

    public boolean isCopyAssociatedTriggers() {
        return isCopyAssociatedTriggers;
    }

    public boolean isConfirmed() {
        return isConfirmed;
    }
}
