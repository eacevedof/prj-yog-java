package modules.query.domain;

import java.util.List;

import org.junit.jupiter.api.Test;

import testcase.AbstractTestCase;

import modules.query.domain.entities.SqlStatementEntity;
import modules.query.domain.services.SqlStatementSplitter;

/**
 * El troceo del editor en sentencias: lo que decide que se manda al servidor con F9 y con Ctrl+F9.
 */
final class SqlStatementSplitterTest extends AbstractTestCase {

    private final SqlStatementSplitter sqlStatementSplitter = SqlStatementSplitter.getInstance();

    @Test
    void testSplitsBySemicolonAndIgnoresTheLastEmptyOne() {
        List<String> sqls = getSqls("SELECT 1;\nSELECT 2;\n");

        assertEquals(List.of("SELECT 1", "SELECT 2"), sqls);
    }

    @Test
    void testKeepsTheLastStatementWithoutSemicolon() {
        assertEquals(List.of("SELECT 1", "SELECT 2"), getSqls("SELECT 1; SELECT 2"));
    }

    @Test
    void testDoesNotSplitInsideQuotesOrBackticks() {
        String sql = "SELECT 'a;b', \"c;d\", `e;f` FROM t; SELECT 'it''s; ok', 'x\\';y'";

        assertEquals(List.of("SELECT 'a;b', \"c;d\", `e;f` FROM t", "SELECT 'it''s; ok', 'x\\';y'"), getSqls(sql));
    }

    @Test
    void testDoesNotSplitInsideComments() {
        String sql = "SELECT 1 -- uno; dos\n; /* a; b */ SELECT 2; # tres; cuatro\nSELECT 3";

        assertEquals(List.of("SELECT 1 -- uno; dos", "/* a; b */ SELECT 2", "# tres; cuatro\nSELECT 3"), getSqls(sql));
    }

    @Test
    void testSkipsStatementsWithOnlyComments() {
        assertEquals(List.of("SELECT 1"), getSqls("-- solo un comentario;\n/* otro */;\nSELECT 1;"));
    }

    @Test
    void testDoubleDashWithoutSpaceIsNotAComment() {
        assertEquals(List.of("SELECT 5--1", "SELECT 2"), getSqls("SELECT 5--1; SELECT 2"));
    }

    @Test
    void testDelimiterCommandChangesTheDelimiterForRoutines() {
        String sql = """
            DELIMITER $$
            CREATE PROCEDURE p()
            BEGIN
                SELECT 1;
                SELECT 2;
            END$$
            DELIMITER ;
            CALL p();
            """;

        List<String> sqls = getSqls(sql);

        assertEquals(2, sqls.size());
        assertTrue(sqls.getFirst().startsWith("CREATE PROCEDURE p()"));
        assertTrue(sqls.getFirst().endsWith("END"));
        assertEquals("CALL p()", sqls.get(1));
    }

    @Test
    void testStatementAtCaretIsTheOneContainingIt() {
        String sql = "SELECT 1;\n\nSELECT 2;\n\nSELECT 3;";

        assertEquals("SELECT 2", sqlStatementSplitter.getStatementAt(sql, sql.indexOf("2")).getSql());
        assertEquals("SELECT 1", sqlStatementSplitter.getStatementAt(sql, 0).getSql());
        assertEquals("SELECT 3", sqlStatementSplitter.getStatementAt(sql, sql.length()).getSql());
    }

    @Test
    void testStatementAtCaretBetweenTwoIsThePreviousOne() {
        String sql = "SELECT 1;\n\n\nSELECT 2;";

        assertEquals("SELECT 1", sqlStatementSplitter.getStatementAt(sql, sql.indexOf("\n\n") + 1).getSql());
    }

    @Test
    void testStatementPositionsPointToTheEditorText() {
        String sql = "  SELECT 1;   SELECT 2";
        SqlStatementEntity secondStatement = sqlStatementSplitter.getStatements(sql).get(1);

        assertEquals(sql.indexOf("SELECT 2"), secondStatement.getStartIndex());
        assertEquals("SELECT 2", sql.substring(secondStatement.getStartIndex(), secondStatement.getStartIndex() + secondStatement.getSql().length()));
    }

    @Test
    void testEmptyTextHasNoStatements() {
        assertTrue(sqlStatementSplitter.getStatements("  \n ").isEmpty());
        assertEquals(null, sqlStatementSplitter.getStatementAt("", 0));
    }

    private List<String> getSqls(String text) {
        return sqlStatementSplitter.getStatements(text).stream().map(SqlStatementEntity::getSql).toList();
    }
}
