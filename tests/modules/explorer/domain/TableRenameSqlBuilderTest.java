package modules.explorer.domain;

import org.junit.jupiter.api.Test;

import modules.shared.domain.services.SqlDialect;

import testcase.AbstractTestCase;

import modules.explorer.domain.services.TableNameValidator;
import modules.explorer.domain.services.TableRenameSqlBuilder;

/**
 * F2 en el arbol: el SQL de renombrar una tabla o una columna en cada motor, con los nombres entrecomillados.
 */
final class TableRenameSqlBuilderTest extends AbstractTestCase {

    @Test
    void testRenameTableInEachEngine() {
        assertEquals("RENAME TABLE `db_shop`.`orders` TO `db_shop`.`orders_old`",
            TableRenameSqlBuilder.getInstance(SqlDialect.MYSQL).getRenameTableSql("db_shop", "orders", "orders_old"));
        assertEquals("ALTER TABLE \"public\".\"orders\" RENAME TO \"orders_old\"",
            TableRenameSqlBuilder.getInstance(SqlDialect.PGSQL).getRenameTableSql("public", "orders", "orders_old"));
        assertEquals("ALTER TABLE \"orders\" RENAME TO \"orders_old\"",
            TableRenameSqlBuilder.getInstance(SqlDialect.SQLITE).getRenameTableSql("main", "orders", "orders_old"));
        assertEquals("EXEC [db_shop].sys.sp_rename N'[sales].[orders]', N'orders_old'",
            TableRenameSqlBuilder.getInstance(SqlDialect.MSSQL).getRenameTableSql("db_shop", "sales.orders", "sales.orders_old"));
    }

    @Test
    void testRenameColumnInEachEngine() {
        assertEquals("ALTER TABLE `db_shop`.`orders` RENAME COLUMN `total` TO `amount`",
            TableRenameSqlBuilder.getInstance(SqlDialect.MYSQL).getRenameColumnSql("db_shop", "orders", "total", "amount"));
        assertEquals("ALTER TABLE \"public\".\"orders\" RENAME COLUMN \"total\" TO \"amount\"",
            TableRenameSqlBuilder.getInstance(SqlDialect.PGSQL).getRenameColumnSql("public", "orders", "total", "amount"));
        assertEquals("EXEC [db_shop].sys.sp_rename N'[dbo].[orders].[it''s]', N'amount', N'COLUMN'",
            TableRenameSqlBuilder.getInstance(SqlDialect.MSSQL).getRenameColumnSql("db_shop", "orders", "it's", "amount"));
    }

    @Test
    void testColumnNamesHaveNoSchema() {
        TableNameValidator tableNameValidator = TableNameValidator.getInstance(SqlDialect.MSSQL);

        assertTrue(tableNameValidator.isValidColumn("amount_2"));
        assertFalse(tableNameValidator.isValidColumn("dbo.amount"));
        assertFalse(tableNameValidator.isValidColumn("2amount"));
        assertFalse(tableNameValidator.isValidColumn("my amount"));
    }
}
