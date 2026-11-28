package modules.explorer.application.getcreatedatabaseoptions;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static modules.shared.application.Primitives.getBool;
import static modules.shared.application.Primitives.getTrimmedString;

/**
 * Lo que necesita el dialogo "Crear BD" segun el motor. MySQL: juegos con sus cotejamientos, los del servidor por
 * defecto y si acepta ENCRYPTION y COMMENT. PostgreSQL: roles (propietario) y el actual. SQL Server: cotejamientos y el
 * del servidor.
 */
public final class GetCreateDatabaseOptionsResultDto {

    private final String dbEngine;
    private final String defaultCharacterSet;
    private final String defaultCollation;
    private final Map<String, List<String>> collationsByCharacterSet = new LinkedHashMap<>();
    private final List<String> collations;
    private final List<String> owners;
    private final String defaultOwner;
    private final boolean isEncryptionSupported;
    private final boolean isCommentSupported;

    private GetCreateDatabaseOptionsResultDto(Map<String, Object> primitives) {
        this.dbEngine = getTrimmedString(primitives.get("dbEngine"));
        this.defaultCharacterSet = getTrimmedString(primitives.get("defaultCharacterSet"));
        this.defaultCollation = getTrimmedString(primitives.get("defaultCollation"));
        if (primitives.get("collationsByCharacterSet") instanceof Map<?, ?> collationsMap) {
            collationsMap.forEach((characterSet, characterSetCollations) -> collationsByCharacterSet.put(
                getTrimmedString(characterSet), getTexts(characterSetCollations)
            ));
        }
        this.collations = getTexts(primitives.get("collations"));
        this.owners = getTexts(primitives.get("owners"));
        this.defaultOwner = getTrimmedString(primitives.get("defaultOwner"));
        this.isEncryptionSupported = getBool(primitives.get("isEncryptionSupported"));
        this.isCommentSupported = getBool(primitives.get("isCommentSupported"));
    }

    public static GetCreateDatabaseOptionsResultDto fromPrimitives(Map<String, Object> primitives) {
        return new GetCreateDatabaseOptionsResultDto(primitives);
    }

    private List<String> getTexts(Object values) {
        if (!(values instanceof List<?> list)) return List.of();
        return list.stream().map(value -> getTrimmedString(value)).toList();
    }

    public String getDbEngine() {
        return dbEngine;
    }

    public String getDefaultCharacterSet() {
        return defaultCharacterSet;
    }

    public String getDefaultCollation() {
        return defaultCollation;
    }

    public Map<String, List<String>> getCollationsByCharacterSet() {
        return collationsByCharacterSet;
    }

    public List<String> getCollations() {
        return collations;
    }

    public List<String> getOwners() {
        return owners;
    }

    public String getDefaultOwner() {
        return defaultOwner;
    }

    public boolean isEncryptionSupported() {
        return isEncryptionSupported;
    }

    public boolean isCommentSupported() {
        return isCommentSupported;
    }
}
