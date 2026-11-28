package modules.mongo.application.executecommand;

import java.util.Map;

import static modules.shared.application.Primitives.getBool;
import static modules.shared.application.Primitives.getTrimmedString;

public final class ExecuteMongoCommandDto {

    private final String connectionUuid;
    private final String sessionKey;
    private final String databaseName;
    private final String commandLine;
    private final boolean isConfirmed;

    private ExecuteMongoCommandDto(Map<String, Object> primitives) {
        this.connectionUuid = getTrimmedString(primitives.get("connectionUuid"));
        this.sessionKey = getTrimmedString(primitives.get("sessionKey"));
        this.databaseName = getTrimmedString(primitives.get("databaseName"));
        this.commandLine = getTrimmedString(primitives.get("commandLine"));
        this.isConfirmed = getBool(primitives.get("isConfirmed"));
    }

    public static ExecuteMongoCommandDto fromPrimitives(Map<String, Object> primitives) {
        return new ExecuteMongoCommandDto(primitives);
    }

    public String getConnectionUuid() {
        return connectionUuid;
    }

    public String getSessionKey() {
        return sessionKey;
    }

    /**
     * La BD activa de la consola ("db" en mongosh); use x la cambia.
     */
    public String getDatabaseName() {
        return databaseName;
    }

    public String getCommandLine() {
        return commandLine;
    }

    public boolean isConfirmed() {
        return isConfirmed;
    }
}
