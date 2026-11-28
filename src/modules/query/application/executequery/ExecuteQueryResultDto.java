package modules.query.application.executequery;

import java.util.List;
import java.util.Map;

import static modules.shared.application.Primitives.getBool;
import static modules.shared.application.Primitives.getTrimmedString;

public final class ExecuteQueryResultDto {

    private final List<Map<String, Object>> results;
    private final String currentDatabase;
    private final boolean isCancelled;
    private final long elapsedMs;

    @SuppressWarnings("unchecked")
    private ExecuteQueryResultDto(Map<String, Object> primitives) {
        this.results = List.copyOf((List<Map<String, Object>>) primitives.get("results"));
        this.currentDatabase = getTrimmedString(primitives.get("currentDatabase"));
        this.isCancelled = getBool(primitives.get("isCancelled"));
        Object elapsed = primitives.get("elapsedMs");
        this.elapsedMs = elapsed instanceof Number number ? number.longValue() : 0;
    }

    public static ExecuteQueryResultDto fromPrimitives(Map<String, Object> primitives) {
        return new ExecuteQueryResultDto(primitives);
    }

    /**
     * Un resultado por result set o recuento de filas, con las claves de QueryKeyEnum. Si una sentencia falla, es el
     * ultimo y lleva error_message.
     */
    public List<Map<String, Object>> getResults() {
        return results;
    }

    /**
     * La BD activa tras ejecutar (un USE la cambia): la UI la refleja.
     */
    public String getCurrentDatabase() {
        return currentDatabase;
    }

    public boolean isCancelled() {
        return isCancelled;
    }

    public long getElapsedMs() {
        return elapsedMs;
    }
}
