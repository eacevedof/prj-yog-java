package core.boot;

import javafx.application.Application;

import modules.shared.application.Primitives;
import modules.shared.infrastructure.components.theme.ThemeComponent;

import modules.devops.application.runmigrations.RunMigrationsDto;
import modules.devops.application.runmigrations.RunMigrationsService;

import modules.settings.domain.enums.SettingsKeyEnum;
import modules.settings.application.getsetting.GetSettingDto;
import modules.settings.application.getsetting.GetSettingService;

import modules.connections.application.encryptstoredsecrets.EncryptStoredSecretsDto;
import modules.connections.application.encryptstoredsecrets.EncryptStoredSecretsService;

import core.config.Translator;

/**
 * Arranque: primero la BD local al dia (migraciones), luego la ventana. Si las migraciones fallan la app no abre:
 * sin BD no hay conexiones que mostrar.
 */
public final class DesktopMain {

    private DesktopMain() {
    }

    public static DesktopMain getInstance() {
        return new DesktopMain();
    }

    public void run(String[] args) {
        RunMigrationsService.getInstance().invoke(RunMigrationsDto.fromPrimitives(Primitives.of()));
        // feature 47: las passwords que quedaran en claro de versiones anteriores se cifran ya
        EncryptStoredSecretsService.getInstance().invoke(EncryptStoredSecretsDto.fromPrimitives(Primitives.of()));
        loadTheme();
        loadLanguage();
        Application.launch(DesktopApplication.class, args);
    }

    /**
     * El idioma guardado; sin preferencia, el del sistema (o ingles si la app no lo tiene).
     */
    private void loadLanguage() {
        String language = GetSettingService.getInstance().invoke(
            GetSettingDto.fromPrimitives(Primitives.of("settingKey", SettingsKeyEnum.LANGUAGE))
        ).getSettingValue();
        Translator.getInstance().setLanguage(SettingsKeyEnum.LANGUAGE_SYSTEM.equals(language) ? Translator.getSystemLanguage() : language);
    }

    /**
     * El tema guardado (feature 37), antes de crear ninguna ventana.
     */
    private void loadTheme() {
        String theme = GetSettingService.getInstance().invoke(
            GetSettingDto.fromPrimitives(Primitives.of("settingKey", SettingsKeyEnum.THEME))
        ).getSettingValue();
        ThemeComponent.getInstance().setDark(SettingsKeyEnum.THEME_DARK.equals(theme));
    }
}
