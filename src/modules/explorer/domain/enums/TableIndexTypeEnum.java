package modules.explorer.domain.enums;

import java.util.List;

/**
 * Los tipos de indice del editor de tablas (MySQL); la clave primaria va aparte, en las columnas.
 */
public final class TableIndexTypeEnum {

    public static final String INDEX = "INDEX";
    public static final String UNIQUE = "UNIQUE";
    public static final String FULLTEXT = "FULLTEXT";
    public static final String SPATIAL = "SPATIAL";

    public static final List<String> VALUES = List.of(INDEX, UNIQUE, FULLTEXT, SPATIAL);

    private TableIndexTypeEnum() {
    }
}
