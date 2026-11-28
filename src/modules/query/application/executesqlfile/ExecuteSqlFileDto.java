package modules.query.application.executesqlfile;

import java.util.Map;

import static modules.shared.application.Primitives.getBool;
import static modules.shared.application.Primitives.getTrimmedString;

/**
 * "Execute Query(s) From A File" de SQLyog: el fichero, la conexion, la sesion propia de la ejecucion (su clave sirve
 * tambien para el progreso y para Parar), la BD activa, si para en el primer error y si ya se confirmo produccion.
 */
public final class ExecuteSqlFileDto {

    private final String connectionUuid;
    private final String sessionKey;
    private final String databaseName;
    private final String filePath;
    private final boolean isAbortOnError;
    private final boolean isConfirmed;

    private ExecuteSqlFileDto(Map<String, Object> primitives) {
        this.connectionUuid = getTrimmedString(primitives.get("connectionUuid"));
        this.sessionKey = getTrimmedString(primitives.get("sessionKey"));
        this.databaseName = getTrimmedString(primitives.get("databaseName"));
        this.filePath = getTrimmedString(primitives.get("filePath"));
        this.isAbortOnError = getBool(primitives.get("isAbortOnError"));
        this.isConfirmed = getBool(primitives.get("isConfirmed"));
    }

    public static ExecuteSqlFileDto fromPrimitives(Map<String, Object> primitives) {
        return new ExecuteSqlFileDto(primitives);
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

    public String getFilePath() {
        return filePath;
    }

    public boolean isAbortOnError() {
        return isAbortOnError;
    }

    public boolean isConfirmed() {
        return isConfirmed;
    }
}
