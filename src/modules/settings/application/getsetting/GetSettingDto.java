package modules.settings.application.getsetting;

import java.util.Map;

import static modules.shared.application.Primitives.getTrimmedString;

public final class GetSettingDto {

    private final String settingKey;

    private GetSettingDto(Map<String, Object> primitives) {
        this.settingKey = getTrimmedString(primitives.get("settingKey"));
    }

    public static GetSettingDto fromPrimitives(Map<String, Object> primitives) {
        return new GetSettingDto(primitives);
    }

    public String getSettingKey() {
        return settingKey;
    }
}
