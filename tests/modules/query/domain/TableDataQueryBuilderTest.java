package modules.query.domain;

import java.util.List;

import org.junit.jupiter.api.Test;

import testcase.AbstractTestCase;

import modules.query.domain.enums.GridFilterOperatorEnum;
import modules.query.domain.services.TableDataQueryBuilder;

/**
 * El SELECT de la pestaña Datos (feature 7).
 */
final class TableDataQueryBuilderTest extends AbstractTestCase {

    private final TableDataQueryBuilder tableDataQueryBuilder = TableDataQueryBuilder.getInstance();

    @Test
    void testWithoutFilterOnlyLimits() {
        assertEquals("SELECT * FROM `shop`.`users`\nLIMIT 1000", tableDataQueryBuilder.getSelectSql("shop", "users", "", " ", 1000));
    }

    @Test
    void testFilterGoesInParenthesesAndTrailingSemicolonsAreDropped() {
        String selectSql = tableDataQueryBuilder.getSelectSql("shop", "users", "a = 1 OR b = 2;", "id DESC;", 50);

        assertEquals("SELECT * FROM `shop`.`users`\nWHERE (a = 1 OR b = 2)\nORDER BY id DESC\nLIMIT 50", selectSql);
    }

    @Test
    void testColumnFilterQuotesTheValueWrapsLikeAndTurnsNullIntoIsNull() {
        assertEquals("`name` = 'O\\'Brien'", tableDataQueryBuilder.getColumnCondition("name", GridFilterOperatorEnum.EQUAL, "O'Brien"));
        assertEquals("`name` NOT LIKE '%an%'", tableDataQueryBuilder.getColumnCondition("name", GridFilterOperatorEnum.NOT_LIKE, "an"));
        assertEquals("`id` > '10'", tableDataQueryBuilder.getColumnCondition("id", GridFilterOperatorEnum.GREATER, "10"));
        assertEquals("`deleted_at` IS NULL", tableDataQueryBuilder.getColumnCondition("deleted_at", GridFilterOperatorEnum.EQUAL, null));
        assertEquals("`deleted_at` IS NOT NULL", tableDataQueryBuilder.getColumnCondition("deleted_at", GridFilterOperatorEnum.NOT_EQUAL, null));
        assertThrows(IllegalArgumentException.class, () -> tableDataQueryBuilder.getColumnCondition("id", "; DROP", "1"));
    }

    @Test
    void testNamesAreQuotedAndZeroLimitMeansNoLimit() {
        assertEquals("SELECT * FROM `my-db`.`we``ird`", tableDataQueryBuilder.getSelectSql("my-db", "we`ird", null, null, 0));
    }

    @Test
    void testWithoutOrderByGoesByPrimaryKeySoAnEditedRowKeepsItsPlace() {
        TableDataQueryBuilder pgsqlTableDataQueryBuilder = TableDataQueryBuilder.getInstance("pgsql");

        assertEquals(
            "SELECT * FROM \"shop\".\"lines\"\nORDER BY \"order_id\", \"line\"\nLIMIT 1000",
            pgsqlTableDataQueryBuilder.getSelectSqlOrderedByKey("shop", "lines", "", "", 1000, List.of("order_id", "line"))
        );
        assertEquals(
            "SELECT * FROM \"shop\".\"lines\"\nORDER BY line DESC\nLIMIT 1000",
            pgsqlTableDataQueryBuilder.getSelectSqlOrderedByKey("shop", "lines", "", "line DESC", 1000, List.of("order_id", "line"))
        );
        assertEquals(
            "SELECT * FROM \"shop\".\"logs\"\nLIMIT 1000",
            pgsqlTableDataQueryBuilder.getSelectSqlOrderedByKey("shop", "logs", "", "", 1000, List.of())
        );
    }
}
