package core.config;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Las funciones globales de task-java: getenv()/putenv() y __(). Java no deja escribir variables de entorno, asi que el
 * entorno de la app vive aqui: lo fijado con putenv() (los tests) gana sobre la variable real del OS.
 */
public final class Global {

    private static final Map<String, String> ENV_VARS = new ConcurrentHashMap<>();
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{(\\d+)\\}");

    private Global() {
    }

    /**
     * getenv(): lo fijado con putenv(); si no, la variable REAL del OS. null si no existe.
     */
    public static String getenv(String key) {
        String value = ENV_VARS.get(key);
        return value != null ? value : System.getenv(key);
    }

    public static void putenv(String key, String value) {
        ENV_VARS.put(key, value);
    }

    /**
     * La traduccion del msgid al idioma de la interfaz (Translator; sin traduccion, el propio msgid) con {0}, {1}...
     * sustituidos por los args en una sola pasada: un argumento que lleve "{1}" se queda tal cual. Un {n} sin argumento
     * tambien se queda.
     */
    public static String __(String msgId, Object... args) {
        String msgTrimmed = msgId == null ? "" : msgId.trim();
        if (msgTrimmed.isEmpty()) {
            return "";
        }
        String msgTranslated = Translator.getInstance().translate(msgTrimmed);
        if (args.length == 0) {
            return msgTranslated;
        }
        Matcher placeholderMatcher = PLACEHOLDER_PATTERN.matcher(msgTranslated);
        StringBuilder msgChanged = new StringBuilder();
        while (placeholderMatcher.find()) {
            int argIndex = Integer.parseInt(placeholderMatcher.group(1));
            String replacement = argIndex < args.length ? String.valueOf(args[argIndex]) : placeholderMatcher.group();
            placeholderMatcher.appendReplacement(msgChanged, Matcher.quoteReplacement(replacement));
        }
        placeholderMatcher.appendTail(msgChanged);
        return msgChanged.toString();
    }
}
