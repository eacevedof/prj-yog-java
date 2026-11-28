package modules.shared.infrastructure.components.appversion;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * La version de la app (la del pom.xml), para el pie de la ventana. Maven la escribe en app-version.properties al
 * compilar; sin compilar con Maven (el placeholder sigue ahi) o sin el fichero, es "dev".
 */
public final class AppVersionComponent {

    private static final String VERSION_FILE = "app-version.properties";
    private static final String VERSION_KEY = "version";
    private static final String UNKNOWN_VERSION = "dev";
    private static final String UNFILTERED_MARK = "${";

    private AppVersionComponent() {
    }

    public static AppVersionComponent getInstance() {
        return new AppVersionComponent();
    }

    /**
     * @throws IOException si el fichero existe pero no se puede leer
     */
    public String getVersion() throws IOException {
        try (InputStream versionStream = AppVersionComponent.class.getResourceAsStream(VERSION_FILE)) {
            if (versionStream == null) return UNKNOWN_VERSION;
            Properties properties = new Properties();
            properties.load(versionStream);
            String version = properties.getProperty(VERSION_KEY, "").trim();
            return version.isEmpty() || version.contains(UNFILTERED_MARK) ? UNKNOWN_VERSION : version;
        }
    }
}
