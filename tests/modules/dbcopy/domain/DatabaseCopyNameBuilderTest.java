package modules.dbcopy.domain;

import org.junit.jupiter.api.Test;

import testcase.AbstractTestCase;

import modules.dbcopy.domain.services.DatabaseCopyNameBuilder;

/**
 * "Duplicar BD": &lt;bd&gt;_copy_&lt;ymdhis&gt;, sin pasar de los 64 caracteres de MySQL (se recorta el nombre, no la fecha).
 */
final class DatabaseCopyNameBuilderTest extends AbstractTestCase {

    private final DatabaseCopyNameBuilder databaseCopyNameBuilder = DatabaseCopyNameBuilder.getInstance();

    @Test
    void testCopyNameCarriesTheDate() {
        assertEquals("db_shop_copy_20261127124500", databaseCopyNameBuilder.getCopyDatabaseName("db_shop", "2026-11-27 12:45:00"));
    }

    @Test
    void testLongNamesAreCutToFit() {
        String longDatabaseName = "d".repeat(60);

        String copyDatabaseName = databaseCopyNameBuilder.getCopyDatabaseName(longDatabaseName, "2026-11-27 12:45:00");

        assertEquals(64, copyDatabaseName.length());
        assertEquals("d".repeat(44) + "_copy_20261127124500", copyDatabaseName);
    }
}
