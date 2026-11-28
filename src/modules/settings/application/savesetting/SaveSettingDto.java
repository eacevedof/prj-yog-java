package modules.settings.application.savesetting;

import java.util.Map;

import static modules.shared.application.Primitives.getTrimmedString;

public final class SaveSettingDto {

    private final String settingKey;
    private final String settingValue;

    private SaveSettingDto(Map<String, Object> primitives) {
        this.settingKey = getTrimmedString(primitives.get("settingKey"));
        this.settingValue = getTrimmedString(primitives.get("settingValue"));
    }

    public static SaveSettingDto fromPrimitives(Map<String, Object> primitives) {
        return new SaveSettingDto(primitives);
    }

    public String getSettingKey() {
        return settingKey;
    }

    public String getSettingValue() {
        return settingValue;
    }
}
