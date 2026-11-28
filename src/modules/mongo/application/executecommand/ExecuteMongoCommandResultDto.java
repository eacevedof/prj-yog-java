package modules.mongo.application.executecommand;

import java.util.Map;

import static modules.shared.application.Primitives.getBool;

public final class ExecuteMongoCommandResultDto {

    private final String output;
    private final boolean isError;
    private final String currentDatabase;
    private final long elapsedMs;

    private ExecuteMongoCommandResultDto(Map<String, Object> primitives) {
        this.output = String.valueOf(primitives.get("output"));
        this.isError = getBool(primitives.get("isError"));
        this.currentDatabase = String.valueOf(primitives.get("currentDatabase"));
        this.elapsedMs = primitives.get("elapsedMs") instanceof Number number ? number.longValue() : 0;
    }

    public static ExecuteMongoCommandResultDto fromPrimitives(Map<String, Object> primitives) {
        return new ExecuteMongoCommandResultDto(primitives);
    }

    public String getOutput() {
        return output;
    }

    public boolean isError() {
        return isError;
    }

    /**
     * La BD activa tras la orden (use x la cambia).
     */
    public String getCurrentDatabase() {
        return currentDatabase;
    }

    public long getElapsedMs() {
        return elapsedMs;
    }
}
