package modules.query.application.executequery;

import java.util.Map;

import static modules.shared.application.Primitives.getBool;
import static modules.shared.application.Primitives.getInt;
import static modules.shared.application.Primitives.getTrimmedString;

public final class ExecuteQueryDto {

    private final String connectionUuid;
    private final String sessionKey;
    private final String databaseName;
    private final String sqlQuery;
    private final int maxRows;
    private final boolean isConfirmed;

    private ExecuteQueryDto(Map<String, Object> primitives) {
        this.connectionUuid = getTrimmedString(primitives.get("connectionUuid"));
        this.sessionKey = getTrimmedString(primitives.get("sessionKey"));
        this.databaseName = getTrimmedString(primitives.get("databaseName"));
        this.sqlQuery = getTrimmedString(primitives.get("sqlQuery"));
        this.maxRows = getInt(primitives.get("maxRows"));
        this.isConfirmed = getBool(primitives.get("isConfirmed"));
    }

    public static ExecuteQueryDto fromPrimitives(Map<String, Object> primitives) {
        return new ExecuteQueryDto(primitives);
    }

    public String getConnectionUuid() {
        return connectionUuid;
    }

    public String getSessionKey() {
        return sessionKey;
    }

    /**
     * La BD activa de la pestaña ("" = la que tenga la sesion).
     */
    public String getDatabaseName() {
        return databaseName;
    }

    /**
     * Una o varias sentencias: se trocean y se ejecutan en orden hasta la primera que falle.
     */
    public String getSqlQuery() {
        return sqlQuery;
    }

    /**
     * 0 = sin limite.
     */
    public int getMaxRows() {
        return maxRows;
    }

    /**
     * true: el usuario ya confirmo las sentencias destructivas (solo se pide en conexiones de produccion).
     */
    public boolean isConfirmed() {
        return isConfirmed;
    }
}
