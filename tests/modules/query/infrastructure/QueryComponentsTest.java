package modules.query.infrastructure;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.fxmisc.richtext.model.StyleSpan;
import org.fxmisc.richtext.model.StyleSpans;

import org.junit.jupiter.api.Test;

import testcase.AbstractTestCase;

import modules.query.infrastructure.components.ResultTextFormatter;
import modules.query.infrastructure.components.SqlHighlighter;

/**
 * El resultado en texto plano y el resaltado del editor (sin ventana: solo calculan).
 */
final class QueryComponentsTest extends AbstractTestCase {

    @Test
    void testTextTableAlignsColumnsAndShowsNull() {
        String table = ResultTextFormatter.getInstance().getTable(
            List.of("id", "email"),
            List.of(List.of("1", "a@b.es"), Arrays.asList("20", null))
        );

        String expected = """
            +----+--------+
            | id | email  |
            +----+--------+
            | 1  | a@b.es |
            | 20 | (NULL) |
            +----+--------+
            """;
        assertEquals(expected, table);
    }

    @Test
    void testTextTableCutsLongCellsAndFlattensNewLines() {
        String table = ResultTextFormatter.getInstance().getTable(List.of("txt"), List.of(List.of("a\nb" + "x".repeat(100))));

        assertTrue(table.contains("| a bxxx"));
        assertTrue(table.contains("...|") || table.contains("... |"));
    }

    @Test
    void testHighlighterCoversTheWholeTextWithItsClasses() {
        String sql = "SELECT `id`, 'x' FROM t WHERE n = 10 -- fin";

        StyleSpans<Collection<String>> styleSpans = SqlHighlighter.getInstance().getStyleSpans(sql);

        assertEquals(sql.length(), styleSpans.length());
        assertEquals(List.of("sql-keyword"), List.copyOf(getStyleAt(styleSpans, sql.indexOf("SELECT"))));
        assertEquals(List.of("sql-identifier"), List.copyOf(getStyleAt(styleSpans, sql.indexOf("`id`"))));
        assertEquals(List.of("sql-string"), List.copyOf(getStyleAt(styleSpans, sql.indexOf("'x'"))));
        assertEquals(List.of("sql-number"), List.copyOf(getStyleAt(styleSpans, sql.indexOf("10"))));
        assertEquals(List.of("sql-comment"), List.copyOf(getStyleAt(styleSpans, sql.indexOf("-- fin"))));
    }

    @Test
    void testHighlighterAcceptsEmptyText() {
        assertEquals(0, SqlHighlighter.getInstance().getStyleSpans("").length());
    }

    private Collection<String> getStyleAt(StyleSpans<Collection<String>> styleSpans, int position) {
        int spanStart = 0;
        for (StyleSpan<Collection<String>> styleSpan : styleSpans) {
            if (position < spanStart + styleSpan.getLength()) return styleSpan.getStyle();
            spanStart += styleSpan.getLength();
        }
        return List.of();
    }
}
