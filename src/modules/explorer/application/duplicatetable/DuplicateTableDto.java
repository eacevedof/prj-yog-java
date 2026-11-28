package modules.explorer.application.duplicatetable;

import java.util.Map;

import static modules.shared.application.Primitives.getTrimmedString;

public final class DuplicateTableDto {

    private final String connectionUuid;
    private final String sessionKey;
    private final String databaseName;
    private final String tableName;
    private final String copyTableName;

    private DuplicateTableDto(Map<String, Object> primitives) {
        this.connectionUuid = getTrimmedString(primitives.get("connectionUuid"));
        this.sessionKey = getTrimmedString(primitives.get("sessionKey"));
        this.databaseName = getTrimmedString(primitives.get("databaseName"));
        this.tableName = getTrimmedString(primitives.get("tableName"));
        this.copyTableName = getTrimmedString(primitives.get("copyTableName"));
    }

    public static DuplicateTableDto fromPrimitives(Map<String, Object> primitives) {
        return new DuplicateTableDto(primitives);
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
     * Vacio: el nombre por defecto, &lt;tabla&gt;_copy_&lt;ymdhis&gt;.
     */
    public String getCopyTableName() {
        return copyTableName;
    }
}
