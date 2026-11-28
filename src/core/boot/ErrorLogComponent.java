package core.boot;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Las excepciones que nadie captura (un fallo dentro de un clic) JavaFX solo las escribe en la consola y la ventana
 * sigue como si nada: "no hace nada". Aqui se guardan tambien en <JAVAYOG_HOME>/errors.log, con fecha, para saber que
 * paso aunque la app se haya abierto sin consola.
 */
public final class ErrorLogComponent {

    private static final String FILE_NAME = "errors.log";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private ErrorLogComponent() {
    }

    public static ErrorLogComponent getInstance() {
        return new ErrorLogComponent();
    }

    /**
     * Para el hilo de JavaFX (y cualquier otro sin manejador propio): consola + errors.log.
     */
    public void install() {
        Thread.UncaughtExceptionHandler handler = (thread, exception) -> log(thread.getName(), exception);
        Thread.setDefaultUncaughtExceptionHandler(handler);
        Thread.currentThread().setUncaughtExceptionHandler(handler);
    }

    public void log(String threadName, Throwable exception) {
        StringWriter stackTrace = new StringWriter();
        exception.printStackTrace(new PrintWriter(stackTrace));
        String entry = LocalDateTime.now().format(DATE_FORMAT) + " [" + threadName + "] " + stackTrace + System.lineSeparator();
        System.err.print(entry);
        try {
            Path appHome = Path.of(BootPathsEnum.getPathAppHome());
            Files.createDirectories(appHome);
            Files.writeString(appHome.resolve(FILE_NAME), entry, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        }
        catch (IOException e) {
            System.err.println("multidbs: could not write " + FILE_NAME + ": " + e.getMessage());
        }
    }
}
