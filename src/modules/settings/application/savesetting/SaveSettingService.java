package modules.settings.application.savesetting;

import java.util.List;

import modules.shared.infrastructure.components.date.DateTimer;

import static core.config.Global.__;

import modules.settings.domain.enums.SettingsKeyEnum;
import modules.settings.infrastructure.repositories.SettingsSqliteRepository;
import modules.settings.domain.exceptions.SettingsException;

/**
 * Guarda una preferencia: solo claves y valores de SettingsKeyEnum.
 */
public final class SaveSettingService {

    private SaveSettingService() {
    }

    public static SaveSettingService getInstance() {
        return new SaveSettingService();
    }

    /**
     * @throws SettingsException
     */
    public void invoke(SaveSettingDto saveSettingDto) {
        List<String> allowedValues = SettingsKeyEnum.ALLOWED_VALUES_BY_KEY.get(saveSettingDto.getSettingKey());
        if (allowedValues == null) {
            throw SettingsException.badRequestCustom(__("Unknown setting {0}", saveSettingDto.getSettingKey()));
        }
        if (!allowedValues.contains(saveSettingDto.getSettingValue())) {
            throw SettingsException.badRequestCustom(
                __("Invalid value {0} for {1}, allowed: {2}", saveSettingDto.getSettingValue(), saveSettingDto.getSettingKey(), allowedValues)
            );
        }
        SettingsSqliteRepository.getInstance().upsertSetting(
            saveSettingDto.getSettingKey(), saveSettingDto.getSettingValue(), DateTimer.getInstance().getNowDt()
        );
    }
}
