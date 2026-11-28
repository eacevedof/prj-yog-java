package modules.explorer.domain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

import testcase.AbstractTestCase;

import modules.explorer.domain.services.DatabaseEnvironmentResolver;

/**
 * El color de cada BD en el arbol sale de la palabra de entorno de su nombre.
 */
final class DatabaseEnvironmentResolverTest extends AbstractTestCase {

    private final DatabaseEnvironmentResolver resolver = DatabaseEnvironmentResolver.getInstance();

    @Test
    void testPrefixesAndSuffixesAreDetected() {
        assertEquals(DatabaseEnvironmentResolver.PRODUCTION, resolver.getEnvironment("prod-shop"));
        assertEquals(DatabaseEnvironmentResolver.PRODUCTION, resolver.getEnvironment("shop_production"));
        assertEquals(DatabaseEnvironmentResolver.PRODUCTION, resolver.getEnvironment("PROD"));
        assertEquals(DatabaseEnvironmentResolver.DEVELOPMENT, resolver.getEnvironment("dev-shop"));
        assertEquals(DatabaseEnvironmentResolver.DEVELOPMENT, resolver.getEnvironment("shop_develop"));
        assertEquals(DatabaseEnvironmentResolver.LOCAL, resolver.getEnvironment("loc-crm"));
        assertEquals(DatabaseEnvironmentResolver.LOCAL, resolver.getEnvironment("local_crm"));
        assertEquals(DatabaseEnvironmentResolver.LOCAL, resolver.getEnvironment("crm.local"));
    }

    @Test
    void testWordsInsideOtherWordsDoNotCount() {
        assertEquals(DatabaseEnvironmentResolver.NONE, resolver.getEnvironment("products"));
        assertEquals(DatabaseEnvironmentResolver.NONE, resolver.getEnvironment("devices"));
        assertEquals(DatabaseEnvironmentResolver.NONE, resolver.getEnvironment("location"));
        assertEquals(DatabaseEnvironmentResolver.NONE, resolver.getEnvironment("shop"));
        assertEquals(DatabaseEnvironmentResolver.NONE, resolver.getEnvironment(""));
    }

    @Test
    void testFirstWordWins() {
        assertEquals(DatabaseEnvironmentResolver.DEVELOPMENT, resolver.getEnvironment("dev-copy-of-prod"));
        assertEquals(DatabaseEnvironmentResolver.PRODUCTION, resolver.getEnvironment("prod-backup-dev"));
    }
}
