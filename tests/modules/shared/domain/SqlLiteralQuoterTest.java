package modules.shared.domain;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import testcase.AbstractTestCase;

import modules.shared.domain.services.SqlLiteralQuoter;

/**
 * Feature 13: por el tunel HTTP no hay sentencias preparadas y los ? se sustituyen por literales.
 */
final class SqlLiteralQuoterTest extends AbstractTestCase {

    private final SqlLiteralQuoter sqlLiteralQuoter = SqlLiteralQuoter.getInstance();

    @Test
    void testLiteralsEscapeQuotesBackslashesAndControlChars() {
        assertEquals("NULL", sqlLiteralQuoter.getLiteral(null));
        assertEquals("42", sqlLiteralQuoter.getLiteral(42L));
        assertEquals("1", sqlLiteralQuoter.getLiteral(true));
        assertEquals("12.50", sqlLiteralQuoter.getLiteral(new BigDecimal("12.50")));
        assertEquals("X'cafe'", sqlLiteralQuoter.getLiteral(new byte[]{(byte) 0xCA, (byte) 0xFE}));
        assertEquals("'it\\'s \\\\ a\\nb\\0'", sqlLiteralQuoter.getLiteral("it's \\ a\nb\0"));
        // un numero que llega como texto sigue siendo texto: MySQL lo convierte al comparar
        assertEquals("'7'", sqlLiteralQuoter.getLiteral("7"));
    }

    @Test
    void testOnlyPlaceholdersOutsideQuotesAndCommentsAreReplaced() {
        String sql = """
            -- getX ?
            SELECT '?', `a?b`, "?" /* ? */ FROM t WHERE a = ? AND b = ? # ?
            """;

        String inlined = sqlLiteralQuoter.getInlined(sql, Arrays.asList("x'y", null));

        assertTrue(inlined.contains("-- getX ?"));
        assertTrue(inlined.contains("SELECT '?', `a?b`, \"?\" /* ? */ FROM t WHERE a = 'x\\'y' AND b = NULL # ?"));
    }

    @Test
    void testParamCountMustMatch() {
        assertThrows(IllegalArgumentException.class, () -> sqlLiteralQuoter.getInlined("SELECT ?", List.of()));
        assertThrows(IllegalArgumentException.class, () -> sqlLiteralQuoter.getInlined("SELECT 1", List.of(1)));
    }
}
