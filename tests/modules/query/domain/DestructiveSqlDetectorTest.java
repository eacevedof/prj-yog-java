package modules.query.domain;

import org.junit.jupiter.api.Test;

import testcase.AbstractTestCase;

import modules.query.domain.services.DestructiveSqlDetector;

/**
 * Lo que pide confirmacion en una conexion de produccion (feature 44).
 */
final class DestructiveSqlDetectorTest extends AbstractTestCase {

    private final DestructiveSqlDetector destructiveSqlDetector = DestructiveSqlDetector.getInstance();

    @Test
    void testUpdateAndDeleteWithoutWhereAreDestructive() {
        assertTrue(destructiveSqlDetector.isDestructive("UPDATE users SET active = 0"));
        assertTrue(destructiveSqlDetector.isDestructive("delete from users"));
        assertTrue(destructiveSqlDetector.isDestructive("-- limpiar\nDELETE FROM users"));
    }

    @Test
    void testUpdateAndDeleteWithWhereAreNot() {
        assertFalse(destructiveSqlDetector.isDestructive("UPDATE users SET active = 0 WHERE id = 3"));
        assertFalse(destructiveSqlDetector.isDestructive("DELETE FROM users\nwhere id IN (1, 2)"));
    }

    @Test
    void testWhereInsideLiteralsOrCommentsDoesNotCount() {
        assertTrue(destructiveSqlDetector.isDestructive("UPDATE users SET note = 'where am i'"));
        assertTrue(destructiveSqlDetector.isDestructive("UPDATE `where` SET a = 1"));
        assertTrue(destructiveSqlDetector.isDestructive("DELETE FROM users -- WHERE id = 1"));
        assertFalse(destructiveSqlDetector.isDestructive("UPDATE t SET a = '-- x' WHERE id = 1"));
    }

    @Test
    void testDropAndTruncateAreDestructive() {
        assertTrue(destructiveSqlDetector.isDestructive("DROP TABLE users"));
        assertTrue(destructiveSqlDetector.isDestructive("drop database shop"));
        assertTrue(destructiveSqlDetector.isDestructive("TRUNCATE users"));
    }

    @Test
    void testReadsAndInsertsAreNot() {
        assertFalse(destructiveSqlDetector.isDestructive("SELECT * FROM users"));
        assertFalse(destructiveSqlDetector.isDestructive("INSERT INTO users (id) VALUES (1)"));
        assertFalse(destructiveSqlDetector.isDestructive("SELECT 'DROP TABLE x'"));
    }
}
