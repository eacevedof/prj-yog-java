package modules.history.application;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import modules.shared.application.Primitives;
import static modules.shared.application.Primitives.getBool;
import static modules.shared.application.Primitives.getInt;

import testcase.AbstractTestCase;

import modules.connections.domain.enums.ConnectionsKeyEnum;
import modules.connections.application.createconnection.CreateConnectionDto;
import modules.connections.application.createconnection.CreateConnectionService;
import modules.connections.infrastructure.factories.TargetSessionFactory;

import modules.explorer.application.listdatabaseobjects.ListDatabaseObjectsDto;
import modules.explorer.application.listtablecolumns.ListTableColumnsDto;
import modules.explorer.application.listdatabaseobjects.ListDatabaseObjectsService;
import modules.explorer.application.listtablecolumns.ListTableColumnsService;

import modules.query.application.executequery.ExecuteQueryDto;
import modules.query.application.executequery.ExecuteQueryService;

import modules.history.domain.enums.QueryHistoryKeyEnum;
import modules.history.application.listqueryhistory.ListQueryHistoryDto;
import modules.history.application.listqueryhistory.ListQueryHistoryService;
import modules.history.application.recordqueryhistory.QueryHistoryTracer;

/**
 * Feature 8: lo que lanzan por debajo las acciones "automaticas" (arbol, columnas, Info...) tambien queda en el
 * historial, con el SQL real y los parametros ya puestos. Sobre una conexion SQLite.
 */
final class QueryHistoryTracerTest extends AbstractTestCase {

    private static final String MAIN = "main";
    private static final String SESSION = "history-tracer";

    private static String connectionUuid;

    @BeforeAll
    static void createSqliteConnection() throws Exception {
        bootFreshDatabase("QueryHistoryTracerTest");
        Path sqliteFile = Path.of("target", "javayog-lite-history-tracer.sqlite").toAbsolutePath();
        Files.deleteIfExists(sqliteFile);
        Files.createFile(sqliteFile);
        connectionUuid = String.valueOf(CreateConnectionService.getInstance().invoke(CreateConnectionDto.fromPrimitives(Primitives.of(
            "connectionName", "lite-history-tracer", "dbEngine", "sqlite", "databaseName", sqliteFile.toString()
        ))).getConnection().get(ConnectionsKeyEnum.CONNECTION_UUID));
        ExecuteQueryService.getInstance().invoke(ExecuteQueryDto.fromPrimitives(Primitives.of(
            "connectionUuid", connectionUuid,
            "sessionKey", SESSION,
            "databaseName", MAIN,
            "sqlQuery", "CREATE TABLE items (id INTEGER PRIMARY KEY, name TEXT)",
            "maxRows", 100
        )));
    }

    @AfterAll
    static void closeSessions() {
        TargetSessionFactory.getInstance().closeAll();
    }

    @Test
    void testTreeAndColumnReadsAreRecordedWithTheirRealSql() throws Exception {
        int historyBefore = getHistory().size();

        ListDatabaseObjectsService.getInstance().invoke(ListDatabaseObjectsDto.fromPrimitives(Primitives.of(
            "connectionUuid", connectionUuid, "sessionKey", SESSION, "databaseName", MAIN
        )));
        List<Map<String, Object>> columns = ListTableColumnsService.getInstance().invoke(ListTableColumnsDto.fromPrimitives(Primitives.of(
            "connectionUuid", connectionUuid, "sessionKey", SESSION, "databaseName", MAIN, "tableName", "items"
        ))).getColumns();

        List<Map<String, Object>> history = getHistory();
        assertEquals(historyBefore + 2, history.size());
        Map<String, Object> columnsRow = history.getFirst();
        assertEquals(MAIN, columnsRow.get(QueryHistoryKeyEnum.DATABASE_NAME));
        assertTrue(String.valueOf(columnsRow.get(QueryHistoryKeyEnum.SQL_QUERY)).contains("items"));
        assertEquals(columns.size(), getInt(columnsRow.get(QueryHistoryKeyEnum.ROWS_COUNT)));
        assertTrue(!getBool(columnsRow.get(QueryHistoryKeyEnum.HAS_ERROR)));
    }

    @Test
    void testANestedTraceIsRecordedOnceByTheOuterOne() throws Exception {
        int historyBefore = getHistory().size();

        QueryHistoryTracer.getInstance(connectionUuid, MAIN).getTraced(() -> ListTableColumnsService.getInstance().invoke(ListTableColumnsDto.fromPrimitives(Primitives.of(
            "connectionUuid", connectionUuid, "sessionKey", SESSION, "databaseName", MAIN, "tableName", "items"
        ))));

        assertEquals(historyBefore + 1, getHistory().size());
    }

    private List<Map<String, Object>> getHistory() {
        return ListQueryHistoryService.getInstance().invoke(ListQueryHistoryDto.fromPrimitives(Primitives.of(
            "connectionUuid", connectionUuid, "searchText", ""
        ))).getHistory();
    }
}
