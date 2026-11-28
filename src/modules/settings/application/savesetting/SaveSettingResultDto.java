package modules.settings.application.savesetting;

import java.util.Map;

/**
 * Sin datos de salida: la preferencia queda guardada.
 */
public final class SaveSettingResultDto {

    private SaveSettingResultDto(Map<String, Object> primitives) {
    }

    public static SaveSettingResultDto fromPrimitives(Map<String, Object> primitives) {
        return new SaveSettingResultDto(primitives);
    }
}
