package modules.shared.infrastructure;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import core.config.Translator;
import static core.config.Global.__;

import testcase.AbstractTestCase;

import modules.shared.domain.enums.LanguageEnum;

/**
 * La interfaz multi-idioma: cada texto de los FXML (los text="%..." y promptText="%...") tiene traduccion en ingles,
 * neerlandes y papiamento; los placeholders se conservan; un idioma desconocido cae en ingles.
 */
final class TranslationsTest extends AbstractTestCase {

    private static final Pattern FXML_KEY_PATTERN = Pattern.compile("(?:text|promptText)=\"%([^\"]*)\"");
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{\\d+\\}");

    @AfterEach
    void restoreLanguage() {
        Translator.getInstance().reset();
    }

    @Test
    void testEveryFxmlTextIsTranslatedToEnglishDutchAndPapiamento() throws IOException {
        Set<String> fxmlKeys = getFxmlKeys();
        for (String language : List.of(LanguageEnum.EN, LanguageEnum.NL, LanguageEnum.PAP)) {
            Translator.getInstance().setLanguage(language);
            List<String> untranslated = new ArrayList<>();
            for (String fxmlKey : fxmlKeys) {
                if (!Translator.getInstance().hasTranslation(fxmlKey) && !isLanguageNeutral(fxmlKey)) untranslated.add(fxmlKey);
            }
            assertEquals(language + ": " + List.of(), language + ": " + untranslated);
        }
    }

    @Test
    void testPlaceholdersSurviveAndUnknownLanguageFallsBackToEnglish() {
        Translator.getInstance().setLanguage("xx");
        assertEquals(LanguageEnum.EN, Translator.getInstance().getLanguage());

        for (String language : LanguageEnum.VALUES) {
            Translator.getInstance().setLanguage(language);
            String translated = __("Cambiar el idioma cierra las conexiones abiertas. ¿Seguir?");
            assertFalse(translated.isBlank());
        }
        Translator.getInstance().setLanguage(LanguageEnum.EN);
        assertEquals("Connect", __("Conectar"));
    }

    @Test
    void testEveryPoEntryKeepsItsPlaceholders() throws IOException {
        List<String> broken = new ArrayList<>();
        try (Stream<Path> poFiles = Files.walk(Path.of("src/modules"))) {
            for (Path poFile : poFiles.filter(path -> path.toString().endsWith(".po")).toList()) {
                String msgId = null;
                for (String line : Files.readAllLines(poFile, StandardCharsets.UTF_8)) {
                    String trimmed = line.trim();
                    if (trimmed.startsWith("msgid ")) msgId = trimmed.substring(6);
                    else if (trimmed.startsWith("msgstr ") && msgId != null) {
                        if (!getPlaceholders(msgId).equals(getPlaceholders(trimmed.substring(7)))) broken.add(poFile.getFileName() + ": " + msgId);
                        msgId = null;
                    }
                }
            }
        }
        assertEquals(List.of(), broken);
    }

    private Set<String> getFxmlKeys() throws IOException {
        Set<String> fxmlKeys = new TreeSet<>();
        try (Stream<Path> fxmlFiles = Files.walk(Path.of("src/modules"))) {
            for (Path fxmlFile : fxmlFiles.filter(path -> path.toString().endsWith(".fxml")).toList()) {
                Matcher matcher = FXML_KEY_PATTERN.matcher(Files.readString(fxmlFile, StandardCharsets.UTF_8));
                while (matcher.find()) {
                    fxmlKeys.add(matcher.group(1).replace("&gt;", ">").replace("&lt;", "<").replace("&quot;", "\"").replace("&amp;", "&"));
                }
            }
        }
        return fxmlKeys;
    }

    /**
     * Textos que son iguales en todos los idiomas (SQL, nombres propios, siglas): no cuentan como sin traducir.
     */
    private boolean isLanguageNeutral(String fxmlKey) {
        return fxmlKey.matches("[A-Z0-9 _.:/()&*+,=<>'%-]+") || fxmlKey.matches("(?i)(SSH|HTTP|TLS|SSL|MySQL|Redis|MongoDB|PostgreSQL|SQLite|JSON|Host|Hash|Set|ZSet|Stream|TTL|Login).*") && fxmlKey.length() < 14;
    }

    private Set<String> getPlaceholders(String text) {
        Set<String> placeholders = new TreeSet<>();
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(text);
        while (matcher.find()) placeholders.add(matcher.group());
        return placeholders;
    }
}
