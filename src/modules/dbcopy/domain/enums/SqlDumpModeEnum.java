package modules.dbcopy.domain.enums;

import java.util.List;

/**
 * Lo que lleva un volcado .sql, como el "Export as SQL" de SQLyog.
 */
public final class SqlDumpModeEnum {

    public static final String STRUCTURE_ONLY = "structure";
    public static final String DATA_ONLY = "data";
    public static final String STRUCTURE_AND_DATA = "both";

    public static final List<String> VALUES = List.of(STRUCTURE_ONLY, DATA_ONLY, STRUCTURE_AND_DATA);

    private SqlDumpModeEnum() {
    }
}
