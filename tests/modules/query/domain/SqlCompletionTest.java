package modules.query.domain;

import org.junit.jupiter.api.Test;

import testcase.AbstractTestCase;

import modules.query.domain.entities.SqlCompletionContextEntity;
import modules.query.domain.services.FuzzyMatcher;
import modules.query.domain.services.SqlCompletionAnalyzer;

/**
 * El autocompletado (feature 41): alias y contexto, y el ranking contiene/fuzzy.
 */
final class SqlCompletionTest extends AbstractTestCase {

    private final SqlCompletionAnalyzer sqlCompletionAnalyzer = SqlCompletionAnalyzer.getInstance();
    private final FuzzyMatcher fuzzyMatcher = FuzzyMatcher.getInstance();

    @Test
    void testAliasDefinedAfterTheCaretResolvesToItsTable() {
        String sql = "SELECT c.em FROM customers c JOIN shop.orders AS o ON o.customer_id = c.id";

        SqlCompletionContextEntity context = sqlCompletionAnalyzer.getContext(sql, sql.indexOf("em") + 2);

        assertEquals("em", context.getPrefix());
        assertEquals("c", context.getQualifier());
        assertEquals("customers", context.getTablesByAlias().get("c")[1]);
        assertEquals("shop", context.getTablesByAlias().get("o")[0]);
        assertEquals("orders", context.getTablesByAlias().get("o")[1]);
        assertFalse(context.isTableExpected());
    }

    @Test
    void testCommaListInFromAndClauseWordsAreNotAliases() {
        String sql = "SELECT * FROM a x, `b b` WHERE x.id = 1";

        SqlCompletionContextEntity context = sqlCompletionAnalyzer.getContext(sql, sql.length());

        assertEquals("a", context.getTablesByAlias().get("x")[1]);
        assertEquals("b b", context.getTablesByAlias().get("b b")[1]);
        assertFalse(context.getTablesByAlias().containsKey("where"));
    }

    @Test
    void testTableIsExpectedAfterFromJoinAndFromComma() {
        assertTrue(sqlCompletionAnalyzer.getContext("SELECT * FROM cus", 17).isTableExpected());
        assertTrue(sqlCompletionAnalyzer.getContext("SELECT * FROM a, ", 17).isTableExpected());
        assertTrue(sqlCompletionAnalyzer.getContext("UPDATE ", 7).isTableExpected());
        assertFalse(sqlCompletionAnalyzer.getContext("SELECT * FROM a WHERE ", 22).isTableExpected());
        assertFalse(sqlCompletionAnalyzer.getContext("SELECT 'from ' ", 15).isTableExpected());
    }

    @Test
    void testQualifierCanBeADatabaseAndQuotedNamesAreUnquoted() {
        SqlCompletionContextEntity context = sqlCompletionAnalyzer.getContext("SELECT * FROM `my db`.us", 24);

        assertEquals("my db", context.getQualifier());
        assertEquals("us", context.getPrefix());
    }

    @Test
    void testFuzzyRanking() {
        assertTrue(fuzzyMatcher.getScore("created_at", "cre") > fuzzyMatcher.getScore("user_created", "cre"));
        assertTrue(fuzzyMatcher.getScore("user_created", "cre") > fuzzyMatcher.getScore("recreate", "cre"));
        assertTrue(fuzzyMatcher.getScore("recreate", "cre") > fuzzyMatcher.getScore("customer_role_end", "cre"));
        assertTrue(fuzzyMatcher.getScore("user_created_at", "uca") > 0);
        assertEquals(-1, fuzzyMatcher.getScore("email", "xyz"));
        assertEquals(0, fuzzyMatcher.getScore("email", ""));
    }
}
