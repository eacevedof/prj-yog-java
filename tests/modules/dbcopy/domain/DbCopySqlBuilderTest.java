package modules.dbcopy.domain;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import testcase.AbstractTestCase;

import modules.dbcopy.domain.enums.DbCopyObjectTypeEnum;
import modules.dbcopy.domain.services.BulkInsertBuffer;
import modules.dbcopy.domain.services.DbCopySqlBuilder;

final class DbCopySqlBuilderTest extends AbstractTestCase {

    private final DbCopySqlBuilder dbCopySqlBuilder = DbCopySqlBuilder.getInstance();

    @Test
    void removes_only_the_definer_of_the_header() {
        assertEquals(
            "CREATE ALGORITHM=UNDEFINED SQL SECURITY DEFINER VIEW `v` AS select 1",
            dbCopySqlBuilder.getWithoutDefiner("CREATE ALGORITHM=UNDEFINED DEFINER=`root`@`%` SQL SECURITY DEFINER VIEW `v` AS select 1")
        );
        assertEquals(
            "CREATE PROCEDURE `p`() BEGIN SELECT 'DEFINER=`x`@`y`'; END",
            dbCopySqlBuilder.getWithoutDefiner("CREATE DEFINER=`app user`@`10.0.%` PROCEDURE `p`() BEGIN SELECT 'DEFINER=`x`@`y`'; END")
        );
        assertEquals("CREATE TRIGGER t", dbCopySqlBuilder.getWithoutDefiner("CREATE DEFINER=CURRENT_USER TRIGGER t"));
        assertEquals("CREATE EVENT e", dbCopySqlBuilder.getWithoutDefiner("CREATE DEFINER='root'@'localhost' EVENT e"));
        assertEquals("CREATE TABLE `t` (id int)", dbCopySqlBuilder.getWithoutDefiner("CREATE TABLE `t` (id int)"));
    }

    @Test
    void a_table_without_drop_is_created_only_if_missing() {
        assertEquals("CREATE TABLE IF NOT EXISTS `t` (id int)", dbCopySqlBuilder.getCreateTableIfNotExists("CREATE TABLE `t` (id int)"));
        assertEquals("CREATE TABLE IF NOT EXISTS `t` (id int)", dbCopySqlBuilder.getCreateTableIfNotExists("CREATE TABLE IF NOT EXISTS `t` (id int)"));
        assertEquals("CREATE VIEW `v` AS select 1", dbCopySqlBuilder.getWithoutDefiner("CREATE DEFINER=`a`@`b` VIEW `v` AS select 1"));
        assertEquals("DROP PROCEDURE IF EXISTS `p``x`", dbCopySqlBuilder.getDropSql(DbCopyObjectTypeEnum.PROCEDURE, "p`x"));
    }

    @Test
    void creates_the_database_with_the_source_charset_and_ignores_strange_values() {
        assertEquals(
            "CREATE DATABASE IF NOT EXISTS `new db` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci",
            dbCopySqlBuilder.getCreateDatabaseSql("new db", "utf8mb4", "utf8mb4_unicode_ci")
        );
        assertEquals("CREATE DATABASE IF NOT EXISTS `x`", dbCopySqlBuilder.getCreateDatabaseSql("x", "utf8; DROP", ""));
    }

    @Test
    void pages_by_primary_key_range_or_by_offset() {
        List<String> columns = List.of("id", "flag", "name");
        assertEquals(
            "SELECT `id`, CAST(`flag` AS UNSIGNED) AS `flag`, `name` FROM `db`.`t` ORDER BY `id` LIMIT 1000",
            dbCopySqlBuilder.getSelectPageSql("db", "t", columns, Set.of("flag"), List.of("id"), List.of(), 1000, 0)
        );
        assertEquals(
            "SELECT `id`, `flag`, `name` FROM `db`.`t` WHERE `id` > '41' ORDER BY `id` LIMIT 1000",
            dbCopySqlBuilder.getSelectPageSql("db", "t", columns, Set.of(), List.of("id"), List.of("41"), 1000, 1000)
        );
        assertEquals(
            "SELECT `id`, `flag`, `name` FROM `db`.`t` WHERE (`id`, `name`) > ('7', 'O\\'Brien') ORDER BY `id`, `name` LIMIT 10",
            dbCopySqlBuilder.getSelectPageSql("db", "t", columns, Set.of(), List.of("id", "name"), List.of("7", "O'Brien"), 10, 0)
        );
        assertEquals(
            "SELECT `id`, `flag`, `name` FROM `db`.`t` LIMIT 1000 OFFSET 2000",
            dbCopySqlBuilder.getSelectPageSql("db", "t", columns, Set.of(), List.of(), List.of(), 1000, 2000)
        );
    }

    @Test
    void writes_text_binary_bit_and_null_cells() {
        assertEquals("INSERT INTO `t` (`a`, `b`) VALUES ", dbCopySqlBuilder.getInsertPrefix("t", List.of("a", "b")));
        assertEquals(
            "('it\\'s\\n', X'cafe', 5, NULL, '')",
            dbCopySqlBuilder.getRowTuple(Arrays.asList("it's\n", new byte[]{(byte) 0xCA, (byte) 0xFE}, new BigDecimal("5"), null, ""))
        );
        assertTrue(dbCopySqlBuilder.isBinaryDataType("LONGBLOB"));
        assertTrue(dbCopySqlBuilder.isBinaryDataType("point"));
        assertFalse(dbCopySqlBuilder.isBinaryDataType("varchar"));
        assertTrue(dbCopySqlBuilder.isBitDataType("bit"));
    }

    @Test
    void joins_rows_up_to_the_size_limit_or_one_per_statement() {
        // prefijo de 23 + fila de 11 = 34: con tope 55 caben dos filas por sentencia (34 + 2 + 11 = 47)
        BulkInsertBuffer bulkBuffer = BulkInsertBuffer.getBulkInstance("INSERT INTO `t` VALUES ", 55);
        bulkBuffer.add("(1, 'aaaa')");
        bulkBuffer.add("(2, 'bbbb')");
        bulkBuffer.add("(3, 'cccc')");
        assertEquals(
            List.of("INSERT INTO `t` VALUES (1, 'aaaa'), (2, 'bbbb')", "INSERT INTO `t` VALUES (3, 'cccc')"),
            bulkBuffer.getStatements()
        );
        // leerlas no cambia el buffer
        assertEquals(2, bulkBuffer.getStatements().size());

        // una fila mayor que el tope va sola
        BulkInsertBuffer tinyBuffer = BulkInsertBuffer.getBulkInstance("INSERT INTO `t` VALUES ", 10);
        tinyBuffer.add("(1, 'aaaa')");
        tinyBuffer.add("(2, 'bbbb')");
        assertEquals(List.of("INSERT INTO `t` VALUES (1, 'aaaa')", "INSERT INTO `t` VALUES (2, 'bbbb')"), tinyBuffer.getStatements());

        BulkInsertBuffer rowByRowBuffer = BulkInsertBuffer.getRowByRowInstance("INSERT INTO `t` VALUES ");
        rowByRowBuffer.add("(1, 'aaaa')");
        rowByRowBuffer.add("(2, 'bbbb')");
        assertEquals(List.of("INSERT INTO `t` VALUES (1, 'aaaa')", "INSERT INTO `t` VALUES (2, 'bbbb')"), rowByRowBuffer.getStatements());
    }
}
