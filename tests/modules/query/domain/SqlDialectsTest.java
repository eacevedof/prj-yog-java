package modules.query.domain;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import modules.shared.domain.services.SqlIdentifierQuoter;
import modules.shared.domain.services.SqlLiteralQuoter;
import modules.shared.application.Primitives;

import testcase.AbstractTestCase;

import modules.explorer.domain.enums.ExplorerNodeTypeEnum;
import modules.explorer.domain.services.ObjectDefinitionSqlBuilder;
import modules.explorer.domain.services.TableDuplicateSqlBuilder;
import modules.explorer.domain.services.TableNameValidator;
import modules.explorer.domain.services.TableSelectSqlBuilder;

import modules.query.domain.enums.TableChangeKeyEnum;
import modules.query.domain.entities.PreparedSqlEntity;
import modules.query.domain.entities.SqlStatementEntity;
import modules.query.domain.services.SqlStatementSplitter;
import modules.query.domain.services.TableChangesSqlBuilder;
import modules.query.domain.services.TableDataQueryBuilder;

/**
 * Lo que cambia por motor (MySQL, PostgreSQL, SQLite) en el SQL que trocea o genera multidbs.
 */
final class SqlDialectsTest extends AbstractTestCase {

    @Test
    void testTableNamesOnlyLettersDigitsAndUnderscore() {
        TableNameValidator mysqlValidator = TableNameValidator.getInstance("mysql");
        TableNameValidator mssqlValidator = TableNameValidator.getInstance("mssql");

        assertTrue(mysqlValidator.isValid("clientes_copy_20261126153045"));
        assertTrue(mysqlValidator.isValid("_tmp2"));
        assertFalse(mysqlValidator.isValid("con espacio"));
        assertFalse(mysqlValidator.isValid("clientes-2"));
        assertFalse(mysqlValidator.isValid("2clientes"));
        assertFalse(mysqlValidator.isValid("niño"));
        assertFalse(mysqlValidator.isValid("ventas.pedidos"));
        assertFalse(mysqlValidator.isValid("a".repeat(65)));
        assertTrue(mssqlValidator.isValid("ventas.pedidos_copy"));
        assertFalse(mssqlValidator.isValid("ventas.pedidos.x"));
        assertFalse(mssqlValidator.isValid("ventas."));
    }

    @Test
    void testDuplicateTableStatementsPerEngine() {
        TableDuplicateSqlBuilder sqliteBuilder = TableDuplicateSqlBuilder.getInstance("sqlite");
        String copyName = sqliteBuilder.getCopyTableName("clientes", "2026-11-26 15:30:45");
        List<String> sqliteStatements = sqliteBuilder.getStatements("main", "clientes", copyName,
            "CREATE TABLE \"clientes\" (id INTEGER PRIMARY KEY, txt TEXT);\nCREATE INDEX ix ON clientes (txt);", Set.of());

        assertEquals("clientes_copy_20261126153045", copyName);
        assertEquals(List.of(
            "CREATE TABLE \"clientes_copy_20261126153045\" (id INTEGER PRIMARY KEY, txt TEXT)",
            "INSERT INTO \"clientes_copy_20261126153045\" SELECT * FROM \"clientes\""
        ), sqliteStatements);
        assertEquals(List.of("CREATE TABLE `db`.`t_copy_1` LIKE `db`.`t`", "INSERT INTO `db`.`t_copy_1` SELECT * FROM `db`.`t`"),
            TableDuplicateSqlBuilder.getInstance("mysql").getStatements("db", "t", "t_copy_1", "", Set.of()));
        assertEquals(List.of("SELECT * INTO [db].[dbo].[t_copy_1] FROM [db].[dbo].[t]", "ALTER TABLE [db].[dbo].[t_copy_1] ADD PRIMARY KEY ([a], [b])"),
            TableDuplicateSqlBuilder.getInstance("mssql").getStatements("db", "t", "t_copy_1", "", new LinkedHashSet<>(List.of("a", "b"))));
    }

