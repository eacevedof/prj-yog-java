package modules.workspace.application.getworkspacestate;

import java.util.Map;

public final class GetWorkspaceStateResultDto {

    private final String stateValue;

    private GetWorkspaceStateResultDto(Map<String, Object> primitives) {
        Object value = primitives.get("stateValue");
        this.stateValue = value == null ? "" : String.valueOf(value);
    }

    public static GetWorkspaceStateResultDto fromPrimitives(Map<String, Object> primitives) {
        return new GetWorkspaceStateResultDto(primitives);
    }

    /**
     * "" si no se ha guardado nunca.
     */
    public String getStateValue() {
        return stateValue;
    }
}
