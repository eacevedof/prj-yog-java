package modules.files.domain.enums;

import java.util.Map;

/**
 * Lo que guarda cada columna de un fichero, para crearla con su tipo en SQLite y escribirla igual al guardar: en un
 * CSV, numeros o texto; en un JSON ademas booleanos y objetos o listas anidados (que en SQLite van como texto JSON).
 */
public final class FilesColumnKindEnum {

    public static final String STRING = "string";
    public static final String INTEGER = "integer";
    public static final String REAL = "real";
    public static final String BOOLEAN = "boolean";
    public static final String JSON = "json";

    public static final Map<String, String> SQLITE_TYPES = Map.of(
        STRING, "TEXT",
        INTEGER, "INTEGER",
        REAL, "NUMERIC",
        BOOLEAN, "INTEGER",
        JSON, "TEXT"
    );

    private FilesColumnKindEnum() {
    }
}
