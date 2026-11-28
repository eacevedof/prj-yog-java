package modules.redis.application.createkey;

import java.util.Map;

import static modules.shared.application.Primitives.getTrimmedString;
import static modules.shared.application.Primitives.getUntrimmedString;

/**
 * Una clave nueva del navegador: su tipo (RedisKeyEnum.TYPE_*) y el primer elemento (campo en un hash, score en un
 * zset). Clave, campo y valor no se recortan: los espacios pueden ser parte de ellos.
 */
public final class CreateKeyDto {

    private final String connectionUuid;
    private final String sessionKey;
    private final String key;
    private final String type;
    private final String field;
    private final String value;
    private final String score;
    private final String ttlSeconds;

    private CreateKeyDto(Map<String, Object> primitives) {
        this.connectionUuid = getTrimmedString(primitives.get("connectionUuid"));
        this.sessionKey = getTrimmedString(primitives.get("sessionKey"));
        this.key = getUntrimmedString(primitives.get("key"));
        this.type = getTrimmedString(primitives.get("type"));
        this.field = getUntrimmedString(primitives.get("field"));
        this.value = getUntrimmedString(primitives.get("value"));
        this.score = getTrimmedString(primitives.get("score"));
        this.ttlSeconds = getTrimmedString(primitives.get("ttlSeconds"));
    }

    public static CreateKeyDto fromPrimitives(Map<String, Object> primitives) {
        return new CreateKeyDto(primitives);
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

    public String getType() {
        return type;
    }

    public String getField() {
        return field;
    }

    public String getValue() {
        return value;
    }

    public String getScore() {
        return score;
    }

    /**
     * Vacio = no caduca.
     */
    public String getTtlSeconds() {
        return ttlSeconds;
    }
}
