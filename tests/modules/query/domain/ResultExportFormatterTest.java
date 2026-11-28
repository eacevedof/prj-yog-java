package modules.query.domain;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import modules.shared.application.Primitives;

import testcase.AbstractTestCase;

import modules.query.domain.enums.ResultExportFormatEnum;
import modules.query.domain.entities.ResultExportOptionsEntity;
import modules.query.domain.services.ExportSeparatorEscaper;
import modules.query.domain.services.ResultExportFormatter;

/**
 * "Exportar las filas como..." de la rejilla (feature 9): cada formato escapa lo suyo y distingue NULL de vacio.
 */
final class ResultExportFormatterTest extends AbstractTestCase {

    private static final List<String> COLUMNS = List.of("id", "name");
    private static final List<List<String>> ROWS = List.of(
        Arrays.asList("1", "O'Brien, \"Bob\""),
        Arrays.asList("2", null)
    );

    private final ResultExportFormatter resultExportFormatter = ResultExportFormatter.getInstance();

    @Test
    void testCsvOptionsLikeSqlyog() {
        // los separadores llegan como en el dialogo: \t, <NONE>, \\, \r\n
        ResultExportOptionsEntity tabOptions = ResultExportOptionsEntity.fromPrimitives(Primitives.of(
            "fieldsTerminatedBy", "\\t", "enclosedBy", "<NONE>", "escapedBy", "\\\\", "linesTerminatedBy", "\\r\\n",
            "nullReplacedBy", "NULL", "isHeaderIncluded", false
        ));
        ResultExportOptionsEntity ownEscapeOptions = ResultExportOptionsEntity.fromPrimitives(Primitives.of(
            "fieldsTerminatedBy", ";", "enclosedBy", "'", "escapedBy", "\\\\"
        ));
        List<List<String>> rows = List.of(Arrays.asList("a\tb", null), Arrays.asList("x;y", "it's"));

        assertEquals("a\\\tb\tNULL\r\nx;y\tit's\r\n", resultExportFormatter.getFormatted(ResultExportFormatEnum.CSV, COLUMNS, rows, tabOptions));
        assertEquals("id;name\n'x;y';'it\\'s'\n", resultExportFormatter.getFormatted(ResultExportFormatEnum.CSV, COLUMNS, rows.subList(1, 2), ownEscapeOptions));
        assertEquals("\\N", ResultExportOptionsEntity.fromPrimitives(Primitives.of()).getNullReplacedBy());
        assertEquals("\\t", ExportSeparatorEscaper.getInstance().getEscaped("\t"));
        assertEquals("<NONE>", ExportSeparatorEscaper.getInstance().getEscaped(""));
    }

    @Test
    void testExcelXmlTypesNumbersAndSqlBatchesRows() {
        ResultExportOptionsEntity options = ResultExportOptionsEntity.fromPrimitives(Primitives.of(
            "tableName", "users", "decimalPlaces", 3, "rowsPerInsert", 2, "charset", "windows-1252"
        ));
        List<List<String>> rows = List.of(Arrays.asList("1.5", "08001"), Arrays.asList("2", null), Arrays.asList("3", "c"));

        String excel = resultExportFormatter.getFormatted(ResultExportFormatEnum.EXCEL_XML, COLUMNS, rows, options);
        String sql = resultExportFormatter.getFormatted(ResultExportFormatEnum.SQL, COLUMNS, rows, options);

        assertTrue(excel.startsWith("<?xml version=\"1.0\" encoding=\"windows-1252\"?>"));
        assertTrue(excel.contains("<Worksheet ss:Name=\"users\">"));
        assertTrue(excel.contains("<NumberFormat ss:Format=\"0.000\"/>"));
        assertTrue(excel.contains("<Cell ss:StyleID=\"decimal\"><Data ss:Type=\"Number\">1.5</Data></Cell><Cell><Data ss:Type=\"String\">08001</Data></Cell>"));
        assertTrue(excel.contains("<Cell><Data ss:Type=\"Number\">2</Data></Cell><Cell/>"));
        assertEquals(
            "INSERT INTO `users` (`id`, `name`) VALUES \n('1.5', '08001'),\n('2', NULL);\nINSERT INTO `users` (`id`, `name`) VALUES \n('3', 'c');\n",
            sql
        );
        assertEquals("xml", ResultExportFormatter.getInstance().getExtension(ResultExportFormatEnum.EXCEL_XML));
    }

    @Test
    void testCsvQuotesWhenNeededAndNullIsBackslashN() {
        String csv = resultExportFormatter.getFormatted(ResultExportFormatEnum.CSV, "users", COLUMNS, ROWS);

        assertEquals("id,name\n1,\"O'Brien, \"\"Bob\"\"\"\n2,\\N\n", csv);
    }

    @Test
    void testJsonEscapesQuotesAndKeepsNull() {
        String json = resultExportFormatter.getFormatted(ResultExportFormatEnum.JSON, "users", COLUMNS, ROWS);

        assertEquals("[\n  {\"id\": \"1\", \"name\": \"O'Brien, \\\"Bob\\\"\"},\n  {\"id\": \"2\", \"name\": null}\n]\n", json);
    }

    @Test
    void testSqlIsOneInsertPerRowWithEscapedLiterals() {
        String sql = resultExportFormatter.getFormatted(ResultExportFormatEnum.SQL, "users", COLUMNS, ROWS);

        assertEquals(
            "INSERT INTO `users` (`id`, `name`) VALUES ('1', 'O\\'Brien, \"Bob\"');\n"
                + "INSERT INTO `users` (`id`, `name`) VALUES ('2', NULL);\n",
            sql
        );
    }

    @Test
    void testSqlPutsTheCreateTableBeforeTheInsertsAndCsvCopyLeavesNullEmpty() {
        ResultExportOptionsEntity options = ResultExportOptionsEntity.fromPrimitives(Primitives.of(
            "tableName", "users",
            "createTableSql", "CREATE TABLE `users` (`id` int, `name` varchar(20))"
        ));
        ResultExportOptionsEntity copyOptions = ResultExportOptionsEntity.fromPrimitives(Primitives.of(
            "nullReplacedBy", ResultExportOptionsEntity.NONE
        ));

        String sql = resultExportFormatter.getFormatted(ResultExportFormatEnum.SQL, COLUMNS, ROWS, options);
        String csv = resultExportFormatter.getFormatted(ResultExportFormatEnum.CSV, COLUMNS, List.of(Arrays.asList("2", null)), copyOptions);

        assertTrue(sql.startsWith("CREATE TABLE `users` (`id` int, `name` varchar(20));\n\nINSERT INTO `users`"));
        assertEquals("id,name\n2,\n", csv);
    }

    @Test
    void testXmlAndHtmlEscapeMarkupAndMarkNull() {
        List<List<String>> rows = List.of(Arrays.asList("<b>", null));

        String xml = resultExportFormatter.getFormatted(ResultExportFormatEnum.XML, "users", COLUMNS, rows);
        String html = resultExportFormatter.getFormatted(ResultExportFormatEnum.HTML, "users", COLUMNS, rows);

        assertTrue(xml.contains("<field name=\"id\">&lt;b&gt;</field>"));
        assertTrue(xml.contains("<field name=\"name\" xsi:nil=\"true\"/>"));
        assertTrue(html.contains("<td>&lt;b&gt;</td><td class=\"null\">(NULL)</td>"));
        assertThrows(IllegalArgumentException.class, () -> resultExportFormatter.getFormatted("pdf", "users", COLUMNS, rows));
    }
}
