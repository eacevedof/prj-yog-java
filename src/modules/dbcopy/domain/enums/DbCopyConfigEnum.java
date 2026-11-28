package modules.dbcopy.domain.enums;

/**
 * Tamaños y prefijos de la copia.
 */
public final class DbCopyConfigEnum {

    // filas por pagina al leer el origen
    public static final int PAGE_ROWS = 1000;
    // prefijo de las claves de sesion de cada copia ("dbcopy-<uuid>:source" / ":target")
    public static final String SESSION_PREFIX = "dbcopy-";
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

    private DbCopyConfigEnum() {
    }
}
