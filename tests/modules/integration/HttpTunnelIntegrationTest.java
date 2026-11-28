package modules.integration;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import modules.shared.application.Primitives;

import testcase.AbstractTestCase;

import modules.connections.domain.enums.ConnectionsKeyEnum;
import modules.connections.application.createconnection.CreateConnectionDto;
import modules.connections.application.testconnection.TestConnectionDto;
import modules.connections.application.createconnection.CreateConnectionService;
import modules.connections.application.testconnection.TestConnectionService;
import modules.connections.infrastructure.factories.TargetSessionFactory;

import modules.explorer.domain.enums.ExplorerKeyEnum;
import modules.explorer.application.gettableinfo.GetTableInfoDto;
import modules.explorer.application.gettableinfo.GetTableInfoResultDto;
import modules.explorer.application.listdatabaseobjects.ListDatabaseObjectsDto;
import modules.explorer.application.listdatabaseobjects.ListDatabaseObjectsResultDto;
import modules.explorer.application.listdatabases.ListDatabasesDto;
import modules.explorer.application.listtablecolumns.ListTableColumnsDto;
import modules.explorer.application.gettableinfo.GetTableInfoService;
import modules.explorer.application.listdatabaseobjects.ListDatabaseObjectsService;
import modules.explorer.application.listdatabases.ListDatabasesService;
import modules.explorer.application.listtablecolumns.ListTableColumnsService;

import modules.query.domain.enums.QueryKeyEnum;
import modules.query.domain.enums.TableChangeKeyEnum;
import modules.query.application.applytablechanges.ApplyTableChangesDto;
import modules.query.application.executequery.ExecuteQueryDto;
import modules.query.application.executequery.ExecuteQueryResultDto;
import modules.query.application.applytablechanges.ApplyTableChangesService;
import modules.query.application.executequery.ExecuteQueryService;

/**
 * Feature 13: todo a traves del tunel HTTP de SQLyog (el PHP de prj-yog). Solo si existen
 * JAVAYOG_TEST_HTTP=url|hostBdDesdeElTunel|puertoBd y JAVAYOG_TEST_MYSQL (usuario y password, y la conexion directa
 * con la que se siembra la BD). make test-mysql levanta el tunel en Docker junto al MySQL.
 */
final class HttpTunnelIntegrationTest extends AbstractTestCase {

    private static final String DATABASE = "javayog_http_it";
    private static final String DIRECT_SESSION = "ctab-http-direct:query-1";
    private static final String EXPLORER_SESSION = "ctab-http:explorer";
    private static final String QUERY_SESSION = "ctab-http:query-1";

    private static String[] http;
    private static String[] mysql;
    private static String directConnectionUuid;
    private static String tunnelConnectionUuid;

