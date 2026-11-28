package modules.redis.application.scankeys;

import java.util.List;
import java.util.Map;

import static modules.shared.application.Primitives.getBool;
import static modules.shared.application.Primitives.getTrimmedString;

public final class ScanKeysResultDto {

    private final List<Map<String, Object>> keys;
    private final String nextCursor;
    private final boolean isComplete;

    @SuppressWarnings("unchecked")
    private ScanKeysResultDto(Map<String, Object> primitives) {
        this.keys = List.copyOf((List<Map<String, Object>>) primitives.get("keys"));
        this.nextCursor = getTrimmedString(primitives.get("nextCursor"));
        this.isComplete = getBool(primitives.get("isComplete"));
    }

    public static ScanKeysResultDto fromPrimitives(Map<String, Object> primitives) {
        return new ScanKeysResultDto(primitives);
    }

    /**
     * Cada clave con key, type y ttl_ms (RedisKeyEnum).
     */
    public List<Map<String, Object>> getKeys() {
        return keys;
    }

    public String getNextCursor() {
        return nextCursor;
    }

    /**
     * SCAN termino la vuelta completa: no hay mas claves que pedir.
     */
    public boolean isComplete() {
        return isComplete;
    }
}
