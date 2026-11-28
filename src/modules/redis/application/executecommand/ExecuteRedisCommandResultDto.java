package modules.redis.application.executecommand;

import java.util.Map;

import static modules.shared.application.Primitives.getBool;

public final class ExecuteRedisCommandResultDto {

    private final String output;
    private final boolean isError;
    private final long elapsedMs;
    private final long currentDatabase;

    private ExecuteRedisCommandResultDto(Map<String, Object> primitives) {
        this.output = String.valueOf(primitives.get("output"));
        this.isError = getBool(primitives.get("isError"));
        this.elapsedMs = primitives.get("elapsedMs") instanceof Number number ? number.longValue() : 0;
        this.currentDatabase = primitives.get("currentDatabase") instanceof Number number ? number.longValue() : 0;
    }

    public static ExecuteRedisCommandResultDto fromPrimitives(Map<String, Object> primitives) {
        return new ExecuteRedisCommandResultDto(primitives);
    }

    /**
     * La respuesta como la ensena redis-cli; con error, "(error) ..." como el.
     */
    public String getOutput() {
        return output;
    }

    public boolean isError() {
        return isError;
    }

    public long getElapsedMs() {
        return elapsedMs;
    }

    /**
     * La BD de la sesion de la consola tras el comando (SELECT n la cambia).
     */
    public long getCurrentDatabase() {
        return currentDatabase;
    }
}
