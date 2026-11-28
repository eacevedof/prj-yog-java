package modules.settings.application.getsetting;

import java.util.List;

import modules.shared.application.Primitives;

import static core.config.Global.__;

import modules.settings.domain.enums.SettingsKeyEnum;
import modules.settings.domain.enums.SettingsMessageEnum;
import modules.settings.infrastructure.repositories.SettingsSqliteRepository;
import modules.settings.domain.exceptions.SettingsException;

/**
 * El valor de una preferencia; si no se ha guardado o ya no es valido, su valor por defecto.
 */
public final class GetSettingService {

    private GetSettingService() {
    }

    public static GetSettingService getInstance() {
        return new GetSettingService();
    }

    /**
     * @throws SettingsException
     */
    public GetSettingResultDto invoke(GetSettingDto getSettingDto) {
        failIfWrongInput(getSettingDto);

        List<String> allowedValues = SettingsKeyEnum.ALLOWED_VALUES_BY_KEY.get(getSettingDto.getSettingKey());
        String savedValue = SettingsSqliteRepository.getInstance().getSettingValue(getSettingDto.getSettingKey());
        // List.of(...).contains(null) lanza NPE: sin valor guardado se va directo al de por defecto
        String settingValue = savedValue != null && allowedValues.contains(savedValue) ? savedValue : allowedValues.getFirst();
        return GetSettingResultDto.fromPrimitives(Primitives.of("settingValue", settingValue));
    }

    /**
     * @throws SettingsException
     */
    private void failIfWrongInput(GetSettingDto getSettingDto) {
        if (!SettingsKeyEnum.ALLOWED_VALUES_BY_KEY.containsKey(getSettingDto.getSettingKey())) {
            throw SettingsException.badRequestCustom(__(SettingsMessageEnum.UNKNOWN_SETTING_X, getSettingDto.getSettingKey()));
        }
    }
}
