package modules.query.domain;

import org.junit.jupiter.api.Test;

import testcase.AbstractTestCase;

import modules.query.domain.services.JsonFormatter;

/**
 * El JSON de una celda formateado (feature 43).
 */
final class JsonFormatterTest extends AbstractTestCase {

    private final JsonFormatter jsonFormatter = JsonFormatter.getInstance();

    @Test
    void testFormatsNestedObjectsAndArrays() {
        String prettyJson = jsonFormatter.getPrettyJson("{\"a\":1,\"b\":[true,null,{\"c\":\"x, y: {z}\"}],\"d\":{}}");

        String expected = """
            {
              "a": 1,
              "b": [
                true,
                null,
                {
                  "c": "x, y: {z}"
                }
              ],
              "d": {}
            }""";
        assertEquals(expected, prettyJson);
    }

    @Test
    void testKeepsEscapesInStrings() {
        assertEquals("[\n  \"say \\\"hi\\\"\",\n  -1.5e3\n]", jsonFormatter.getPrettyJson(" [\"say \\\"hi\\\"\", -1.5e3] "));
    }

    @Test
    void testInvalidOrScalarValuesAreNotJson() {
        assertFalse(jsonFormatter.isJson("{\"a\":}"));
        assertFalse(jsonFormatter.isJson("{\"a\":1"));
        assertFalse(jsonFormatter.isJson("[1, 2] extra"));
        assertFalse(jsonFormatter.isJson("{a: 1}"));
        assertFalse(jsonFormatter.isJson("42"));
        assertFalse(jsonFormatter.isJson("\"texto\""));
        assertFalse(jsonFormatter.isJson(null));
        assertTrue(jsonFormatter.isJson("[]"));
    }
}
