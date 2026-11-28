package modules.explorer.application.gettabledefinition;

import java.util.Map;

import static modules.shared.application.Primitives.getTrimmedString;

public final class GetTableDefinitionDto {

    private final String connectionUuid;
    private final String sessionKey;
    private final String databaseName;
    private final String tableName;

    private GetTableDefinitionDto(Map<String, Object> primitives) {
        this.connectionUuid = getTrimmedString(primitives.get("connectionUuid"));
        this.sessionKey = getTrimmedString(primitives.get("sessionKey"));
        this.databaseName = getTrimmedString(primitives.get("databaseName"));
        this.tableName = getTrimmedString(primitives.get("tableName"));
    }

    public static GetTableDefinitionDto fromPrimitives(Map<String, Object> primitives) {
        return new GetTableDefinitionDto(primitives);
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
