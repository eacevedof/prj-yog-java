package modules.query.application.applytablechanges;

import java.util.List;
import java.util.Map;

import static modules.shared.application.Primitives.getBool;
import static modules.shared.application.Primitives.getTrimmedString;

public final class ApplyTableChangesDto {

    private final String connectionUuid;
    private final String sessionKey;
    private final String databaseName;
    private final String tableName;
    private final List<Map<String, Object>> changes;
    private final boolean isConfirmed;

    @SuppressWarnings("unchecked")
    private ApplyTableChangesDto(Map<String, Object> primitives) {
        this.connectionUuid = getTrimmedString(primitives.get("connectionUuid"));
        this.sessionKey = getTrimmedString(primitives.get("sessionKey"));
        this.databaseName = getTrimmedString(primitives.get("databaseName"));
        this.tableName = getTrimmedString(primitives.get("tableName"));
        Object changesValue = primitives.get("changes");
        this.changes = changesValue instanceof List<?> ? List.copyOf((List<Map<String, Object>>) changesValue) : List.of();
        this.isConfirmed = getBool(primitives.get("isConfirmed"));
    }

    public static ApplyTableChangesDto fromPrimitives(Map<String, Object> primitives) {
        return new ApplyTableChangesDto(primitives);
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

    public String getTableName() {
        return tableName;
    }

    /**
     * Con las claves de TableChangeKeyEnum.
     */
    public List<Map<String, Object>> getChanges() {
        return changes;
    }

    /**
     * true: el usuario ya acepto guardar en una conexion de produccion.
     */
    public boolean isConfirmed() {
        return isConfirmed;
    }
}
