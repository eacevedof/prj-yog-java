package modules.dbcopy.domain.enums;

/**
 * Los pasos de "Renombrar BD" (MySQL no tiene RENAME DATABASE): volcar la original a un .sql, crear la nueva, cargar
 * el volcado en ella, comprobar que tiene los mismos objetos y filas, y borrar la original.
 */
public final class RenameDatabasePhaseEnum {

    public static final String DUMP = "dump";
    public static final String CREATE = "create";
    public static final String LOAD = "load";
    public static final String VERIFY = "verify";
    public static final String DROP = "drop";

    private RenameDatabasePhaseEnum() {
    }
}
