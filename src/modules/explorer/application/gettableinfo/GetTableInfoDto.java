package modules.explorer.application.gettableinfo;

import java.util.Map;

import static modules.shared.application.Primitives.getTrimmedString;

public final class GetTableInfoDto {

    private final String connectionUuid;
    private final String sessionKey;
    private final String databaseName;
    private final String tableName;

    private GetTableInfoDto(Map<String, Object> primitives) {
        this.connectionUuid = getTrimmedString(primitives.get("connectionUuid"));
        this.sessionKey = getTrimmedString(primitives.get("sessionKey"));
        this.databaseName = getTrimmedString(primitives.get("databaseName"));
        this.tableName = getTrimmedString(primitives.get("tableName"));
    }

    public static GetTableInfoDto fromPrimitives(Map<String, Object> primitives) {
        return new GetTableInfoDto(primitives);
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
}
