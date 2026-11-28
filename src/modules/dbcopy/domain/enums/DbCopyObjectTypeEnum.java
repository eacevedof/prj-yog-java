package modules.dbcopy.domain.enums;

import java.util.List;

/**
 * Los tipos de objeto que se copian, en el orden en que se crean en el destino: las tablas (con sus datos) primero, las
 * vistas despues (dependen de tablas), las rutinas, y los triggers y eventos al final (un trigger creado antes de
 * los datos saltaria con cada INSERT de la copia).
 */
public final class DbCopyObjectTypeEnum {

    public static final String TABLE = "table";
    public static final String VIEW = "view";
    public static final String PROCEDURE = "procedure";
    public static final String FUNCTION = "function";
    public static final String TRIGGER = "trigger";
    public static final String EVENT = "event";

    public static final List<String> VALUES = List.of(TABLE, VIEW, PROCEDURE, FUNCTION, TRIGGER, EVENT);

    private DbCopyObjectTypeEnum() {
    }
}
