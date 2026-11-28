package modules.shared.infrastructure.components.files;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.util.Locale;

/**
 * Abre en el explorador del sistema la carpeta de un fichero: en Windows con el fichero ya elegido si existe; si la
 * carpeta aun no existe, la primera de sus padres que si. Corre en un hilo aparte: el explorador puede tardar.
 */
public final class FolderOpenerComponent {

    private static final boolean IS_WINDOWS = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");

    private FolderOpenerComponent() {
    }

    public static FolderOpenerComponent getInstance() {
        return new FolderOpenerComponent();
    }

    /**
     * La carpeta que se abriria para esa ruta; null si no hay ninguna que exista.
     */
    public File getExistingFolder(String filePath) {
        if (filePath == null || filePath.isBlank()) return null;
        File folder = new File(filePath.trim()).getAbsoluteFile();
        if (!folder.isDirectory()) folder = folder.getParentFile();
        while (folder != null && !folder.isDirectory()) folder = folder.getParentFile();
        return folder;
    }

    /**
     * @throws IllegalArgumentException si no hay carpeta que abrir
     */
    public void open(String filePath) {
        File folder = getExistingFolder(filePath);
        if (folder == null) throw new IllegalArgumentException(filePath);
        File file = new File(filePath.trim()).getAbsoluteFile();
        Thread daemonThread = new Thread(() -> {
            try {
                if (IS_WINDOWS && file.isFile()) {
                    new ProcessBuilder("explorer.exe", "/select,", file.getPath()).start();
                    return;
                }
                if (IS_WINDOWS) {
                    new ProcessBuilder("explorer.exe", folder.getPath()).start();
                    return;
                }
                Desktop.getDesktop().open(folder);
            }
            catch (IOException | RuntimeException e) {
                System.err.println("multidbs: no se pudo abrir la carpeta " + folder + ": " + e.getMessage());
            }
        }, "open-folder");
        daemonThread.setDaemon(true);
        daemonThread.start();
    }
}
