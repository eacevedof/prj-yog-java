package modules.settings.application.savesetting;

import java.util.List;

import modules.shared.application.Primitives;
import modules.shared.infrastructure.components.date.DateTimer;

import static core.config.Global.__;

import modules.settings.domain.enums.SettingsKeyEnum;
import modules.settings.domain.enums.SettingsMessageEnum;
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
    public SaveSettingResultDto invoke(SaveSettingDto saveSettingDto) {
        failIfWrongInput(saveSettingDto);

        SettingsSqliteRepository.getInstance().upsertSetting(
            saveSettingDto.getSettingKey(), saveSettingDto.getSettingValue(), DateTimer.getInstance().getNowDt()
        );
        return SaveSettingResultDto.fromPrimitives(Primitives.of());
    }

    /**
     * @throws SettingsException
     */
    private void failIfWrongInput(SaveSettingDto saveSettingDto) {
        List<String> allowedValues = SettingsKeyEnum.ALLOWED_VALUES_BY_KEY.get(saveSettingDto.getSettingKey());
        if (allowedValues == null) {
            throw SettingsException.badRequestCustom(__(SettingsMessageEnum.UNKNOWN_SETTING_X, saveSettingDto.getSettingKey()));
        }
        if (!allowedValues.contains(saveSettingDto.getSettingValue())) {
            throw SettingsException.badRequestCustom(__(
                SettingsMessageEnum.INVALID_VALUE_X_FOR_X_ALLOWED_X, saveSettingDto.getSettingValue(), saveSettingDto.getSettingKey(), allowedValues
            ));
        }
    }
}
