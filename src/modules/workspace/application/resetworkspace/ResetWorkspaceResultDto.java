package modules.workspace.application.resetworkspace;

import java.util.Map;

import static modules.shared.application.Primitives.getInt;

public final class ResetWorkspaceResultDto {

    private final int deletedStates;
    private final int deletedQueryTabs;

    private ResetWorkspaceResultDto(Map<String, Object> primitives) {
        this.deletedStates = getInt(primitives.get("deletedStates"));
        this.deletedQueryTabs = getInt(primitives.get("deletedQueryTabs"));
    }

    public static ResetWorkspaceResultDto fromPrimitives(Map<String, Object> primitives) {
        return new ResetWorkspaceResultDto(primitives);
    }

    public int getDeletedStates() {
        return deletedStates;
    }

    public int getDeletedQueryTabs() {
        return deletedQueryTabs;
    }
}
