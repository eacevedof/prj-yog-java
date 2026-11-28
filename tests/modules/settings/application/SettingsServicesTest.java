package modules.settings.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import modules.shared.application.Primitives;

import testcase.AbstractTestCase;

import modules.settings.domain.enums.SettingsKeyEnum;
import modules.settings.application.getsetting.GetSettingDto;
import modules.settings.application.savesetting.SaveSettingDto;
import modules.settings.application.getsetting.GetSettingService;
import modules.settings.application.savesetting.SaveSettingService;
import modules.settings.domain.exceptions.SettingsException;

/**
 * Las preferencias de la app (el tema, feature 37).
 */
final class SettingsServicesTest extends AbstractTestCase {

    @BeforeEach
    void bootDatabase() {
        bootFreshDatabase("SettingsServicesTest");
    }

    @Test
    void testThemeIsLightUntilSaved() {
        assertEquals(SettingsKeyEnum.THEME_LIGHT, getTheme());

        saveTheme(SettingsKeyEnum.THEME_DARK);
        assertEquals(SettingsKeyEnum.THEME_DARK, getTheme());

        saveTheme(SettingsKeyEnum.THEME_LIGHT);
        assertEquals(SettingsKeyEnum.THEME_LIGHT, getTheme());
    }

    @Test
    void testRejectsUnknownKeysAndValues() {
        assertThrows(SettingsException.class, () -> saveTheme("purple"));
        assertThrows(SettingsException.class, () -> SaveSettingService.getInstance().invoke(
            SaveSettingDto.fromPrimitives(Primitives.of("settingKey", "font", "settingValue", "x"))
        ));
    }

    private String getTheme() {
        return GetSettingService.getInstance().invoke(
            GetSettingDto.fromPrimitives(Primitives.of("settingKey", SettingsKeyEnum.THEME))
        ).getSettingValue();
    }

    private void saveTheme(String theme) {
        SaveSettingService.getInstance().invoke(
            SaveSettingDto.fromPrimitives(Primitives.of("settingKey", SettingsKeyEnum.THEME, "settingValue", theme))
        );
    }
}
