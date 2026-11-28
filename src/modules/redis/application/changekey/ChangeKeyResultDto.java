package modules.redis.application.changekey;

import java.util.Map;

public final class ChangeKeyResultDto {

    private final long affected;

    private ChangeKeyResultDto(Map<String, Object> primitives) {
        this.affected = primitives.get("affected") instanceof Number number ? number.longValue() : 0;
    }

    public static ChangeKeyResultDto fromPrimitives(Map<String, Object> primitives) {
        return new ChangeKeyResultDto(primitives);
    }

    /**
     * Lo que devolvio Redis: claves borradas, elementos añadidos o quitados (0 = no habia nada que hacer).
     */
    public long getAffected() {
        return affected;
    }
}
