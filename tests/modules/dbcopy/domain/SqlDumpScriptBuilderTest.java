package modules.dbcopy.domain;

import org.junit.jupiter.api.Test;

import testcase.AbstractTestCase;

import modules.dbcopy.domain.services.SqlDumpScriptBuilder;

/**
 * Los comentarios "--" del volcado no pueden partirse: un nombre del servidor con salto de linea dejaria SQL suelto
 * en el fichero (CVE-2016-5483 de mysqldump).
 */
final class SqlDumpScriptBuilderTest extends AbstractTestCase {

    private static final String HOSTILE_NAME = "x\nDROP DATABASE prod;\r\n-- ";

    private final SqlDumpScriptBuilder sqlDumpScriptBuilder = SqlDumpScriptBuilder.getInstance();

    @Test
    void testCommentsWithANewLineInTheNameStayOnOneLine() {
        assertEquals("\n-- Tabla `x DROP DATABASE prod;  -- `\n\n", sqlDumpScriptBuilder.getSectionComment("Tabla", HOSTILE_NAME));
        assertEquals("\n-- Datos de `x DROP DATABASE prod;  -- `\n", sqlDumpScriptBuilder.getDataComment(HOSTILE_NAME));
        assertEquals("-- ERROR `x DROP DATABASE prod;  -- `: line 1 line 2\n", sqlDumpScriptBuilder.getErrorComment(HOSTILE_NAME, "line 1\nline 2"));
    }

    @Test
    void testHeaderKeepsTheDatabaseNameOnItsCommentLine() {
        String header = sqlDumpScriptBuilder.getHeader(HOSTILE_NAME, "MySQL 8.4\nSELECT 1;", "2026-11-27 23:00:00", false);

        assertTrue(header.contains("\n-- BD: x DROP DATABASE prod;  -- \n"));
        assertTrue(header.contains("\n-- MySQL 8.4 SELECT 1;\n"));
        for (String line : header.split("\n")) {
            assertTrue(line.isEmpty() || line.startsWith("-- ") || line.startsWith("/*!"));
        }
    }
}