    @BeforeAll
    static void seedDatabase() throws SQLException {
        String testHttp = System.getenv("JAVAYOG_TEST_HTTP");
        String testMysql = System.getenv("JAVAYOG_TEST_MYSQL");
        Assumptions.assumeTrue(testHttp != null && !testHttp.isBlank() && testMysql != null && !testMysql.isBlank(),
            "JAVAYOG_TEST_HTTP or JAVAYOG_TEST_MYSQL not set: HTTP tunnel tests skipped");
        http = testHttp.split("\\|", 3);
        mysql = testMysql.split(":", 4);

        bootFreshDatabase("HttpTunnelIntegrationTest");
        directConnectionUuid = createConnection(Primitives.of(
            "connectionName", "http-it-direct", "host", mysql[0], "port", mysql[1], "userName", mysql[2], "userPassword", mysql[3]
        ));
        tunnelConnectionUuid = createConnection(getTunnelForm("http-it-tunnel", http[0]));

        ExecuteQueryResultDto seedResult = execute(directConnectionUuid, DIRECT_SESSION, "", """
            DROP DATABASE IF EXISTS javayog_http_it;
            CREATE DATABASE javayog_http_it;
            USE javayog_http_it;
            CREATE TABLE customers (
                id INT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
                email VARCHAR(100) NOT NULL UNIQUE,
                name VARCHAR(100) NULL,
                notes TEXT NULL,
                prefs JSON NULL,
                uid BINARY(4) NULL,
                active BIT(1) NOT NULL DEFAULT b'1'
            );
            INSERT INTO customers (email, name, notes, prefs, uid, active) VALUES
                ('ana@x.es', 'Ána', 'nota larga', '{"lang":"es"}', 0xCAFEBABE, b'1'),
                ('bob@y.es', NULL, NULL, NULL, NULL, b'0'),
                ('carla@z.es', 'Carla; con \\'comillas\\'', NULL, NULL, NULL, b'1');
            CREATE VIEW v_active AS SELECT id, email FROM customers WHERE active = 1;
            DELIMITER $$
            CREATE PROCEDURE two_results()
            BEGIN
                SELECT COUNT(*) AS total FROM customers;
                SELECT email FROM customers ORDER BY id;
            END$$
            DELIMITER ;
            CREATE TABLE notes (id INT AUTO_INCREMENT PRIMARY KEY, txt VARCHAR(50) NULL);
            INSERT INTO notes (txt) VALUES ('uno'), ('dos'), ('tres');
            """, 0);
        for (Map<String, Object> result : seedResult.getResults()) {
            org.junit.jupiter.api.Assertions.assertNull(result.get(QueryKeyEnum.ERROR_MESSAGE), String.valueOf(result.get(QueryKeyEnum.ERROR_MESSAGE)));
        }
    }

    @AfterAll
    static void dropDatabase() throws SQLException {
        if (directConnectionUuid == null) return;
        execute(directConnectionUuid, DIRECT_SESSION, "", "DROP DATABASE IF EXISTS javayog_http_it", 0);
        TargetSessionFactory.getInstance().closeAll();
    }

    @Test
    void testConnectionTestPingsTheTunnelAndAWrongTokenFails() throws SQLException {
        String serverVersion = TestConnectionService.getInstance().invoke(TestConnectionDto.fromPrimitives(
            getTunnelForm("http-it-test", http[0])
        )).getServerVersion();
        SQLException wrongToken = assertThrows(SQLException.class, () -> TestConnectionService.getInstance().invoke(
            TestConnectionDto.fromPrimitives(getTunnelForm("http-it-bad", http[0] + "-wrong"))
        ));

        assertTrue(serverVersion.startsWith("8."));
        assertTrue(serverVersion.contains("HTTP tunnel 13.21"));
        assertTrue(wrongToken.getMessage().contains("HTTP 404"));
    }

