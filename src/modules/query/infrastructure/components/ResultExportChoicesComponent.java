package modules.query.infrastructure.components;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lo ultimo elegido en el dialogo "Exportar como": se recuerda mientras la app sigue abierta (una instancia por proceso).
 */
public final class ResultExportChoicesComponent {

    private static final ResultExportChoicesComponent INSTANCE = new ResultExportChoicesComponent();

    private final Map<String, Object> lastChoices = new ConcurrentHashMap<>();

    private ResultExportChoicesComponent() {
    }

    public static ResultExportChoicesComponent getInstance() {
        return INSTANCE;
    }

    public String getLastChoice(String key, String defaultValue) {
        Object value = lastChoices.get(key);
        return value == null ? defaultValue : String.valueOf(value);
    }

    public void saveChoices(Map<String, Object> choices) {
        choices.forEach((key, value) -> {
            if (value != null) lastChoices.put(key, value);
        });
    }
}
