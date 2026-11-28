package modules.explorer.domain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

import testcase.AbstractTestCase;

import modules.explorer.domain.enums.DatabaseEnvironmentEnum;
import modules.explorer.domain.services.DatabaseEnvironmentResolver;

/**
 * El color de cada BD en el arbol sale de la palabra de entorno de su nombre.
 */
final class DatabaseEnvironmentResolverTest extends AbstractTestCase {

    private final DatabaseEnvironmentResolver resolver = DatabaseEnvironmentResolver.getInstance();

    @Test
    void testPrefixesAndSuffixesAreDetected() {
        assertEquals(DatabaseEnvironmentEnum.PRODUCTION, resolver.getEnvironment("prod-shop"));
        assertEquals(DatabaseEnvironmentEnum.PRODUCTION, resolver.getEnvironment("shop_production"));
        assertEquals(DatabaseEnvironmentEnum.PRODUCTION, resolver.getEnvironment("PROD"));
        assertEquals(DatabaseEnvironmentEnum.DEVELOPMENT, resolver.getEnvironment("dev-shop"));
        assertEquals(DatabaseEnvironmentEnum.DEVELOPMENT, resolver.getEnvironment("shop_develop"));
        assertEquals(DatabaseEnvironmentEnum.LOCAL, resolver.getEnvironment("loc-crm"));
        assertEquals(DatabaseEnvironmentEnum.LOCAL, resolver.getEnvironment("local_crm"));
        assertEquals(DatabaseEnvironmentEnum.LOCAL, resolver.getEnvironment("crm.local"));
    }

    @Test
    void testWordsInsideOtherWordsDoNotCount() {
        assertEquals(DatabaseEnvironmentEnum.NONE, resolver.getEnvironment("products"));
        assertEquals(DatabaseEnvironmentEnum.NONE, resolver.getEnvironment("devices"));
        assertEquals(DatabaseEnvironmentEnum.NONE, resolver.getEnvironment("location"));
        assertEquals(DatabaseEnvironmentEnum.NONE, resolver.getEnvironment("shop"));
        assertEquals(DatabaseEnvironmentEnum.NONE, resolver.getEnvironment(""));
    }

    @Test
    void testFirstWordWins() {
        assertEquals(DatabaseEnvironmentEnum.DEVELOPMENT, resolver.getEnvironment("dev-copy-of-prod"));
        assertEquals(DatabaseEnvironmentEnum.PRODUCTION, resolver.getEnvironment("prod-backup-dev"));
    }
}
