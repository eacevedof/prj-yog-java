package modules.dbcopy.application.copydatabase;

import java.util.List;
import java.util.Map;

import static modules.shared.application.Primitives.getBool;
import static modules.shared.application.Primitives.getInt;

/**
 * El resumen de la copia: una entrada por objeto (tipo, nombre, si fue bien, filas copiadas y el error si lo hubo) y
 * los totales que calcula el servicio.
 */
public final class CopyDatabaseResultDto {

    private final List<Map<String, Object>> objects;
    private final int okCount;
    private final int errorCount;
    private final long copiedRows;
    private final boolean isCancelled;
    private final long elapsedMs;

    @SuppressWarnings("unchecked")
    private CopyDatabaseResultDto(Map<String, Object> primitives) {
        Object rawObjects = primitives.get("objects");
        this.objects = rawObjects == null ? List.of() : List.copyOf((List<Map<String, Object>>) rawObjects);
        this.okCount = getInt(primitives.get("okCount"));
        this.errorCount = getInt(primitives.get("errorCount"));
        this.copiedRows = primitives.get("copiedRows") instanceof Number number ? number.longValue() : 0;
        this.isCancelled = getBool(primitives.get("isCancelled"));
        this.elapsedMs = primitives.get("elapsedMs") instanceof Number number ? number.longValue() : 0;
    }

    public static CopyDatabaseResultDto fromPrimitives(Map<String, Object> primitives) {
        return new CopyDatabaseResultDto(primitives);
    }

    /**
     * [object_type, object_name, is_ok, copied_rows, message] por objeto, en el orden en que se copiaron.
     */
    public List<Map<String, Object>> getObjects() {
        return objects;
    }

    public int getOkCount() {
        return okCount;
    }

    public int getErrorCount() {
        return errorCount;
    }

    public long getCopiedRows() {
        return copiedRows;
    }

    public boolean isCancelled() {
        return isCancelled;
    }

    public long getElapsedMs() {
        return elapsedMs;
    }
}