    @Test
    void testExplorerWorksThroughTheTunnel() throws SQLException {
        List<String> databaseNames = ListDatabasesService.getInstance().invoke(ListDatabasesDto.fromPrimitives(Primitives.of(
            "connectionUuid", tunnelConnectionUuid, "sessionKey", EXPLORER_SESSION
        ))).getDatabaseNames();
        ListDatabaseObjectsResultDto objects = ListDatabaseObjectsService.getInstance().invoke(ListDatabaseObjectsDto.fromPrimitives(Primitives.of(
            "connectionUuid", tunnelConnectionUuid, "sessionKey", EXPLORER_SESSION, "databaseName", DATABASE
        )));
        List<Map<String, Object>> columns = ListTableColumnsService.getInstance().invoke(ListTableColumnsDto.fromPrimitives(Primitives.of(
            "connectionUuid", tunnelConnectionUuid, "sessionKey", EXPLORER_SESSION, "databaseName", DATABASE, "tableName", "customers"
        ))).getColumns();
        GetTableInfoResultDto tableInfo = GetTableInfoService.getInstance().invoke(GetTableInfoDto.fromPrimitives(Primitives.of(
            "connectionUuid", tunnelConnectionUuid, "sessionKey", EXPLORER_SESSION, "databaseName", DATABASE, "tableName", "customers"
        )));

        assertTrue(databaseNames.contains(DATABASE));
        assertTrue(objects.getTables().contains("customers"));
        assertEquals(List.of("v_active"), objects.getViews());
        assertEquals(List.of("two_results"), objects.getProcedures());
        assertEquals(7, columns.size());
        assertEquals("PRI", columns.getFirst().get(ExplorerKeyEnum.COLUMN_KEY));
        assertTrue(tableInfo.getCreateStatement().startsWith("CREATE TABLE `customers`"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testSelectDecodesTextNullBinaryBitAndJson() throws SQLException {
        Map<String, Object> result = execute(tunnelConnectionUuid, QUERY_SESSION, DATABASE,
            "SELECT name, notes, prefs, uid, active FROM customers ORDER BY id", 0).getResults().getFirst();
        List<List<String>> rows = (List<List<String>>) result.get(QueryKeyEnum.ROWS);

        assertEquals("Ána", rows.getFirst().get(0));
        assertEquals("nota larga", rows.getFirst().get(1));
        assertTrue(rows.getFirst().get(2).contains("\"lang\""));
        assertEquals("0xCAFEBABE", rows.getFirst().get(3));
        assertEquals("1", rows.getFirst().get(4));
        assertEquals(null, rows.get(1).get(0));
        assertEquals("0", rows.get(1).get(4));
        assertEquals("Carla; con 'comillas'", rows.get(2).get(0));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testCallMaxRowsUseAndErrors() throws SQLException {
        List<Map<String, Object>> called = execute(tunnelConnectionUuid, QUERY_SESSION, DATABASE, "CALL two_results()", 0).getResults();
        ExecuteQueryResultDto truncated = execute(tunnelConnectionUuid, QUERY_SESSION, DATABASE, "SELECT * FROM customers", 2);
        ExecuteQueryResultDto used = execute(tunnelConnectionUuid, "ctab-http:query-2", DATABASE, "USE mysql", 0);
        ExecuteQueryResultDto stillThere = execute(tunnelConnectionUuid, "ctab-http:query-2", "mysql", "SELECT DATABASE()", 0);
        ExecuteQueryResultDto failed = execute(tunnelConnectionUuid, QUERY_SESSION, DATABASE, "SELECT * FROM nope", 0);

        List<Map<String, Object>> resultSets = called.stream().filter(result -> Boolean.TRUE.equals(result.get(QueryKeyEnum.HAS_RESULT_SET))).toList();
        assertEquals(2, resultSets.size());
        assertEquals("3", ((List<List<String>>) resultSets.getFirst().get(QueryKeyEnum.ROWS)).getFirst().getFirst());
        assertEquals(2, ((List<?>) truncated.getResults().getFirst().get(QueryKeyEnum.ROWS)).size());
        assertEquals(true, truncated.getResults().getFirst().get(QueryKeyEnum.IS_TRUNCATED));
        assertEquals("mysql", used.getCurrentDatabase());
        assertEquals("mysql", ((List<List<String>>) stillThere.getResults().getFirst().get(QueryKeyEnum.ROWS)).getFirst().getFirst());
        assertTrue(String.valueOf(failed.getResults().getLast().get(QueryKeyEnum.ERROR_MESSAGE)).startsWith("Error Code: 1146"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testGridChangesAreAtomicThroughTheTunnel() throws SQLException {
        int affectedRows = applyChanges("notes", List.of(
            change(TableChangeKeyEnum.TYPE_DELETE, Map.of("id", "3"), Map.of()),
            change(TableChangeKeyEnum.TYPE_UPDATE, Map.of("id", "1"), Map.of("txt", "uno")),
            change(TableChangeKeyEnum.TYPE_INSERT, Map.of(), Map.of("txt", "it's; new\\"))
        ));
        SQLException rowGone = assertThrows(SQLException.class, () -> applyChanges("notes", List.of(
            change(TableChangeKeyEnum.TYPE_UPDATE, Map.of("id", "2"), Map.of("txt", "changed")),
            change(TableChangeKeyEnum.TYPE_DELETE, Map.of("id", "99"), Map.of())
        )));
        List<List<String>> rows = (List<List<String>>) execute(tunnelConnectionUuid, QUERY_SESSION, DATABASE, "SELECT id, txt FROM notes ORDER BY id", 0)
            .getResults().getFirst().get(QueryKeyEnum.ROWS);

        // el UPDATE pone el mismo valor: por PDO no "cambia" la fila, pero existe y cuenta
        assertEquals(3, affectedRows);
        assertTrue(rowGone.getMessage().contains("Expected 1 row"));
        assertEquals(3, rows.size());
        assertEquals("dos", rows.get(1).get(1));
        assertEquals("it's; new\\", rows.get(2).get(1));
    }

    @Test
    void testCancelCutsTheRequest() throws Exception {
        String sessionKey = "ctab-http:query-cancel";
        java.util.concurrent.CompletableFuture<ExecuteQueryResultDto> running = java.util.concurrent.CompletableFuture.supplyAsync(
            () -> execute(tunnelConnectionUuid, sessionKey, DATABASE, "SELECT SLEEP(30); SELECT 'not reached'", 0)
        );
        Thread.sleep(1500);
        modules.query.application.cancelquery.CancelQueryService.getInstance().invoke(
            modules.query.application.cancelquery.CancelQueryDto.fromPrimitives(Primitives.of("sessionKey", sessionKey))
        );

        ExecuteQueryResultDto cancelled = running.get(15, java.util.concurrent.TimeUnit.SECONDS);

        assertTrue(cancelled.isCancelled());
        assertTrue(cancelled.getElapsedMs() < 15000);
    }

    private static Map<String, Object> getTunnelForm(String connectionName, String httpUrl) {
        return Primitives.of(
            "connectionName", connectionName,
            "host", http[1], "port", http[2], "userName", mysql[2], "userPassword", mysql[3],
            "httpEnabled", true, "httpUrl", httpUrl, "httpTimeoutS", 20
        );
    }

    private static Map<String, Object> change(String changeType, Map<String, String> keyValues, Map<String, String> newValues) {
        return Primitives.of(
            TableChangeKeyEnum.CHANGE_TYPE, changeType,
            TableChangeKeyEnum.KEY_VALUES, keyValues,
            TableChangeKeyEnum.NEW_VALUES, newValues
        );
    }

    private static int applyChanges(String tableName, List<Map<String, Object>> changes) throws SQLException {
        return ApplyTableChangesService.getInstance().invoke(ApplyTableChangesDto.fromPrimitives(Primitives.of(
            "connectionUuid", tunnelConnectionUuid,
            "sessionKey", "ctab-http:data-1",
            "databaseName", DATABASE,
            "tableName", tableName,
            "changes", changes,
            "isConfirmed", false
        ))).getAffectedRows();
    }

    private static String createConnection(Map<String, Object> form) {
        return String.valueOf(CreateConnectionService.getInstance().invoke(CreateConnectionDto.fromPrimitives(form))
            .getConnection().get(ConnectionsKeyEnum.CONNECTION_UUID));
    }

    private static ExecuteQueryResultDto execute(String connectionUuid, String sessionKey, String databaseName, String sql, int maxRows) {
        return ExecuteQueryService.getInstance().invoke(ExecuteQueryDto.fromPrimitives(Primitives.of(
            "connectionUuid", connectionUuid,
            "sessionKey", sessionKey,
            "databaseName", databaseName,
            "sqlQuery", sql,
            "maxRows", maxRows,
            "isConfirmed", false
        )));
    }
}
