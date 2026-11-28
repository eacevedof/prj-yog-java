package modules.query.application.executesqlfile;

import java.util.List;
import java.util.Map;

import static modules.shared.application.Primitives.getBool;

/**
 * Lo que paso al ejecutar el fichero: sentencias ejecutadas y con error, los primeros errores ("sql" recortado y
 * "message"), si se paro (Parar) o se aborto en un error, y el tiempo.
 */
public final class ExecuteSqlFileResultDto {

    private final long executedCount;
    private final long errorCount;
    private final List<Map<String, Object>> errors;
    private final boolean isCancelled;
    private final boolean isAborted;
    private final long elapsedMs;

    @SuppressWarnings("unchecked")
    private ExecuteSqlFileResultDto(Map<String, Object> primitives) {
        this.executedCount = ((Number) primitives.getOrDefault("executedCount", 0L)).longValue();
        this.errorCount = ((Number) primitives.getOrDefault("errorCount", 0L)).longValue();
        this.errors = primitives.get("errors") instanceof List<?> rawErrors ? List.copyOf((List<Map<String, Object>>) rawErrors) : List.of();
        this.isCancelled = getBool(primitives.get("isCancelled"));
        this.isAborted = getBool(primitives.get("isAborted"));
        this.elapsedMs = ((Number) primitives.getOrDefault("elapsedMs", 0L)).longValue();
    }

    public static ExecuteSqlFileResultDto fromPrimitives(Map<String, Object> primitives) {
        return new ExecuteSqlFileResultDto(primitives);
    }

    public long getExecutedCount() {
        return executedCount;
    }

    public long getErrorCount() {
        return errorCount;
    }

    public List<Map<String, Object>> getErrors() {
        return errors;
    }

    public boolean isCancelled() {
        return isCancelled;
    }

    public boolean isAborted() {
        return isAborted;
    }

    public long getElapsedMs() {
        return elapsedMs;
    }
}
