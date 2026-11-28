package modules.redis.application.changekey;

import java.util.Map;

import static modules.shared.application.Primitives.getBool;
import static modules.shared.application.Primitives.getTrimmedString;
import static modules.shared.application.Primitives.getUntrimmedString;

/**
 * Un cambio del navegador: operation (RedisKeyEnum.OP_*) sobre key; field, value, score, ttlSeconds, newKey e index
 * segun la operacion. Clave, campo y valor no se recortan: los espacios pueden ser parte de ellos.
 */
public final class ChangeKeyDto {

    private final String connectionUuid;
    private final String sessionKey;
    private final String key;
    private final String operation;
    private final String field;
    private final String value;
    private final String score;
    private final String ttlSeconds;
    private final String newKey;
    private final String index;
    private final boolean isConfirmed;

    private ChangeKeyDto(Map<String, Object> primitives) {
        this.connectionUuid = getTrimmedString(primitives.get("connectionUuid"));
        this.sessionKey = getTrimmedString(primitives.get("sessionKey"));
        this.key = getUntrimmedString(primitives.get("key"));
        this.operation = getTrimmedString(primitives.get("operation"));
        this.field = getUntrimmedString(primitives.get("field"));
        this.value = getUntrimmedString(primitives.get("value"));
        this.score = getTrimmedString(primitives.get("score"));
        this.ttlSeconds = getTrimmedString(primitives.get("ttlSeconds"));
        this.newKey = getUntrimmedString(primitives.get("newKey"));
        this.index = getTrimmedString(primitives.get("index"));
        this.isConfirmed = getBool(primitives.get("isConfirmed"));
    }

    public static ChangeKeyDto fromPrimitives(Map<String, Object> primitives) {
        return new ChangeKeyDto(primitives);
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

    public String getOperation() {
        return operation;
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

    public String getTtlSeconds() {
        return ttlSeconds;
    }

    public String getNewKey() {
        return newKey;
    }

    public String getIndex() {
        return index;
    }

    public boolean isConfirmed() {
        return isConfirmed;
    }
}
