package modules.shared.infrastructure.components.date;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class DateTimer {

    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    // para nombres de fichero: 261126223015 = 2026-11-26 22:30:15
    private static final DateTimeFormatter COMPACT_DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyMMddHHmmss");

    private DateTimer() {
    }

    public static DateTimer getInstance() {
        return new DateTimer();
    }

    /**
     * Fecha-hora local de la maquina: la app es de escritorio y de un solo usuario.
     */
    public String getNowDt() {
        return LocalDateTime.now().format(DATETIME_FORMATTER);
    }

    /**
     * Fecha-hora local sin separadores (yyMMddHHmmss), para el nombre de un fichero exportado.
     */
    public String getNowCompactDt() {
        return LocalDateTime.now().format(COMPACT_DATETIME_FORMATTER);
    }
}
