package modules.workspace.application.getworkspacestate;

import java.util.Map;

import static modules.shared.application.Primitives.getTrimmedString;

public final class GetWorkspaceStateDto {

    private final String stateKey;

    private GetWorkspaceStateDto(Map<String, Object> primitives) {
        this.stateKey = getTrimmedString(primitives.get("stateKey"));
    }

    public static GetWorkspaceStateDto fromPrimitives(Map<String, Object> primitives) {
        return new GetWorkspaceStateDto(primitives);
    }

    public String getStateKey() {
        return stateKey;
    }
}
