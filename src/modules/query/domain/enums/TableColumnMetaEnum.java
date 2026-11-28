package modules.query.domain.enums;

import java.util.Set;

/**
 * Lo que dicen las columnas de ListTableColumnsService (COLUMN_KEY, EXTRA, DATA_TYPE) para saber si una rejilla se
 * edita: la clave primaria, las autonumericas, las calculadas y los tipos que no se editan como texto (binarios,
 * BIT y geometria: la rejilla ensena "0x..." o WKT, no el valor).
 */
public final class TableColumnMetaEnum {

    public static final String KEY_PRIMARY = "PRI";
    public static final String EXTRA_AUTO_INCREMENT = "auto_increment";
    public static final String EXTRA_GENERATED = "generated";

    public static final Set<String> READ_ONLY_DATA_TYPES = Set.of(
        "bit", "binary", "varbinary", "bytea", "tinyblob", "blob", "mediumblob", "longblob", "geometry", "point", "linestring",
        "polygon", "multipoint", "multilinestring", "multipolygon", "geometrycollection"
    );

    private TableColumnMetaEnum() {
    }
}
