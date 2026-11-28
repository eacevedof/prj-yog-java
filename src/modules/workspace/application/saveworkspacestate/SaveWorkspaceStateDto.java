package modules.workspace.application.saveworkspacestate;

import java.util.Map;

import static modules.shared.application.Primitives.getTrimmedString;

public final class SaveWorkspaceStateDto {

    private final String stateKey;
    private final String stateValue;

    private SaveWorkspaceStateDto(Map<String, Object> primitives) {
        this.stateKey = getTrimmedString(primitives.get("stateKey"));
        Object value = primitives.get("stateValue");
        this.stateValue = value == null ? "" : String.valueOf(value);
    }

    public static SaveWorkspaceStateDto fromPrimitives(Map<String, Object> primitives) {
        return new SaveWorkspaceStateDto(primitives);
    }

    public String getStateKey() {
        return stateKey;
    }

    /**
     * Tal cual llega (sin recortar).
     */
    public String getStateValue() {
        return stateValue;
    }
}
