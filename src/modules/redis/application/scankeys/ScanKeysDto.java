package modules.redis.application.scankeys;

import java.util.Map;

import static modules.shared.application.Primitives.getInt;
import static modules.shared.application.Primitives.getTrimmedString;

import modules.redis.domain.enums.RedisConfigEnum;

public final class ScanKeysDto {

    private final String connectionUuid;
    private final String sessionKey;
    private final String pattern;
    private final String cursor;
    private final int count;

    private ScanKeysDto(Map<String, Object> primitives) {
        this.connectionUuid = getTrimmedString(primitives.get("connectionUuid"));
        this.sessionKey = getTrimmedString(primitives.get("sessionKey"));
        String patternValue = getTrimmedString(primitives.get("pattern"));
        this.pattern = patternValue.isEmpty() ? RedisConfigEnum.SCAN_DEFAULT_PATTERN : patternValue;
        String cursorValue = getTrimmedString(primitives.get("cursor"));
        this.cursor = cursorValue.isEmpty() ? RedisConfigEnum.SCAN_FIRST_CURSOR : cursorValue;
        int countValue = getInt(primitives.get("count"));
        this.count = countValue <= 0 ? RedisConfigEnum.SCAN_DEFAULT_COUNT : countValue;
    }

    public static ScanKeysDto fromPrimitives(Map<String, Object> primitives) {
        return new ScanKeysDto(primitives);
    }

    public String getConnectionUuid() {
        return connectionUuid;
    }

    public String getSessionKey() {
        return sessionKey;
    }

    public String getPattern() {
        return pattern;
    }

    public String getCursor() {
        return cursor;
    }

    public int getCount() {
        return count;
    }
}
