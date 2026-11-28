package modules.history.domain;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import modules.shared.domain.services.SqlDialect;
import modules.shared.application.Primitives;

import testcase.AbstractTestCase;

import modules.query.domain.entities.SqlStatementEntity;
import modules.query.domain.services.SqlStatementSplitter;

import modules.history.domain.enums.QueryHistoryKeyEnum;
import modules.history.domain.services.QueryHistorySqlScriptBuilder;

/**
 * El historial como script: lo que se ejecuto bien sale ejecutable y cerrado con ";", y todo lo demas (errores,
 * ficheros SQL, copias, comentarios) dentro de /* *&#47;, de modo que al trocearlo solo quedan las sentencias a relanzar.
 */
final class QueryHistorySqlScriptBuilderTest extends AbstractTestCase {

    @Test
    void testOnlySuccessfulStatementsStayExecutableWithUseOnDatabaseChange() {
        List<Map<String, Object>> historyRows = List.of(
            getHistoryRow(1, "shop", "CREATE TABLE t (id INT)", false),
            getHistoryRow(2, "shop", "INSERT INTO t VALUES (1);", false),
            getHistoryRow(3, "shop", "UPDATE t SET nope = 1", true),
            getHistoryRow(4, "shop", "SOURCE C:/dumps/x.sql", false),
            getHistoryRow(5, "crm", "-- why\nDELETE FROM t WHERE id = 1 -- the old one", false),
            getHistoryRow(6, "crm", "-- copy database loc.`a` -> `b`", false)
        );

        String script = QueryHistorySqlScriptBuilder.getInstance(SqlDialect.MYSQL).getScript(historyRows, "2026-11-26 22:30:00");
        List<String> statements = SqlStatementSplitter.getInstance(SqlDialect.MYSQL).getStatements(script).stream()
            .map(SqlStatementEntity::getSql)
            .map(sql -> sql.replaceAll("(?s)/\\*.*?\\*/", "").strip())
            .toList();

        assertEquals(List.of(
            "USE `shop`",
            "CREATE TABLE t (id INT)",
            "INSERT INTO t VALUES (1)",
            "USE `crm`",
            "DELETE FROM t WHERE id = 1 -- the old one"
        ), statements);
        assertTrue(script.contains("ERROR, not replayed\nUPDATE t SET nope = 1\n*/"));
        assertTrue(script.contains("not a statement, not replayed\nSOURCE C:/dumps/x.sql\n*/"));
        assertTrue(script.contains("/* why */"));
    }

    @Test
    void testACommentEndInsideACommentedEntryDoesNotCloseIt() {
        List<Map<String, Object>> historyRows = List.of(getHistoryRow(1, "main", "SELECT '*/' FROM nope", true));

        String script = QueryHistorySqlScriptBuilder.getInstance(SqlDialect.SQLITE).getScript(historyRows, "2026-11-26 22:30:00");

        assertTrue(script.contains("SELECT '* /' FROM nope"));
        assertEquals(0, SqlStatementSplitter.getInstance(SqlDialect.SQLITE).getStatements(script).size());
    }

    private Map<String, Object> getHistoryRow(int id, String databaseName, String sql, boolean hasError) {
        return Primitives.of(
            QueryHistoryKeyEnum.ID, id,
            QueryHistoryKeyEnum.CONNECTION_NAME, "loc-test",
            QueryHistoryKeyEnum.DATABASE_NAME, databaseName,
            QueryHistoryKeyEnum.SQL_QUERY, sql,
            QueryHistoryKeyEnum.EXECUTED_AT, "2026-11-26 22:00:0" + id,
            QueryHistoryKeyEnum.ELAPSED_MS, 3,
            QueryHistoryKeyEnum.ROWS_COUNT, 1,
            QueryHistoryKeyEnum.HAS_ERROR, hasError
        );
    }
}
