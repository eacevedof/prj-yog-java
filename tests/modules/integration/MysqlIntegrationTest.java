package modules.integration;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import modules.shared.domain.enums.ExceptionEnum;
import modules.shared.application.Primitives;

import testcase.AbstractTestCase;

import modules.connections.domain.enums.ConnectionsKeyEnum;
import modules.connections.application.createconnection.CreateConnectionDto;
import modules.connections.application.testconnection.TestConnectionDto;
import modules.connections.application.createconnection.CreateConnectionService;
import modules.connections.application.testconnection.TestConnectionService;
import modules.connections.infrastructure.factories.TargetSessionFactory;

import modules.history.application.listqueryhistory.ListQueryHistoryDto;
import modules.history.application.listqueryhistory.ListQueryHistoryService;

import modules.explorer.domain.enums.ExplorerKeyEnum;
import modules.explorer.application.duplicatetable.DuplicateTableDto;
import modules.explorer.application.gettableinfo.GetTableInfoDto;
import modules.explorer.application.gettableinfo.GetTableInfoResultDto;
import modules.explorer.application.listdatabaseobjects.ListDatabaseObjectsDto;
import modules.explorer.application.listdatabaseobjects.ListDatabaseObjectsResultDto;
import modules.explorer.application.listdatabases.ListDatabasesDto;
import modules.explorer.application.listtablecolumns.ListTableColumnsDto;
import modules.explorer.application.duplicatetable.DuplicateTableService;
import modules.explorer.application.gettableinfo.GetTableInfoService;
import modules.explorer.application.listdatabaseobjects.ListDatabaseObjectsService;
import modules.explorer.application.listdatabases.ListDatabasesService;
import modules.explorer.application.listtablecolumns.ListTableColumnsService;

import modules.query.domain.enums.QueryKeyEnum;
import modules.query.domain.enums.TableChangeKeyEnum;
import modules.query.domain.entities.SqlCompletionContextEntity;
import modules.query.domain.services.SqlCompletionAnalyzer;
import modules.query.application.applytablechanges.ApplyTableChangesDto;
import modules.query.application.cancelquery.CancelQueryDto;
import modules.query.application.executequery.ExecuteQueryDto;
import modules.query.application.executequery.ExecuteQueryResultDto;
import modules.query.application.applytablechanges.ApplyTableChangesService;
import modules.query.application.cancelquery.CancelQueryService;
import modules.query.application.executequery.ExecuteQueryService;
import modules.query.infrastructure.components.CompletionProvider;
import modules.query.infrastructure.components.SchemaCacheComponent;
import modules.query.domain.exceptions.QueryException;

/**
 * Contra un MySQL de verdad: solo si existe JAVAYOG_TEST_MYSQL=host:puerto:usuario:password (make test-mysql levanta
 * uno desechable en Docker). Sin ella los tests se saltan. Crea y borra la BD javayog_it.
 */
final class MysqlIntegrationTest extends AbstractTestCase {

    private static final String ENV_TEST_MYSQL = "JAVAYOG_TEST_MYSQL";
    private static final String DATABASE = "javayog_it";
    private static final String EXPLORER_SESSION = "ctab-it:explorer";
    private static final String QUERY_SESSION = "ctab-it:query-1";

    private static String connectionUuid;
    private static String productionConnectionUuid;

