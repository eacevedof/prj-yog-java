package modules.explorer.infrastructure;

import java.util.List;

import org.junit.jupiter.api.Test;

import modules.shared.application.Primitives;

import testcase.AbstractTestCase;

import modules.explorer.application.gettableinfo.GetTableInfoResultDto;
import modules.explorer.infrastructure.components.TableInfoFormatter;

/**
 * La pestaña Info (feature 10) a partir de lo que devuelve information_schema.
 */
final class TableInfoFormatterTest extends AbstractTestCase {

    @Test
    void testTableInfoShowsSizesIndexesAndDdl() {
        GetTableInfoResultDto tableInfo = GetTableInfoResultDto.fromPrimitives(Primitives.of(
            "databaseName", "shop",
            "tableName", "users",
            "isView", false,
            "createStatement", "CREATE TABLE `users` (...)",
            "indexes", List.of(
                Primitives.of("index_name", "PRIMARY", "non_unique", 0, "column_name", "id", "index_type", "BTREE"),
                Primitives.of("index_name", "ix_name", "non_unique", 1, "column_name", "last_name", "index_type", "BTREE"),
                Primitives.of("index_name", "ix_name", "non_unique", 1, "column_name", "first_name", "index_type", "BTREE"),
                Primitives.of("index_name", "uk_email", "non_unique", 0, "column_name", "email", "index_type", "BTREE")
            ),
            "status", Primitives.of("engine", "InnoDB", "table_rows", 1200, "data_length", 1_572_864L, "index_length", 16384L, "auto_increment", null)
        ));

        String text = TableInfoFormatter.getInstance().getText(tableInfo);

        assertTrue(text.contains("Engine:          InnoDB"));
        assertTrue(text.contains("Datos:           1.5 MB"));
        assertTrue(text.contains("PRIMARY KEY (id) BTREE"));
        assertTrue(text.contains("INDEX ix_name (last_name, first_name) BTREE"));
        assertTrue(text.contains("UNIQUE uk_email (email) BTREE"));
        assertTrue(text.endsWith("CREATE TABLE `users` (...);\n"));
        assertFalse(text.contains("Auto_increment"));
    }

    @Test
    void testSizeText() {
        TableInfoFormatter tableInfoFormatter = TableInfoFormatter.getInstance();

        assertEquals("512 B", tableInfoFormatter.getSizeText(512));
        assertEquals("2.0 KB", tableInfoFormatter.getSizeText(2048));
        assertEquals("1.0 GB", tableInfoFormatter.getSizeText(1L << 30));
    }
}
