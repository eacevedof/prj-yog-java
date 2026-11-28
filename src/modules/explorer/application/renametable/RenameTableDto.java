package modules.explorer.application.renametable;

import java.util.Map;

import static modules.shared.application.Primitives.getBool;
import static modules.shared.application.Primitives.getTrimmedString;

public final class RenameTableDto {

    private final String connectionUuid;
    private final String sessionKey;
    private final String databaseName;
    private final String tableName;
    private final String newTableName;
    private final boolean isConfirmed;

    private RenameTableDto(Map<String, Object> primitives) {
        this.connectionUuid = getTrimmedString(primitives.get("connectionUuid"));
        this.sessionKey = getTrimmedString(primitives.get("sessionKey"));
        this.databaseName = getTrimmedString(primitives.get("databaseName"));
        this.tableName = getTrimmedString(primitives.get("tableName"));
        this.newTableName = getTrimmedString(primitives.get("newTableName"));
        this.isConfirmed = getBool(primitives.get("isConfirmed"));
    }

    public static RenameTableDto fromPrimitives(Map<String, Object> primitives) {
        return new RenameTableDto(primitives);
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

    public String getNewTableName() {
        return newTableName;
    }

    /**
     * En una conexion de produccion: true solo tras confirmarlo.
     */
    public boolean isConfirmed() {
        return isConfirmed;
    }
}
