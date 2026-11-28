package modules.workspace.application.resetworkspace;

import java.util.Map;

/**
 * Sin datos de entrada: se borra todo lo que recuerda la ventana.
 */
public final class ResetWorkspaceDto {

    private ResetWorkspaceDto(Map<String, Object> primitives) {
    }

    public static ResetWorkspaceDto fromPrimitives(Map<String, Object> primitives) {
        return new ResetWorkspaceDto(primitives);
    }
}
