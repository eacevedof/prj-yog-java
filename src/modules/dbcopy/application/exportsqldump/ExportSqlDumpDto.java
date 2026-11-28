package modules.dbcopy.application.exportsqldump;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static modules.shared.application.Primitives.getBool;
import static modules.shared.application.Primitives.getTrimmedString;

import modules.dbcopy.domain.enums.DbCopyObjectTypeEnum;

/**
 * El "SQL Dump" de SQLyog: la BD, el fichero, los objetos marcados por tipo ("objects" => [tipo => [nombres]]), que va
 * (SqlDumpModeEnum) y las opciones (de origen: transaccion consistente; del fichero: USE, CREATE DATABASE,
 * FOREIGN_KEY_CHECKS=0, DROP, sin DEFINER, INSERT en bloque, version; avanzadas: fecha delante del nombre).
 */
public final class ExportSqlDumpDto {

    private final String dumpId;
    private final String connectionUuid;
    private final String databaseName;
    private final String filePath;
    private final String dumpMode;
    private final Map<String, List<String>> objectNamesByType = new LinkedHashMap<>();
    private final boolean isSingleTransaction;
    private final boolean isUseIncluded;
    private final boolean isCreateDatabaseIncluded;
    private final boolean isForeignKeyChecksOff;
    private final boolean isDropIncluded;
    private final boolean isIgnoreDefiner;
    private final boolean isBulkInsert;
    private final boolean isVersionIncluded;
    private final boolean isTimestampPrefix;

    private ExportSqlDumpDto(Map<String, Object> primitives) {
        this.dumpId = getTrimmedString(primitives.get("dumpId"));
        this.connectionUuid = getTrimmedString(primitives.get("connectionUuid"));
        this.databaseName = getTrimmedString(primitives.get("databaseName"));
        this.filePath = getTrimmedString(primitives.get("filePath"));
        this.dumpMode = getTrimmedString(primitives.get("dumpMode"));
        this.isSingleTransaction = getBool(primitives.get("isSingleTransaction"));
        this.isUseIncluded = getBool(primitives.get("isUseIncluded"));
        this.isCreateDatabaseIncluded = getBool(primitives.get("isCreateDatabaseIncluded"));
        this.isForeignKeyChecksOff = getBool(primitives.get("isForeignKeyChecksOff"));
        this.isDropIncluded = getBool(primitives.get("isDropIncluded"));
        this.isIgnoreDefiner = getBool(primitives.get("isIgnoreDefiner"));
        this.isBulkInsert = getBool(primitives.get("isBulkInsert"));
        this.isVersionIncluded = getBool(primitives.get("isVersionIncluded"));
        this.isTimestampPrefix = getBool(primitives.get("isTimestampPrefix"));
        Object objects = primitives.get("objects");
        for (String objectType : DbCopyObjectTypeEnum.VALUES) {
            List<String> names = new ArrayList<>();
            if (objects instanceof Map<?, ?> objectsByType && objectsByType.get(objectType) instanceof List<?> rawNames) {
                for (Object rawName : rawNames) {
                    String name = getTrimmedString(rawName);
                    if (!name.isEmpty() && !names.contains(name)) names.add(name);
                }
            }
            objectNamesByType.put(objectType, List.copyOf(names));
        }
    }

    public static ExportSqlDumpDto fromPrimitives(Map<String, Object> primitives) {
        return new ExportSqlDumpDto(primitives);
    }

    public String getDumpId() {
        return dumpId;
    }

    public String getConnectionUuid() {
        return connectionUuid;
    }

    public String getDatabaseName() {
        return databaseName;
    }

    public String getFilePath() {
        return filePath;
    }

    public String getDumpMode() {
        return dumpMode;
    }

    public List<String> getObjectNames(String objectType) {
        return objectNamesByType.getOrDefault(objectType, List.of());
    }

    public boolean isSingleTransaction() {
        return isSingleTransaction;
    }

    public boolean isUseIncluded() {
        return isUseIncluded;
    }

    public boolean isCreateDatabaseIncluded() {
        return isCreateDatabaseIncluded;
    }

    public boolean isForeignKeyChecksOff() {
        return isForeignKeyChecksOff;
    }

    public boolean isDropIncluded() {
        return isDropIncluded;
    }

    public boolean isIgnoreDefiner() {
        return isIgnoreDefiner;
    }

    public boolean isBulkInsert() {
        return isBulkInsert;
    }

    public boolean isVersionIncluded() {
        return isVersionIncluded;
    }

    public boolean isTimestampPrefix() {
        return isTimestampPrefix;
    }
}
