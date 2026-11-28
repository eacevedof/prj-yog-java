package modules.query.domain;

import org.junit.jupiter.api.Test;

import testcase.AbstractTestCase;

import modules.query.domain.entities.SelectSourceTableEntity;
import modules.query.domain.services.EditableSelectDetector;

/**
 * Que SELECT se puede editar en la rejilla del resultado (como SQLyog): de una sola tabla y con columnas tal cual.
 */
final class EditableSelectDetectorTest extends AbstractTestCase {

    private final EditableSelectDetector editableSelectDetector = EditableSelectDetector.getInstance();

    @Test
    void testSingleTableSelectsAreEditable() {
        assertTable("", "users", "SELECT * FROM users");
        assertTable("", "users", "select * from `users` where id > 10 order by id desc limit 50;");
        assertTable("shop", "users", "SELECT id, email FROM `shop`.`users` u WHERE u.name = 'Ana'");
        assertTable("shop", "users", "SELECT u.* FROM shop.users AS u LIMIT 10 OFFSET 5");
        assertTable("public", "base_user", "SELECT \"id\", \"login\" FROM \"public\".\"base_user\"");
        assertTable("dbo", "orders", "SELECT [id], [total] FROM [dbo].[orders]");
        assertTable("", "users", "-- los activos\nSELECT id, u.email\nFROM users u\nWHERE id IN (SELECT user_id FROM orders) FOR UPDATE");
    }

    @Test
    void testTextInsideStringsAndCommentsDoesNotCount() {
        assertTable("", "users", "SELECT * FROM users WHERE note = 'group by x union select 1' /* having */");
        assertTable("", "users", "SELECT * FROM users WHERE name = 'O''Brien' AND bio = 'a\\'b JOIN c'");
        assertReadOnly("SELECT * FROM users WHERE bio = 'a\\'b' UNION SELECT * FROM old_users");
    }

    @Test
    void testAnythingElseIsReadOnly() {
        assertReadOnly("SELECT * FROM users u JOIN orders o ON o.user_id = u.id");
        assertReadOnly("SELECT * FROM users u LEFT JOIN orders o ON o.user_id = u.id");
        assertReadOnly("SELECT * FROM users, orders");
        assertReadOnly("SELECT * FROM (SELECT * FROM users) t");
        assertReadOnly("SELECT DISTINCT email FROM users");
        assertReadOnly("SELECT COUNT(*) FROM users");
        assertReadOnly("SELECT id, email AS mail FROM users");
        assertReadOnly("SELECT id, UPPER(email) FROM users");
        assertReadOnly("SELECT status FROM users GROUP BY status");
        assertReadOnly("SELECT * FROM users WHERE id > 1 GROUP BY status HAVING COUNT(*) > 1");
        assertReadOnly("SELECT * FROM users UNION SELECT * FROM old_users");
        assertReadOnly("SELECT * FROM users WHERE id = 1 INTO OUTFILE '/tmp/x'");
        assertReadOnly("SELECT 1");
        assertReadOnly("SHOW TABLES");
        assertReadOnly("UPDATE users SET a = 1");
        assertReadOnly("");
        assertReadOnly(null);
    }

    private void assertTable(String databaseName, String tableName, String sql) {
        SelectSourceTableEntity sourceTable = editableSelectDetector.getSourceTable(sql);
        // si falla, el mensaje lleva la SQL
        String actual = sourceTable == null ? "solo lectura: " + sql : sourceTable.getDatabaseName() + "|" + sourceTable.getTableName();
        assertEquals(databaseName + "|" + tableName, actual);
    }

    private void assertReadOnly(String sql) {
        assertEquals("solo lectura", editableSelectDetector.getSourceTable(sql) == null ? "solo lectura" : "editable: " + sql);
    }
}