    @BeforeAll
    static void seedDatabase() throws SQLException {
        String testMysql = System.getenv(ENV_TEST_MYSQL);
        Assumptions.assumeTrue(testMysql != null && !testMysql.isBlank(), ENV_TEST_MYSQL + " not set: MySQL integration tests skipped");
        String[] hostPortUserPassword = testMysql.split(":", 4);

        bootFreshDatabase("MysqlIntegrationTest");
        connectionUuid = createConnection("it-local", hostPortUserPassword, false);
        productionConnectionUuid = createConnection("it-prod", hostPortUserPassword, true);

        // el propio editor siembra la BD: troceo con DELIMITER, varias sentencias y DDL
        ExecuteQueryResultDto seedResult = execute(connectionUuid, QUERY_SESSION, "", """
            DROP DATABASE IF EXISTS javayog_it;
            CREATE DATABASE javayog_it;
            USE javayog_it;
            CREATE TABLE customers (
                id INT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
                email VARCHAR(100) NOT NULL UNIQUE,
                name VARCHAR(100) NULL,
                prefs JSON NULL,
                avatar BLOB NULL,
                uid BINARY(4) NULL,
                active BIT(1) NOT NULL DEFAULT b'1',
                KEY ix_name (name, email)
            );
            INSERT INTO customers (email, name, prefs, avatar, uid, active) VALUES
                ('ana@x.es', 'Ana', '{"lang":"es","tags":[1,2]}', REPEAT('a', 100), 0xCAFEBABE, b'1'),
                ('bob@y.es', NULL, NULL, NULL, NULL, b'0'),
                ('carla@z.es', 'Carla; con punto y coma', NULL, NULL, NULL, b'1');
            CREATE VIEW v_active AS SELECT id, email FROM customers WHERE active = 1;
            DELIMITER $$
            CREATE PROCEDURE two_results()
            BEGIN
                SELECT COUNT(*) AS total FROM customers;
                SELECT email FROM customers ORDER BY id;
            END$$
            CREATE FUNCTION plus_one(n INT) RETURNS INT DETERMINISTIC
            BEGIN
                RETURN n + 1;
            END$$
            CREATE TRIGGER tr_email BEFORE INSERT ON customers FOR EACH ROW
            BEGIN
                SET NEW.email = LOWER(NEW.email);
            END$$
            DELIMITER ;
            """, 0, false);
        for (Map<String, Object> result : seedResult.getResults()) {
            org.junit.jupiter.api.Assertions.assertNull(result.get(QueryKeyEnum.ERROR_MESSAGE), String.valueOf(result.get(QueryKeyEnum.ERROR_MESSAGE)));
        }
    }

    @AfterAll
    static void dropDatabase() throws SQLException {
        if (connectionUuid == null) return;
        execute(connectionUuid, QUERY_SESSION, "", "DROP DATABASE IF EXISTS javayog_it", 0, false);
        TargetSessionFactory.getInstance().closeAll();
    }

    @Test
    void testConnectionTestReportsServerVersionUsingTheSavedPassword() throws SQLException {
        String[] hostPortUserPassword = System.getenv(ENV_TEST_MYSQL).split(":", 4);

        String serverVersion = TestConnectionService.getInstance().invoke(TestConnectionDto.fromPrimitives(Primitives.of(
            "connectionUuid", connectionUuid,
            "isPasswordChanged", false,
            "connectionName", "it-local",
            "host", hostPortUserPassword[0],
            "port", hostPortUserPassword[1],
            "userName", hostPortUserPassword[2]
        ))).getServerVersion();

        assertTrue(serverVersion.startsWith("8.") || serverVersion.contains("MariaDB"));
    }

    @Test
    void testExplorerListsDatabasesObjectsAndColumns() throws SQLException {
        List<String> databaseNames = ListDatabasesService.getInstance().invoke(ListDatabasesDto.fromPrimitives(Primitives.of(
            "connectionUuid", connectionUuid, "sessionKey", EXPLORER_SESSION
        ))).getDatabaseNames();
        ListDatabaseObjectsResultDto objects = ListDatabaseObjectsService.getInstance().invoke(ListDatabaseObjectsDto.fromPrimitives(Primitives.of(
            "connectionUuid", connectionUuid, "sessionKey", EXPLORER_SESSION, "databaseName", DATABASE
        )));
        List<Map<String, Object>> columns = ListTableColumnsService.getInstance().invoke(ListTableColumnsDto.fromPrimitives(Primitives.of(
            "connectionUuid", connectionUuid, "sessionKey", EXPLORER_SESSION, "databaseName", DATABASE, "tableName", "customers"
        ))).getColumns();

        assertTrue(databaseNames.contains(DATABASE));
        // otros tests crean tablas en la misma BD: aqui basta con que este la sembrada
        assertTrue(objects.getTables().contains("customers"));
        assertEquals(List.of("v_active"), objects.getViews());
        assertEquals(List.of("two_results"), objects.getProcedures());
        assertEquals(List.of("plus_one"), objects.getFunctions());
        assertEquals(List.of("tr_email"), objects.getTriggers());
        assertEquals(7, columns.size());
        assertEquals("PRI", columns.getFirst().get(ExplorerKeyEnum.COLUMN_KEY));
        assertEquals(true, columns.get(2).get(ExplorerKeyEnum.IS_NULLABLE));
    }

