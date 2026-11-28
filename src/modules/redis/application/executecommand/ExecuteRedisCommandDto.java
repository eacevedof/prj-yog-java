package modules.redis.application.executecommand;

import java.util.Map;

import static modules.shared.application.Primitives.getBool;
import static modules.shared.application.Primitives.getTrimmedString;

public final class ExecuteRedisCommandDto {

    private final String connectionUuid;
    private final String sessionKey;
    private final String commandLine;
    private final boolean isConfirmed;

    private ExecuteRedisCommandDto(Map<String, Object> primitives) {
        this.connectionUuid = getTrimmedString(primitives.get("connectionUuid"));
        this.sessionKey = getTrimmedString(primitives.get("sessionKey"));
        this.commandLine = getTrimmedString(primitives.get("commandLine"));
        this.isConfirmed = getBool(primitives.get("isConfirmed"));
    }

    public static ExecuteRedisCommandDto fromPrimitives(Map<String, Object> primitives) {
        return new ExecuteRedisCommandDto(primitives);
    }

    public String getConnectionUuid() {
        return connectionUuid;
    }

    public String getSessionKey() {
        return sessionKey;
    }

    public String getCommandLine() {
        return commandLine;
    }

    public boolean isConfirmed() {
        return isConfirmed;
    }
}
