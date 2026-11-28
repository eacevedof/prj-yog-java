package modules.redis.application.createkey;

import java.util.Map;

public final class CreateKeyResultDto {

    private final String key;

    private CreateKeyResultDto(Map<String, Object> primitives) {
        Object keyValue = primitives.get("key");
        this.key = keyValue == null ? "" : String.valueOf(keyValue);
    }

    public static CreateKeyResultDto fromPrimitives(Map<String, Object> primitives) {
        return new CreateKeyResultDto(primitives);
    }

    /**
     * La clave creada, tal cual (sin recortar), para elegirla en el arbol.
     */
    public String getKey() {
        return key;
    }
}
