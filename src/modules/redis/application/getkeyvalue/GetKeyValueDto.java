package modules.redis.application.getkeyvalue;

import java.util.Map;

import static modules.shared.application.Primitives.getInt;
import static modules.shared.application.Primitives.getTrimmedString;
import static modules.shared.application.Primitives.getUntrimmedString;

import modules.redis.domain.enums.RedisConfigEnum;

public final class GetKeyValueDto {

    private final String connectionUuid;
    private final String sessionKey;
    private final String key;
    private final int limit;

    private GetKeyValueDto(Map<String, Object> primitives) {
        this.connectionUuid = getTrimmedString(primitives.get("connectionUuid"));
        this.sessionKey = getTrimmedString(primitives.get("sessionKey"));
        // la clave no se recorta: un espacio puede ser parte de ella
        this.key = getUntrimmedString(primitives.get("key"));
        int limitValue = getInt(primitives.get("limit"));
        this.limit = limitValue <= 0 ? RedisConfigEnum.KEY_VALUE_DEFAULT_LIMIT : limitValue;
    }

    public static GetKeyValueDto fromPrimitives(Map<String, Object> primitives) {
        return new GetKeyValueDto(primitives);
    }

    public String getConnectionUuid() {
        return connectionUuid;
    }

    public String getSessionKey() {
        return sessionKey;
    }

    public String getKey() {
        return key;
    }

    public int getLimit() {
        return limit;
    }
}
