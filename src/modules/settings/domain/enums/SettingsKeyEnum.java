package modules.settings.domain.enums;

import java.util.List;
import java.util.Map;

import modules.shared.domain.enums.LanguageEnum;

/**
 * Las preferencias que existen y sus valores permitidos (con el primero como valor por defecto).
 */
public final class SettingsKeyEnum {

    public static final String THEME = "theme";
    public static final String THEME_LIGHT = "light";
    public static final String THEME_DARK = "dark";

    // idioma de la interfaz: "system" (el del sistema operativo, o ingles si la app no lo tiene) o uno de LanguageEnum
    public static final String LANGUAGE = "language";
    public static final String LANGUAGE_SYSTEM = "system";

    public static final Map<String, List<String>> ALLOWED_VALUES_BY_KEY = Map.of(
        THEME, List.of(THEME_LIGHT, THEME_DARK),
        LANGUAGE, List.of(LANGUAGE_SYSTEM, LanguageEnum.EN, LanguageEnum.ES, LanguageEnum.NL, LanguageEnum.PAP)
    );

    private SettingsKeyEnum() {
    }
}
