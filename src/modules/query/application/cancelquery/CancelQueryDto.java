package modules.query.application.cancelquery;

import java.util.Map;

import static modules.shared.application.Primitives.getTrimmedString;

public final class CancelQueryDto {

    private final String sessionKey;

    private CancelQueryDto(Map<String, Object> primitives) {
        this.sessionKey = getTrimmedString(primitives.get("sessionKey"));
    }

    public static CancelQueryDto fromPrimitives(Map<String, Object> primitives) {
        return new CancelQueryDto(primitives);
    }

    public String getSessionKey() {
        return sessionKey;
    }
}
