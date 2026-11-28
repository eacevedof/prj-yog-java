package modules.shared.infrastructure.components.fonts;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import javafx.scene.text.Font;

/**
 * Las fuentes que lleva la app dentro (no dependen de las del sistema): JetBrains Mono para el SQL (editor,
 * autocompletado y resultado en texto). Se registran una vez al arrancar; desde ahi el CSS las usa por su familia,
 * FAMILY_JETBRAINS_MONO. Licencia SIL OFL 1.1 (fonts/OFL.txt).
 */
public final class FontsComponent {

    public static final String FAMILY_JETBRAINS_MONO = "JetBrains Mono";
    // en linea en el editor SQL (RichTextFX): su code-area.css va en el propio nodo y ganaria a styles.css
    public static final String SQL_EDITOR_FONT_STYLE = "-fx-font-family: \"" + FAMILY_JETBRAINS_MONO + "\", \"Consolas\", \"DejaVu Sans Mono\", \"Menlo\", monospace;";
    private static final String PATH_FONTS = "/modules/shared/infrastructure/fonts/";
    private static final List<String> FONT_FILES = List.of(
        "JetBrainsMono-Regular.ttf", "JetBrainsMono-Bold.ttf", "JetBrainsMono-Italic.ttf"
    );
    private static final double LOAD_SIZE = 13;

    private static volatile boolean isLoaded;

    private FontsComponent() {
    }

    public static FontsComponent getInstance() {
        return new FontsComponent();
    }

    /**
     * Si una no se puede leer, el CSS cae a la siguiente de la lista (Consolas...): no impide arrancar.
     */
    public synchronized void loadAll() {
        if (isLoaded) return;
        for (String fontFile : FONT_FILES) {
            try (InputStream fontStream = FontsComponent.class.getResourceAsStream(PATH_FONTS + fontFile)) {
                if (fontStream == null || Font.loadFont(fontStream, LOAD_SIZE) == null) {
                    System.err.println("multidbs: no se pudo cargar la fuente " + fontFile);
                }
            }
            catch (IOException e) {
                System.err.println("multidbs: no se pudo cargar la fuente " + fontFile + ": " + e.getMessage());
            }
        }
        isLoaded = true;
    }
}