    @Test
    void testTreeSelectListsOneColumnPerLine() {
        assertEquals(
            "SELECT\n    `id`,\n    `full name`\nFROM `shop`.`users`\nLIMIT 1000;",
            TableSelectSqlBuilder.getInstance("mysql").getSelectSql("shop", "users", List.of("id", "full name"), 1000)
        );
        assertEquals(
            "SELECT TOP 10\n    [id]\nFROM [db].[ventas].[pedidos];",
            TableSelectSqlBuilder.getInstance("mssql").getSelectSql("db", "ventas.pedidos", List.of("id"), 10)
        );
        assertEquals("SELECT\n    *\nFROM \"public\".\"t\";", TableSelectSqlBuilder.getInstance("pgsql").getSelectSql("public", "t", List.of(), 0));
    }

    @Test
    void testSqlServerSplitsOnlyOnGoLinesAndQuotesWithBrackets() {
        List<String> sqls = getSqls("mssql", """
            DECLARE @n int = 1; SELECT @n;
            go
            CREATE PROCEDURE p AS BEGIN SELECT 1; SELECT 2; END
            GO 2
            SELECT [a;
            GO], 'x
            GO' FROM #tmp
            """);
        String quotedTable = SqlIdentifierQuoter.getInstance("mssql").getQuoted("db", "ventas.pe]didos");

        assertEquals(3, sqls.size());
        assertEquals("DECLARE @n int = 1; SELECT @n;", sqls.getFirst());
        assertTrue(sqls.get(1).endsWith("END"));
        assertTrue(sqls.get(2).endsWith("FROM #tmp"));
        assertEquals("[db].[ventas].[pe]]didos]", quotedTable);
        assertEquals("[dbo].[t]", SqlIdentifierQuoter.getInstance("mssql").getQuoted("", "t"));
        assertEquals("N'it''s \\ ok'", SqlLiteralQuoter.getInstance("mssql").getLiteral("it's \\ ok"));
        assertEquals("0xcafe", SqlLiteralQuoter.getInstance("mssql").getLiteral(new byte[] {(byte) 0xca, (byte) 0xfe}));
        assertEquals("SELECT TOP 5 * FROM [db].[dbo].[t]\nWHERE (a = 1)", TableDataQueryBuilder.getInstance("mssql").getSelectSql("db", "t", "a = 1;", "", 5));
    }

    @Test
    void testPostgresqlKeepsDollarBodiesAndHashOperators() {
        List<String> sqls = getSqls("pgsql", """
            CREATE FUNCTION f() RETURNS int AS $$ BEGIN RETURN 1; END; $$ LANGUAGE plpgsql;
            CREATE PROCEDURE p() LANGUAGE sql AS $body$ SELECT 1; SELECT 2; $body$;
            SELECT '{"a":1}'::jsonb #> '{a}', 'it\\''s';
            DELIMITER $$
            """);

        assertEquals(4, sqls.size());
        assertTrue(sqls.getFirst().endsWith("LANGUAGE plpgsql"));
        assertTrue(sqls.get(1).endsWith("$body$"));
        assertTrue(sqls.get(2).contains("#> '{a}'"));
        assertEquals("DELIMITER $$", sqls.get(3));
    }

    @Test
    void testSqliteTriggerRunsUntilItsEnd() {
        List<String> sqls = getSqls("sqlite", """
            CREATE TRIGGER tr AFTER INSERT ON t BEGIN
                UPDATE t SET a = 1;
                DELETE FROM u;
            END;
            SELECT 1;
            """);

        assertEquals(2, sqls.size());
        assertTrue(sqls.getFirst().endsWith("END"));
        assertEquals("SELECT 1", sqls.get(1));
    }

