package modules.settings.application.getsetting;

import java.util.Map;

import static modules.shared.application.Primitives.getTrimmedString;

public final class GetSettingResultDto {

    private final String settingValue;

    private GetSettingResultDto(Map<String, Object> primitives) {
        this.settingValue = getTrimmedString(primitives.get("settingValue"));
    }

    public static GetSettingResultDto fromPrimitives(Map<String, Object> primitives) {
        return new GetSettingResultDto(primitives);
    }

    public String getSettingValue() {
        return settingValue;
    }
}
