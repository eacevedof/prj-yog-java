package modules.dbcopy.domain.enums;

import java.util.List;

/**
 * Tamaños, prefijos y SQL fijos de la copia y del volcado.
 */
public final class DbCopyConfigEnum {

    // filas por pagina al leer el origen
    public static final int PAGE_ROWS = 1000;
    // prefijo de las claves de sesion de cada copia ("dbcopy-<uuid>:source" / ":target")
    public static final String SESSION_PREFIX = "dbcopy-";
    // las del volcado y el renombrado ("dbcopy-dump-<uuid>:source", "dbcopy-rename-<uuid>:write"...)
    public static final String DUMP_SESSION_PREFIX = SESSION_PREFIX + "dump-";
    public static final String RENAME_SESSION_PREFIX = SESSION_PREFIX + "rename-";
    // las de los dialogos, mientras estan abiertos (listar objetos del origen, BDs del destino)
    public static final String COPY_DIALOG_SESSION_PREFIX = "dbcopy-dialog-";
    public static final String DUMP_DIALOG_SESSION_PREFIX = "sqldump-dialog-";
    // tope de una sentencia INSERT en caracteres: por JDBC cabe holgado en el max_allowed_packet por defecto (4 MB en
    // 5.7, 64 MB en 8.0, aun con 4 bytes por caracter); por el tunel, una peticion HTTP razonable
    public static final int JDBC_MAX_STATEMENT_CHARS = 512 * 1024;
    public static final int TUNNEL_MAX_STATEMENT_CHARS = 256 * 1024;
    // un caracter puede ocupar hasta 4 bytes en utf8mb4
    public static final int MAX_BYTES_PER_CHAR = 4;
    // margen para la cabecera del paquete y el prefijo "INSERT INTO ..."
    public static final int PACKET_MARGIN_CHARS = 1024;
    // por debajo de esto no se trocea: una fila sola por sentencia ya pasa del tope
    public static final int MIN_STATEMENT_CHARS = 1024;

    /**
     * Los SET que preparan la sesion destino, como hace mysqldump: sin claves foraneas (el orden de las tablas no
     * importa), sin modo estricto (fechas cero, valores que el origen acepto) y sin que un 0 en AUTO_INCREMENT
     * se convierta en el siguiente valor. La zona horaria a UTC en las dos sesiones: un TIMESTAMP viaja como texto.
     */
    public static final List<String> TARGET_SESSION_SQLS = List.of(
        "SET FOREIGN_KEY_CHECKS = 0",
        "SET UNIQUE_CHECKS = 0",
        "SET SQL_MODE = 'NO_AUTO_VALUE_ON_ZERO'",
        "SET TIME_ZONE = '+00:00'"
    );
    public static final String SOURCE_SESSION_SQL = "SET TIME_ZONE = '+00:00'";

    public static final String NEW_LINE = "\n";

    private DbCopyConfigEnum() {
    }
}
