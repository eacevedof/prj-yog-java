package modules.workspace.application.savequerytabs;

import java.util.Map;

/**
 * Sin datos de salida: las pestañas quedan guardadas.
 */
public final class SaveQueryTabsResultDto {

    private SaveQueryTabsResultDto(Map<String, Object> primitives) {
    }

    public static SaveQueryTabsResultDto fromPrimitives(Map<String, Object> primitives) {
        return new SaveQueryTabsResultDto(primitives);
    }
}
