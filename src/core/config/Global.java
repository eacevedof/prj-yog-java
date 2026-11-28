package core.config;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Las funciones globales de task-java: getenv()/putenv() y __(). Java no deja escribir variables de entorno, asi que el
 * entorno de la app vive aqui: lo fijado con putenv() (los tests) gana sobre la variable real del OS.
 */
public final class Global {

    private static final Map<String, String> ENV_VARS = new ConcurrentHashMap<>();

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
     * sustituidos por los args.
     */
    public static String __(String msgId, Object... args) {
        String msgTrimmed = msgId == null ? "" : msgId.trim();
        if (msgTrimmed.isEmpty()) {
            return "";
        }
        String msgChanged = Translator.getInstance().translate(msgTrimmed);
        for (int i = 0; i < args.length; i++) {
            msgChanged = msgChanged.replace("{" + i + "}", String.valueOf(args[i]));
        }
        return msgChanged;
    }
}
