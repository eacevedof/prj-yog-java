package modules.explorer.domain.enums;

/**
 * El entorno de una BD segun su nombre (DatabaseEnvironmentResolver): el sufijo de su estilo en el arbol.
 */
public final class DatabaseEnvironmentEnum {

    public static final String PRODUCTION = "prod";
    public static final String DEVELOPMENT = "dev";
    public static final String LOCAL = "local";
    public static final String NONE = "";

    private DatabaseEnvironmentEnum() {
    }
}
