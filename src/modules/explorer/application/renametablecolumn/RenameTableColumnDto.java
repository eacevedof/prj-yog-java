package modules.explorer.application.renametablecolumn;

import java.util.Map;

import static modules.shared.application.Primitives.getBool;
import static modules.shared.application.Primitives.getTrimmedString;

public final class RenameTableColumnDto {

    private final String connectionUuid;
    private final String sessionKey;
    private final String databaseName;
    private final String tableName;
    private final String columnName;
    private final String newColumnName;
    private final boolean isConfirmed;

    private RenameTableColumnDto(Map<String, Object> primitives) {
        this.connectionUuid = getTrimmedString(primitives.get("connectionUuid"));
        this.sessionKey = getTrimmedString(primitives.get("sessionKey"));
        this.databaseName = getTrimmedString(primitives.get("databaseName"));
        this.tableName = getTrimmedString(primitives.get("tableName"));
        this.columnName = getTrimmedString(primitives.get("columnName"));
        this.newColumnName = getTrimmedString(primitives.get("newColumnName"));
        this.isConfirmed = getBool(primitives.get("isConfirmed"));
    }

    public static RenameTableColumnDto fromPrimitives(Map<String, Object> primitives) {
        return new RenameTableColumnDto(primitives);
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

    public String getColumnName() {
        return columnName;
    }

    public String getNewColumnName() {
        return newColumnName;
    }

    /**
     * En una conexion de produccion: true solo tras confirmarlo.
     */
    public boolean isConfirmed() {
        return isConfirmed;
    }
}
