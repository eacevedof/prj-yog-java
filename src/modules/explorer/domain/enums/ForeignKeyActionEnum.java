package modules.explorer.domain.enums;

import java.util.List;

/**
 * ON DELETE / ON UPDATE de una clave foranea.
 */
public final class ForeignKeyActionEnum {

    public static final String RESTRICT = "RESTRICT";
    public static final String CASCADE = "CASCADE";
    public static final String SET_NULL = "SET NULL";
    public static final String NO_ACTION = "NO ACTION";

    public static final List<String> VALUES = List.of(RESTRICT, CASCADE, SET_NULL, NO_ACTION);

    private ForeignKeyActionEnum() {
    }
}
