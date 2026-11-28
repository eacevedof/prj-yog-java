package modules.shared.infrastructure.components.theme;

import java.util.ArrayList;
import java.util.List;

import javafx.scene.Scene;
import javafx.scene.control.Dialog;
import javafx.stage.Window;

/**
 * El tema de la app (feature 37): styles.css siempre y dark.css encima si el tema es oscuro. Toda ventana y todo dialogo
 * que se crea pasa por aqui para salir con el tema actual; al cambiarlo se repintan las ventanas abiertas. El tema
 * guardado lo lee el arranque (modulo settings) y lo fija con setDark().
 */
public final class ThemeComponent {

    public static final String PATH_STYLES = "/modules/shared/infrastructure/views/styles.css";
    public static final String PATH_DARK_STYLES = "/modules/shared/infrastructure/views/dark.css";

    private static volatile boolean isDark;

    private ThemeComponent() {
    }

    public static ThemeComponent getInstance() {
        return new ThemeComponent();
    }

    public boolean isDark() {
        return isDark;
    }

    public void setDark(boolean isDarkTheme) {
        isDark = isDarkTheme;
    }

    public List<String> getStylesheets() {
        List<String> stylesheets = new ArrayList<>();
        stylesheets.add(ThemeComponent.class.getResource(PATH_STYLES).toExternalForm());
        if (isDark) {
            stylesheets.add(ThemeComponent.class.getResource(PATH_DARK_STYLES).toExternalForm());
        }
        return stylesheets;
    }

    public void applyTo(Scene scene) {
        scene.getStylesheets().setAll(getStylesheets());
    }

    public void applyTo(Dialog<?> dialog) {
        dialog.getDialogPane().getStylesheets().setAll(getStylesheets());
    }

    /**
     * Tras cambiar el tema: todas las ventanas abiertas (principal, dialogos, visores).
     */
    public void applyToAllWindows() {
        for (Window window : Window.getWindows()) {
            if (window.getScene() != null) {
                applyTo(window.getScene());
            }
        }
    }
}
