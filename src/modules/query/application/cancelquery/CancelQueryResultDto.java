package modules.query.application.cancelquery;

import java.util.Map;

import static modules.shared.application.Primitives.getBool;

public final class CancelQueryResultDto {

    private final boolean wasRunning;

    private CancelQueryResultDto(Map<String, Object> primitives) {
        this.wasRunning = getBool(primitives.get("wasRunning"));
    }

    public static CancelQueryResultDto fromPrimitives(Map<String, Object> primitives) {
        return new CancelQueryResultDto(primitives);
    }

    /**
     * false: no habia ninguna sentencia en el servidor (entre dos sentencias de "ejecutar todo", o ya habia acabado).
     */
    public boolean wasRunning() {
        return wasRunning;
    }
}
