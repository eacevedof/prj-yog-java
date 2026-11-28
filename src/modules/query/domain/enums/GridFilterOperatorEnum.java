package modules.query.domain.enums;

import java.util.List;

/**
 * Los filtros del menu contextual de la rejilla (submenu Filtro, como SQLyog): la columna comparada con el valor de la
 * celda. LIKE y NOT LIKE buscan el valor en cualquier parte (%valor%).
 */
public final class GridFilterOperatorEnum {

    public static final String EQUAL = "=";
    public static final String NOT_EQUAL = "<>";
    public static final String LIKE = "LIKE";
    public static final String NOT_LIKE = "NOT LIKE";
    public static final String GREATER = ">";
    public static final String LESS = "<";

    public static final List<String> VALUES = List.of(EQUAL, NOT_EQUAL, LIKE, NOT_LIKE, GREATER, LESS);

    private GridFilterOperatorEnum() {
    }
}