    @Test
    void testTableInfoHasDdlIndexesAndStatus() throws SQLException {
        GetTableInfoResultDto tableInfo = GetTableInfoService.getInstance().invoke(GetTableInfoDto.fromPrimitives(Primitives.of(
            "connectionUuid", connectionUuid, "sessionKey", EXPLORER_SESSION, "databaseName", DATABASE, "tableName", "customers"
        )));
        GetTableInfoResultDto viewInfo = GetTableInfoService.getInstance().invoke(GetTableInfoDto.fromPrimitives(Primitives.of(
            "connectionUuid", connectionUuid, "sessionKey", EXPLORER_SESSION, "databaseName", DATABASE, "tableName", "v_active"
        )));

        assertTrue(tableInfo.getCreateStatement().startsWith("CREATE TABLE `customers`"));
        assertEquals("InnoDB", tableInfo.getStatus().get("engine"));
        // PRIMARY (1 columna) + email UNIQUE (1) + ix_name (2 columnas)
        assertEquals(4, tableInfo.getIndexes().size());
        assertTrue(viewInfo.isView());
        assertTrue(viewInfo.getCreateStatement().contains("VIEW `javayog_it`.`v_active` AS select"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testSelectReadsNullBinaryBitAndJsonAsText() throws SQLException {
        Map<String, Object> result = execute(connectionUuid, QUERY_SESSION, DATABASE,
            "SELECT name, prefs, avatar, uid, active FROM customers ORDER BY id", 0, false).getResults().getFirst();
        List<List<String>> rows = (List<List<String>>) result.get(QueryKeyEnum.ROWS);

        assertEquals("Ana", rows.getFirst().get(0));
        assertTrue(rows.getFirst().get(1).contains("\"lang\""));
        assertEquals("(BLOB) 100 bytes", rows.getFirst().get(2));
        assertEquals("0xCAFEBABE", rows.getFirst().get(3));
        assertEquals("1", rows.getFirst().get(4));
        assertEquals(null, rows.get(1).get(0));
        assertEquals("0", rows.get(1).get(4));
        assertEquals("Carla; con punto y coma", rows.get(2).get(0));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testCallReturnsEveryResultSetAndFunctionsWork() throws SQLException {
        List<Map<String, Object>> results = execute(connectionUuid, QUERY_SESSION, DATABASE, "CALL two_results(); SELECT plus_one(41) AS answer", 0, false).getResults();

        List<Map<String, Object>> resultSets = results.stream().filter(result -> Boolean.TRUE.equals(result.get(QueryKeyEnum.HAS_RESULT_SET))).toList();
        assertEquals(3, resultSets.size());
        assertEquals("3", ((List<List<String>>) resultSets.getFirst().get(QueryKeyEnum.ROWS)).getFirst().getFirst());
        assertEquals(3, ((List<List<String>>) resultSets.get(1).get(QueryKeyEnum.ROWS)).size());
        assertEquals("42", ((List<List<String>>) resultSets.get(2).get(QueryKeyEnum.ROWS)).getFirst().getFirst());
    }

    @Test
    void testMaxRowsTruncatesAndUpdateCountsAffectedRows() throws SQLException {
        ExecuteQueryResultDto truncated = execute(connectionUuid, QUERY_SESSION, DATABASE, "SELECT * FROM customers", 2, false);
        ExecuteQueryResultDto updated = execute(connectionUuid, QUERY_SESSION, DATABASE, "UPDATE customers SET name = name WHERE id > 0", 0, false);

        assertEquals(true, truncated.getResults().getFirst().get(QueryKeyEnum.IS_TRUNCATED));
        assertEquals(3, updated.getResults().getFirst().get(QueryKeyEnum.AFFECTED_ROWS));
    }

    @Test
    void testErrorsStopTheRunAndUseChangesTheActiveDatabase() throws SQLException {
        ExecuteQueryResultDto failed = execute(connectionUuid, "ctab-it:query-2", DATABASE, "SELECT 1; SELECT * FROM nope; SELECT 2", 0, false);
        ExecuteQueryResultDto used = execute(connectionUuid, "ctab-it:query-2", DATABASE, "USE mysql", 0, false);

        assertEquals(2, failed.getResults().size());
        assertTrue(String.valueOf(failed.getResults().getLast().get(QueryKeyEnum.ERROR_MESSAGE)).startsWith("Error Code: 1146"));
        assertEquals("mysql", used.getCurrentDatabase());
    }

    @Test
    void testCancelStopsALongQuery() throws Exception {
        String sessionKey = "ctab-it:query-cancel";
        CompletableFuture<ExecuteQueryResultDto> running = CompletableFuture.supplyAsync(
            () -> execute(connectionUuid, sessionKey, DATABASE, "SELECT SLEEP(30); SELECT 'not reached'", 0, false)
        );
        Thread.sleep(1500);
        CancelQueryService.getInstance().invoke(CancelQueryDto.fromPrimitives(Primitives.of("sessionKey", sessionKey)));

        ExecuteQueryResultDto cancelled = running.get(15, TimeUnit.SECONDS);

        assertTrue(cancelled.isCancelled());
        assertTrue(cancelled.getElapsedMs() < 15000);
        assertEquals(1, cancelled.getResults().size());
    }

    @Test
    void testProductionAsksBeforeDeleteWithoutWhere() throws SQLException {
        QueryException queryException = assertThrows(QueryException.class,
            () -> execute(productionConnectionUuid, "ctab-it:prod", DATABASE, "DELETE FROM customers", 0, false)
        );

        assertEquals(ExceptionEnum.CODE_PRECONDITION_REQUIRED, queryException.getCode());
    }

    @Test
    @SuppressWarnings("unchecked")
    void testGridChangesAreSavedInOneTransaction() throws SQLException {
        execute(connectionUuid, QUERY_SESSION, DATABASE, "CREATE TABLE notes (id INT AUTO_INCREMENT PRIMARY KEY, txt VARCHAR(50) NULL); INSERT INTO notes (txt) VALUES ('uno'), ('dos'), ('tres')", 0, false);

        int affectedRows = applyChanges(connectionUuid, "notes", List.of(
            change(TableChangeKeyEnum.TYPE_DELETE, Map.of("id", "3"), Map.of()),
            change(TableChangeKeyEnum.TYPE_UPDATE, Map.of("id", "1"), mapWithNull("txt")),
            change(TableChangeKeyEnum.TYPE_INSERT, Map.of(), Map.of("txt", "it's new"))
        ), false);
        List<List<String>> rows = (List<List<String>>) execute(connectionUuid, QUERY_SESSION, DATABASE, "SELECT id, txt FROM notes ORDER BY id", 0, false)
            .getResults().getFirst().get(QueryKeyEnum.ROWS);

        assertEquals(3, affectedRows);
        assertEquals(3, rows.size());
        assertEquals(null, rows.getFirst().get(1));
        assertEquals("dos", rows.get(1).get(1));
        assertEquals("it's new", rows.get(2).get(1));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testGridSetDefaultUsesTheColumnDefault() throws SQLException {
        execute(connectionUuid, QUERY_SESSION, DATABASE, "CREATE TABLE flags (id INT PRIMARY KEY, state VARCHAR(10) NOT NULL DEFAULT 'draft', txt VARCHAR(10)); INSERT INTO flags VALUES (1, 'sent', 'a')", 0, false);
        Map<String, Object> update = change(TableChangeKeyEnum.TYPE_UPDATE, Map.of("id", "1"), Map.of());
        update.put(TableChangeKeyEnum.DEFAULT_COLUMNS, List.of("state"));
        Map<String, Object> insert = change(TableChangeKeyEnum.TYPE_INSERT, Map.of(), Map.of("id", "2", "txt", "b"));
        insert.put(TableChangeKeyEnum.DEFAULT_COLUMNS, List.of("state"));

        applyChanges(connectionUuid, "flags", List.of(update, insert), false);
        List<List<String>> rows = (List<List<String>>) execute(connectionUuid, QUERY_SESSION, DATABASE, "SELECT state FROM flags ORDER BY id", 0, false)
            .getResults().getFirst().get(QueryKeyEnum.ROWS);

        assertEquals("draft", rows.getFirst().getFirst());
        assertEquals("draft", rows.get(1).getFirst());
    }

    @Test
    @SuppressWarnings("unchecked")
    void testGridChangesRollBackWhenARowIsGone() {
        execute(connectionUuid, QUERY_SESSION, DATABASE, "CREATE TABLE tags (id INT PRIMARY KEY, name VARCHAR(20)); INSERT INTO tags VALUES (1, 'a')", 0, false);

        SQLException rowGone = assertThrows(SQLException.class, () -> applyChanges(connectionUuid, "tags", List.of(
            change(TableChangeKeyEnum.TYPE_UPDATE, Map.of("id", "1"), Map.of("name", "b")),
            change(TableChangeKeyEnum.TYPE_DELETE, Map.of("id", "99"), Map.of())
        ), false));
        List<List<String>> rows = (List<List<String>>) execute(connectionUuid, QUERY_SESSION, DATABASE, "SELECT name FROM tags", 0, false)
            .getResults().getFirst().get(QueryKeyEnum.ROWS);

        assertTrue(rowGone.getMessage().contains("Expected 1 row"));
        assertEquals("a", rows.getFirst().getFirst());
    }

    @Test
    void testGridChangesNeedAPrimaryKeyAndConfirmationInProduction() {
        execute(connectionUuid, QUERY_SESSION, DATABASE, "CREATE TABLE no_pk (a INT)", 0, false);

        QueryException withoutKey = assertThrows(QueryException.class, () -> applyChanges(connectionUuid, "no_pk", List.of(
            change(TableChangeKeyEnum.TYPE_INSERT, Map.of(), Map.of("a", "1"))
        ), false));
        QueryException production = assertThrows(QueryException.class, () -> applyChanges(productionConnectionUuid, "customers", List.of(
            change(TableChangeKeyEnum.TYPE_UPDATE, Map.of("id", "1"), Map.of("name", "x"))
        ), false));

        assertTrue(withoutKey.getMessage().contains("no primary key"));
        assertEquals(ExceptionEnum.CODE_PRECONDITION_REQUIRED, production.getCode());
    }

    @Test
    @SuppressWarnings("unchecked")
    void testDuplicateTableCopiesStructureAndData() throws SQLException {
        String copyTableName = DuplicateTableService.getInstance().invoke(DuplicateTableDto.fromPrimitives(Primitives.of(
            "connectionUuid", connectionUuid, "sessionKey", EXPLORER_SESSION, "databaseName", DATABASE, "tableName", "customers"
        ))).getCopyTableName();
        List<List<String>> counts = (List<List<String>>) execute(connectionUuid, QUERY_SESSION, DATABASE,
            "SELECT (SELECT COUNT(*) FROM `%s`), (SELECT COUNT(*) FROM customers)".formatted(copyTableName), 0, false).getResults().getFirst().get(QueryKeyEnum.ROWS);
        execute(connectionUuid, QUERY_SESSION, DATABASE, "DROP TABLE `%s`".formatted(copyTableName), 0, false);

        assertTrue(copyTableName.matches("customers_copy_[0-9]{14}"));
        assertEquals(counts.getFirst().get(0), counts.getFirst().get(1));
        assertNotEquals("0", counts.getFirst().get(0));
    }

    private static Map<String, Object> change(String changeType, Map<String, String> keyValues, Map<String, String> newValues) {
        return Primitives.of(
            TableChangeKeyEnum.CHANGE_TYPE, changeType,
            TableChangeKeyEnum.KEY_VALUES, keyValues,
            TableChangeKeyEnum.NEW_VALUES, newValues
        );
    }

    private static Map<String, String> mapWithNull(String key) {
        Map<String, String> map = new java.util.LinkedHashMap<>();
        map.put(key, null);
        return map;
    }

    private static int applyChanges(String connectionUuid, String tableName, List<Map<String, Object>> changes, boolean isConfirmed) throws SQLException {
        return ApplyTableChangesService.getInstance().invoke(ApplyTableChangesDto.fromPrimitives(Primitives.of(
            "connectionUuid", connectionUuid,
            "sessionKey", "ctab-it:data-1",
            "databaseName", DATABASE,
            "tableName", tableName,
            "changes", changes,
            "isConfirmed", isConfirmed
        ))).getAffectedRows();
    }

    @Test
    void testCompletionResolvesAliasesAndRanksFuzzy() throws Exception {
        try {
            javafx.application.Platform.startup(() -> { });
        }
        catch (IllegalStateException e) {
            // ya arrancado
        }
        SchemaCacheComponent schemaCache = new SchemaCacheComponent(connectionUuid, "ctab-it:completion");
        String sql = "SELECT c.mai FROM customers c WHERE c.";

        SqlCompletionContextEntity aliasContext = SqlCompletionAnalyzer.getInstance().getContext(sql, sql.indexOf("mai") + 3);
        List<CompletionProvider.Candidate> first = CompletionProvider.getInstance().getCandidates(aliasContext, DATABASE, schemaCache);
        for (int i = 0; i < 50 && schemaCache.getColumnNames(DATABASE, "customers") == null; i++) {
            Thread.sleep(100);
        }
        List<CompletionProvider.Candidate> columns = CompletionProvider.getInstance().getCandidates(aliasContext, DATABASE, schemaCache);
        SqlCompletionContextEntity tableContext = SqlCompletionAnalyzer.getInstance().getContext("SELECT * FROM cstm", 18);
        CompletionProvider.getInstance().getCandidates(tableContext, DATABASE, schemaCache);
        for (int i = 0; i < 50 && schemaCache.getTableNames(DATABASE) == null; i++) {
            Thread.sleep(100);
        }
        List<CompletionProvider.Candidate> tables = CompletionProvider.getInstance().getCandidates(tableContext, DATABASE, schemaCache);

        assertTrue(first.isEmpty());
        assertEquals("email", columns.getFirst().text());
        assertEquals(CompletionProvider.KIND_COLUMN, columns.getFirst().kind());
        assertEquals("customers", tables.getFirst().text());
    }

    /**
     * Feature 12: solo con JAVAYOG_TEST_SSH=sshHost:sshPuerto:usuario:password:hostBdDesdeSsh:puertoBd (make test-mysql
     * levanta un sshd desechable en la misma red Docker que el MySQL).
     */
    @Test
    void testSshTunnelReachesMysqlAndWrongPasswordFails() throws SQLException {
        String testSsh = System.getenv("JAVAYOG_TEST_SSH");
        Assumptions.assumeTrue(testSsh != null && !testSsh.isBlank(), "JAVAYOG_TEST_SSH not set: SSH tunnel test skipped");
        String[] ssh = testSsh.split(":", 6);
        String[] mysql = System.getenv(ENV_TEST_MYSQL).split(":", 4);

        Map<String, Object> tunnelForm = Primitives.of(
            "connectionName", "it-ssh",
            "host", ssh[4], "port", ssh[5], "userName", mysql[2], "userPassword", mysql[3],
            "sshEnabled", true, "sshHost", ssh[0], "sshPort", ssh[1], "sshUser", ssh[2], "sshPassword", ssh[3]
        );
        String serverVersion = TestConnectionService.getInstance().invoke(TestConnectionDto.fromPrimitives(tunnelForm)).getServerVersion();
        String sshConnectionUuid = String.valueOf(CreateConnectionService.getInstance().invoke(CreateConnectionDto.fromPrimitives(tunnelForm))
            .getConnection().get(ConnectionsKeyEnum.CONNECTION_UUID));
        ExecuteQueryResultDto overTunnel = execute(sshConnectionUuid, "ctab-ssh:query-1", "", "SELECT 1 + 1", 0, false);
        TargetSessionFactory.getInstance().closeSessions("ctab-ssh");
        SQLException wrongPassword = assertThrows(SQLException.class, () -> TestConnectionService.getInstance().invoke(
            TestConnectionDto.fromPrimitives(Primitives.merge(tunnelForm, Primitives.of("sshPassword", "wrong")))
        ));

        assertTrue(serverVersion.startsWith("8."));
        assertEquals("2", ((List<?>) ((List<?>) overTunnel.getResults().getFirst().get(QueryKeyEnum.ROWS)).getFirst()).getFirst());
        assertTrue(wrongPassword.getMessage().contains("SSH tunnel failed"));
    }

    @Test
    void testExecutionsGoToTheHistory() {
        execute(connectionUuid, QUERY_SESSION, DATABASE, "SELECT 'history marker'", 0, false);

        List<Map<String, Object>> history = ListQueryHistoryService.getInstance().invoke(ListQueryHistoryDto.fromPrimitives(Primitives.of(
            "connectionUuid", connectionUuid, "searchText", "history marker"
        ))).getHistory();

        assertEquals(1, history.size());
        assertEquals(DATABASE, history.getFirst().get("database_name"));
    }

    private static String createConnection(String connectionName, String[] hostPortUserPassword, boolean isProduction) {
        Map<String, Object> connection = CreateConnectionService.getInstance().invoke(CreateConnectionDto.fromPrimitives(Primitives.of(
            "connectionName", connectionName,
            "host", hostPortUserPassword[0],
            "port", hostPortUserPassword[1],
            "userName", hostPortUserPassword[2],
            "userPassword", hostPortUserPassword[3],
            "isProduction", isProduction
        ))).getConnection();
        return String.valueOf(connection.get(ConnectionsKeyEnum.CONNECTION_UUID));
    }

    private static ExecuteQueryResultDto execute(String connectionUuid, String sessionKey, String databaseName, String sql, int maxRows, boolean isConfirmed) {
        return ExecuteQueryService.getInstance().invoke(ExecuteQueryDto.fromPrimitives(Primitives.of(
            "connectionUuid", connectionUuid,
            "sessionKey", sessionKey,
            "databaseName", databaseName,
            "sqlQuery", sql,
            "maxRows", maxRows,
            "isConfirmed", isConfirmed
        )));
    }
}
