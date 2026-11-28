package modules.dbcopy.domain.enums;

/**
 * Como se lee y se escribe cada celda: texto tal cual lo da el servidor, bytes (X'..') o un BIT como numero.
 */
public final class DbCopyCellKindEnum {

    public static final String TEXT = "text";
    public static final String BINARY = "binary";
    public static final String BIT = "bit";

    private DbCopyCellKindEnum() {
    }
}