    @Test
    void testMysqlStillUsesDelimiterAndHashComments() {
        List<String> sqls = getSqls("mysql", "# comentario\nDELIMITER $$\nCREATE PROCEDURE p() BEGIN SELECT 1; END$$\nDELIMITER ;\nSELECT 2;");

        assertEquals(List.of("CREATE PROCEDURE p() BEGIN SELECT 1; END", "SELECT 2"), sqls);
    }

    @Test
    void testPostgresqlChangesUseDoubleQuotesNoLimitAndDefaultValues() {
        List<PreparedSqlEntity> statements = TableChangesSqlBuilder.getInstance("pgsql").getStatements("app", "notes", Set.of("id"), List.of(
            change(TableChangeKeyEnum.TYPE_UPDATE, Map.of("id", "1"), Map.of("txt", "x")),
            change(TableChangeKeyEnum.TYPE_DELETE, Map.of("id", "2"), Map.of()),
            change(TableChangeKeyEnum.TYPE_INSERT, Map.of(), Map.of())
        ));

        assertEquals("UPDATE \"app\".\"notes\" SET \"txt\" = ? WHERE \"id\" = ?", statements.getFirst().getSql());
        assertEquals("DELETE FROM \"app\".\"notes\" WHERE \"id\" = ?", statements.get(1).getSql());
        assertEquals("INSERT INTO \"app\".\"notes\" DEFAULT VALUES", statements.get(2).getSql());
        assertEquals("SELECT * FROM \"app\".\"notes\"\nLIMIT 10", TableDataQueryBuilder.getInstance("pgsql").getSelectSql("app", "notes", "", "", 10));
    }

    @Test
    void testSqliteLiteralsCarryNoBackslash() {
        assertEquals("'it''s ok'", SqlLiteralQuoter.getInstance("sqlite").getLiteral("it's ok"));
        assertEquals("('x' || char(92) || ''')')", SqlLiteralQuoter.getInstance("sqlite").getLiteral("x\\')"));
        assertEquals("(char(92) || 'a' || char(0))", SqlLiteralQuoter.getInstance("files").getLiteral("\\a\0"));
        assertEquals("\"c\" = ('x' || char(92) || '''); DROP TABLE t; --')",
            TableDataQueryBuilder.getInstance("sqlite").getColumnCondition("c", "=", "x\\'); DROP TABLE t; --"));
    }

    @Test
    void testPostgresqlLiteralsAreStandardAndDefinitionsUseTheCatalog() {
        assertEquals("'it''s \\ ok'", SqlLiteralQuoter.getInstance("pgsql").getLiteral("it's \\ ok"));
        assertEquals("'it\\'s \\\\ ok'", SqlLiteralQuoter.getInstance("mysql").getLiteral("it's \\ ok"));
        assertTrue(ObjectDefinitionSqlBuilder.getInstance("pgsql").getDefinitionSql(ExplorerNodeTypeEnum.FUNCTION, "app", "f").contains("pg_get_functiondef"));
        assertEquals("", ObjectDefinitionSqlBuilder.getInstance("pgsql").getDefinitionSql(ExplorerNodeTypeEnum.TABLE, "app", "t"));
        assertEquals("SHOW CREATE TABLE `app`.`t`;", ObjectDefinitionSqlBuilder.getInstance("mysql").getDefinitionSql(ExplorerNodeTypeEnum.TABLE, "app", "t"));
    }

    private Map<String, Object> change(String changeType, Map<String, String> keyValues, Map<String, String> newValues) {
        return Primitives.of(
            TableChangeKeyEnum.CHANGE_TYPE, changeType,
            TableChangeKeyEnum.KEY_VALUES, keyValues,
            TableChangeKeyEnum.NEW_VALUES, newValues
        );
    }

    private List<String> getSqls(String dbEngine, String text) {
        return SqlStatementSplitter.getInstance(dbEngine).getStatements(text).stream().map(SqlStatementEntity::getSql).toList();
    }
}
