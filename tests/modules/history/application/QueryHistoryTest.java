package modules.history.application;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import modules.shared.application.Primitives;
import static modules.shared.application.Primitives.getBool;

import testcase.AbstractTestCase;

import modules.history.domain.enums.QueryHistoryKeyEnum;
import modules.history.application.clearqueryhistory.ClearQueryHistoryDto;
import modules.history.application.listqueryhistory.ListQueryHistoryDto;
import modules.history.application.recordqueryhistory.RecordQueryHistoryDto;
import modules.history.application.clearqueryhistory.ClearQueryHistoryService;
import modules.history.application.listqueryhistory.ListQueryHistoryService;
import modules.history.application.recordqueryhistory.RecordQueryHistoryService;
import modules.history.infrastructure.repositories.QueryHistoryWriterSqliteRepository;

/**
 * El historial de queries (feature 8) contra la BD local de tests.
 */
final class QueryHistoryTest extends AbstractTestCase {

    @BeforeEach
    void bootDatabase() {
        bootFreshDatabase("QueryHistoryTest");
    }

    @Test
    void testListsNewestFirstFilteredByConnectionAndSearch() {
        insertHistory("conn-a", "SELECT * FROM users");
        insertHistory("conn-b", "SELECT * FROM orders");
        insertHistory("conn-a", "UPDATE users SET a = 1 WHERE id = 2");

        List<Map<String, Object>> allOfA = listHistory("conn-a", "");
        List<Map<String, Object>> usersEverywhere = listHistory("", "USERS");

        assertEquals(2, allOfA.size());
        assertTrue(String.valueOf(allOfA.getFirst().get(QueryHistoryKeyEnum.SQL_QUERY)).startsWith("UPDATE"));
        assertEquals(2, usersEverywhere.size());
    }

    @Test
    void testSearchTreatsPercentAndUnderscoreLiterally() {
        insertHistory("conn-a", "SELECT 'x_y'");
        insertHistory("conn-a", "SELECT 'xzy'");

        assertEquals(1, listHistory("", "x_y").size());
        assertEquals(0, listHistory("", "%z%y_").size());
    }

    @Test
    void testKeepsOnlyTheLastRows() {
        QueryHistoryWriterSqliteRepository queryHistoryWriterSqliteRepository = QueryHistoryWriterSqliteRepository.getInstance();
        for (int i = 1; i <= 5; i++) {
            insertHistory("conn-a", "SELECT " + i);
        }

        queryHistoryWriterSqliteRepository.deleteOlderThanLast(3);

        List<Map<String, Object>> history = listHistory("", "");
        assertEquals(3, history.size());
        assertEquals("SELECT 5", history.getFirst().get(QueryHistoryKeyEnum.SQL_QUERY));
        assertEquals("SELECT 3", history.getLast().get(QueryHistoryKeyEnum.SQL_QUERY));
    }

    @Test
    void testKeepsTheLastRowsEvenWithIdGaps() {
        QueryHistoryWriterSqliteRepository queryHistoryWriterSqliteRepository = QueryHistoryWriterSqliteRepository.getInstance();
        insertHistory("conn-a", "SELECT 1");
        insertHistory("conn-b", "SELECT 2");
        insertHistory("conn-b", "SELECT 3");
        insertHistory("conn-a", "SELECT 4");
        clearHistory("conn-b");

        queryHistoryWriterSqliteRepository.deleteOlderThanLast(2);
        int keptWithFewerRows = queryHistoryWriterSqliteRepository.deleteOlderThanLast(5);

        List<Map<String, Object>> history = listHistory("", "");
        assertEquals(2, history.size());
        assertEquals("SELECT 4", history.getFirst().get(QueryHistoryKeyEnum.SQL_QUERY));
        assertEquals("SELECT 1", history.getLast().get(QueryHistoryKeyEnum.SQL_QUERY));
        assertEquals(0, keptWithFewerRows);
    }

    @Test
    void testClearsTheHistoryOfOneConnectionOrOfAll() {
        insertHistory("conn-a", "SELECT 1");
        insertHistory("conn-a", "SELECT 2");
        insertHistory("conn-b", "SELECT 3");

        int deletedOfA = clearHistory("conn-a");

        assertEquals(2, deletedOfA);
        assertEquals(0, listHistory("conn-a", "").size());
        assertEquals(1, listHistory("conn-b", "").size());
        assertEquals(1, clearHistory(""));
        assertEquals(0, listHistory("", "").size());
    }

    @Test
    void testRecordsAWriteWithItsErrorFlag() {
        RecordQueryHistoryService.getInstance().invoke(RecordQueryHistoryDto.fromPrimitives(Primitives.of(
            "connectionUuid", "conn-unknown",
            "databaseName", "shop",
            "sqlQuery", "ALTER TABLE `users` ADD `age` INT",
            "elapsedMs", 7L,
            "hasError", true
        )));

        Map<String, Object> recorded = listHistory("conn-unknown", "").getFirst();
        assertEquals("ALTER TABLE `users` ADD `age` INT", recorded.get(QueryHistoryKeyEnum.SQL_QUERY));
        assertEquals("shop", recorded.get(QueryHistoryKeyEnum.DATABASE_NAME));
        assertTrue(getBool(recorded.get(QueryHistoryKeyEnum.HAS_ERROR)));
    }

    private int clearHistory(String connectionUuid) {
        return ClearQueryHistoryService.getInstance().invoke(ClearQueryHistoryDto.fromPrimitives(Primitives.of(
            "connectionUuid", connectionUuid
        ))).getDeletedCount();
    }

    private void insertHistory(String connectionUuid, String sql) {
        QueryHistoryWriterSqliteRepository.getInstance().insertHistory(Primitives.of(
            QueryHistoryKeyEnum.CONNECTION_UUID, connectionUuid,
            QueryHistoryKeyEnum.CONNECTION_NAME, "loc-" + connectionUuid,
            QueryHistoryKeyEnum.DATABASE_NAME, "shop",
            QueryHistoryKeyEnum.SQL_QUERY, sql,
            QueryHistoryKeyEnum.EXECUTED_AT, "2026-11-25 10:00:00",
            QueryHistoryKeyEnum.ELAPSED_MS, 12L,
            QueryHistoryKeyEnum.ROWS_COUNT, 3,
            QueryHistoryKeyEnum.HAS_ERROR, false
        ));
    }

    private List<Map<String, Object>> listHistory(String connectionUuid, String searchText) {
        return ListQueryHistoryService.getInstance().invoke(ListQueryHistoryDto.fromPrimitives(Primitives.of(
            "connectionUuid", connectionUuid, "searchText", searchText
        ))).getHistory();
    }
}
