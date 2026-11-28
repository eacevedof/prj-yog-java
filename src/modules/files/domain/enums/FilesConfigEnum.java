package modules.files.domain.enums;

public final class FilesConfigEnum {

    /**
     * La clave de cada tabla cargada: la rejilla solo edita tablas con clave primaria y un fichero no la trae. No se
     * escribe al guardar.
     */
    public static final String ROW_COLUMN = "_row";

    /**
     * Separa nombres y tipos de columna al guardarlos en una sola celda (no aparece en un nombre de columna normal).
     */
    public static final String LIST_SEPARATOR = "\u001F";

    public static final String LINE_SEPARATOR_LF = "\n";
    public static final String LINE_SEPARATOR_CRLF = "\r\n";
    public static final String UTF8_BOM = String.valueOf((char) 0xFEFF);
    public static final String DEFAULT_COLUMN_PREFIX = "column_";
    public static final String DUPLICATE_SUFFIX = "_";

    private FilesConfigEnum() {
    }
}
