package modules.workspace.application.saveworkspacestate;

import java.util.Map;

/**
 * Sin datos de salida: el dato queda guardado.
 */
public final class SaveWorkspaceStateResultDto {

    private SaveWorkspaceStateResultDto(Map<String, Object> primitives) {
    }

    public static SaveWorkspaceStateResultDto fromPrimitives(Map<String, Object> primitives) {
        return new SaveWorkspaceStateResultDto(primitives);
    }
}
