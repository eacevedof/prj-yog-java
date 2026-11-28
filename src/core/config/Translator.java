package core.config;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import modules.shared.domain.enums.LanguageEnum;

/**
 * Las traducciones de la interfaz. Estilo gettext: la clave es el texto tal como esta escrito en el codigo o en el FXML
 * (en español la interfaz, en ingles los mensajes de dominio) y cada idioma trae su "<lang>.po" por modulo en
 * /modules/<modulo>/infrastructure/i18n/. Un texto sin traduccion se queda como esta.
 * <p>
 * Formato .po (subconjunto): pares {@code msgid "..."} / {@code msgstr "..."}, comentarios con #, escapes \" \\ \n \t.
 */
public final class Translator {

    private static final List<String> MODULES = List.of(
        "connections", "dbcopy", "explorer", "files", "mongo", "query", "redis", "settings", "shared", "workspace"
    );
    private static final String PATH_PO = "/modules/%s/infrastructure/i18n/%s.po";
    private static final Translator INSTANCE = new Translator();
    private static final Locale ORIGINAL_LOCALE = Locale.getDefault();

    private String language = LanguageEnum.DEFAULT;
    private Map<String, String> translations = Map.of();

    private Translator() {
    }

    public static Translator getInstance() {
        return INSTANCE;
    }

    /**
     * El idioma del sistema si la app lo tiene; si no, ingles.
     */
    public String getSystemLanguage() {
        String systemLanguage = Locale.getDefault().getLanguage().toLowerCase(Locale.ROOT);
        return LanguageEnum.VALUES.contains(systemLanguage) ? systemLanguage : LanguageEnum.DEFAULT;
    }

    public synchronized void setLanguage(String language) {
        String validLanguage = LanguageEnum.VALUES.contains(language) ? language : LanguageEnum.DEFAULT;
        Map<String, String> loadedTranslations = new HashMap<>();
        for (String module : MODULES) {
            loadedTranslations.putAll(getPoTranslations(PATH_PO.formatted(module, validLanguage)));
        }
        this.translations = Map.copyOf(loadedTranslations);
        this.language = validLanguage;
        // lo que JavaFX trae traducido por su cuenta (ColorPicker, botones de Alert...) sigue el idioma por defecto de Java;
        // en papiamento JavaFX no tiene textos y usa los de ingles
        Locale.setDefault(Locale.of(validLanguage));
    }

    /**
     * Sin traducciones: cada texto tal como esta en el codigo (lo que esperan los tests que no son de idioma).
     */
    public synchronized void reset() {
        this.translations = Map.of();
        this.language = LanguageEnum.DEFAULT;
        Locale.setDefault(ORIGINAL_LOCALE);
    }

    public String getLanguage() {
        return language;
    }

    /**
     * Si el idioma activo trae entrada para el texto (aunque sea igual: "Color" en ingles).
     */
    public boolean hasTranslation(String msgId) {
        return translations.containsKey(msgId);
    }

    public String translate(String msgId) {
        String translation = translations.get(msgId);
        return translation == null || translation.isEmpty() ? msgId : translation;
    }

    private Map<String, String> getPoTranslations(String resourcePath) {
        Map<String, String> poTranslations = new HashMap<>();
        InputStream poStream = Translator.class.getResourceAsStream(resourcePath);
        if (poStream == null) return poTranslations;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(poStream, StandardCharsets.UTF_8))) {
            String msgId = null;
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.startsWith("msgid ")) {
                    msgId = getUnquoted(trimmed.substring(6));
                }
                else if (trimmed.startsWith("msgstr ") && msgId != null) {
                    poTranslations.put(msgId, getUnquoted(trimmed.substring(7)));
                    msgId = null;
                }
            }
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return poTranslations;
    }

    private String getUnquoted(String quoted) {
        String text = quoted.trim();
        if (text.length() < 2 || !text.startsWith("\"") || !text.endsWith("\"")) return text;
        StringBuilder unquoted = new StringBuilder();
        for (int i = 1; i < text.length() - 1; i++) {
            char current = text.charAt(i);
            if (current == '\\' && i + 1 < text.length() - 1) {
                char next = text.charAt(++i);
                switch (next) {
                    case 'n' -> unquoted.append('\n');
                    case 't' -> unquoted.append('\t');
                    default -> unquoted.append(next);
                }
                continue;
            }
            unquoted.append(current);
        }
        return unquoted.toString();
    }
}
