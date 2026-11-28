package modules.explorer.domain.services;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * El entorno de una BD por su nombre, para colorearla en el arbol: prod en rojo, dev en naranja, local en verde. Cuenta
 * la palabra suelta, separada por "-", "_" o "." (prod-shop, shop_dev, loc.crm): asi "device" o "product" no cuentan.
 * Si el nombre lleva varias, gana la primera que aparece.
 */
public final class DatabaseEnvironmentResolver {

    public static final String PRODUCTION = "prod";
    public static final String DEVELOPMENT = "dev";
    public static final String LOCAL = "local";
    public static final String NONE = "";

    private static final Pattern ENVIRONMENT_WORD = Pattern.compile(
        "(?:^|[-_.])(prod|production|pro|dev|develop|development|loc|local|localhost)(?=[-_.]|$)"
    );

    private DatabaseEnvironmentResolver() {
    }

    public static DatabaseEnvironmentResolver getInstance() {
        return new DatabaseEnvironmentResolver();
    }

    public String getEnvironment(String databaseName) {
        if (databaseName == null) return NONE;
        Matcher matcher = ENVIRONMENT_WORD.matcher(databaseName.trim().toLowerCase(Locale.ROOT));
        if (!matcher.find()) return NONE;
        return switch (matcher.group(1)) {
            case "prod", "production", "pro" -> PRODUCTION;
            case "dev", "develop", "development" -> DEVELOPMENT;
            default -> LOCAL;
        };
    }
}
