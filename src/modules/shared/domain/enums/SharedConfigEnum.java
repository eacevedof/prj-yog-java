package modules.shared.domain.enums;

/**
 * Configuracion comun a todos los modulos.
 */
public final class SharedConfigEnum {

    // nombre de la app hacia fuera: cliente en MySQL/PostgreSQL/Redis/Mongo, User-Agent del tunel, cabecera de volcados
    // y exportaciones
    public static final String APP_NAME = "multidbs";
    // las consolas de Redis y MongoDB marcan asi una linea de error, como redis-cli
    public static final String CONSOLE_ERROR_PREFIX = "(error) ";

    private SharedConfigEnum() {
    }
}
