package modules.explorer.application.dropdatabase;

import java.util.Map;

import static modules.shared.application.Primitives.getBool;
import static modules.shared.application.Primitives.getTrimmedString;

public final class DropDatabaseDto {

    private final String connectionUuid;
    private final String sessionKey;
    private final String databaseName;
    private final boolean isConfirmed;

    private DropDatabaseDto(Map<String, Object> primitives) {
        this.connectionUuid = getTrimmedString(primitives.get("connectionUuid"));
        this.sessionKey = getTrimmedString(primitives.get("sessionKey"));
        this.databaseName = getTrimmedString(primitives.get("databaseName"));
        this.isConfirmed = getBool(primitives.get("isConfirmed"));
    }

    public static DropDatabaseDto fromPrimitives(Map<String, Object> primitives) {
        return new DropDatabaseDto(primitives);
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

    public boolean isConfirmed() {
        return isConfirmed;
    }
}
