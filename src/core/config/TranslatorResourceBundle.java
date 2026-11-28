package core.config;

import java.util.Collections;
import java.util.Enumeration;
import java.util.ResourceBundle;

/**
 * El puente entre el FXML y Translator: un {@code text="%Conectar"} del FXML pasa por aqui. Cualquier clave existe (sin
 * traduccion vuelve el propio texto), asi que un texto nuevo sin traducir no rompe la carga de la vista.
 */
public final class TranslatorResourceBundle extends ResourceBundle {

    private static final TranslatorResourceBundle INSTANCE = new TranslatorResourceBundle();

    private TranslatorResourceBundle() {
    }

    public static TranslatorResourceBundle getInstance() {
        return INSTANCE;
    }

    @Override
    protected Object handleGetObject(String key) {
        return Translator.getInstance().translate(key);
    }

    @Override
    public boolean containsKey(String key) {
        return key != null;
    }

    @Override
    public Enumeration<String> getKeys() {
        return Collections.emptyEnumeration();
    }
}
