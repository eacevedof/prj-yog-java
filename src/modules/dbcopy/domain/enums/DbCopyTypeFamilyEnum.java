package modules.dbcopy.domain.enums;

/**
 * Copia entre motores: el "tipo comun" al que se reduce una columna del origen para rehacerla en el destino y escribir
 * sus valores. Lo que no encaja en ninguno (intervalos, arrays, xml, enum de PostgreSQL...) viaja como TEXT.
 */
public final class DbCopyTypeFamilyEnum {

    public static final String INTEGER = "integer";
    public static final String DECIMAL = "decimal";
    public static final String FLOAT = "float";
    public static final String BOOLEAN = "boolean";
    public static final String TEXT = "text";
    public static final String JSON = "json";
    public static final String UUID = "uuid";
    public static final String DATE = "date";
    public static final String TIME = "time";
    public static final String DATETIME = "datetime";
    public static final String BINARY = "binary";

    private DbCopyTypeFamilyEnum() {
    }
}
