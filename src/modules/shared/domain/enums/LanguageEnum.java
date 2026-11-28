package modules.shared.domain.enums;

import java.util.List;
import java.util.Map;

/**
 * Los idiomas de la interfaz. Sin preferencia guardada se usa el del sistema operativo si esta aqui; si no, ingles.
 * Papiamento: codigo ISO 639-2 "pap" (Curazao, Aruba, Bonaire).
 */
public final class LanguageEnum {

    public static final String ES = "es";
    public static final String EN = "en";
    public static final String NL = "nl";
    public static final String PAP = "pap";

    public static final String DEFAULT = EN;
    public static final List<String> VALUES = List.of(EN, ES, NL, PAP);

    // el nombre de cada idioma en su propia lengua (asi se reconoce en el selector aunque no se entienda el actual)
    public static final Map<String, String> NATIVE_NAMES = Map.of(
        EN, "English",
        ES, "Español",
        NL, "Nederlands",
        PAP, "Papiamentu"
    );

    private LanguageEnum() {
    }
}
