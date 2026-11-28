package modules.explorer.application.droptable;

import java.util.Map;

import static modules.shared.application.Primitives.getBool;
import static modules.shared.application.Primitives.getTrimmedString;

public final class DropTableDto {

    private final String connectionUuid;
    private final String sessionKey;
    private final String databaseName;
    private final String tableName;
    private final boolean isConfirmed;

    private DropTableDto(Map<String, Object> primitives) {
        this.connectionUuid = getTrimmedString(primitives.get("connectionUuid"));
        this.sessionKey = getTrimmedString(primitives.get("sessionKey"));
        this.databaseName = getTrimmedString(primitives.get("databaseName"));
        this.tableName = getTrimmedString(primitives.get("tableName"));
        this.isConfirmed = getBool(primitives.get("isConfirmed"));
    }

    public static DropTableDto fromPrimitives(Map<String, Object> primitives) {
        return new DropTableDto(primitives);
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
     * En una conexion de produccion: true solo tras escribir el nombre de la tabla para confirmarlo.
     */
    public boolean isConfirmed() {
        return isConfirmed;
    }
}
