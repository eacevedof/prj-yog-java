package modules.integration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import modules.shared.domain.enums.ExceptionEnum;
import modules.shared.application.Primitives;
import modules.shared.infrastructure.exceptions.FieldsException;

import testcase.AbstractTestCase;

import modules.connections.domain.enums.ConnectionsKeyEnum;
import modules.connections.application.createconnection.CreateConnectionDto;
import modules.connections.application.testconnection.TestConnectionDto;
import modules.connections.application.createconnection.CreateConnectionService;
import modules.connections.application.testconnection.TestConnectionService;
import modules.connections.infrastructure.factories.TargetSessionFactory;

import modules.explorer.domain.enums.ExplorerKeyEnum;
import modules.explorer.domain.mothers.TableDefinitionMother;
import modules.explorer.application.createtable.CreateTableDto;
import modules.explorer.application.droptable.DropTableDto;
import modules.explorer.application.duplicatetable.DuplicateTableDto;
import modules.explorer.application.gettableinfo.GetTableInfoDto;
import modules.explorer.application.gettableinfo.GetTableInfoResultDto;
import modules.explorer.application.listdatabaseobjects.ListDatabaseObjectsDto;
import modules.explorer.application.listdatabaseobjects.ListDatabaseObjectsResultDto;
import modules.explorer.application.listdatabases.ListDatabasesDto;
import modules.explorer.application.listtablecolumns.ListTableColumnsDto;
import modules.explorer.application.createtable.CreateTableService;
import modules.explorer.application.droptable.DropTableService;
import modules.explorer.application.duplicatetable.DuplicateTableService;
import modules.explorer.application.gettableinfo.GetTableInfoService;
import modules.explorer.application.listdatabaseobjects.ListDatabaseObjectsService;
import modules.explorer.application.listdatabases.ListDatabasesService;
import modules.explorer.application.listtablecolumns.ListTableColumnsService;
import modules.explorer.domain.exceptions.ExplorerException;

import modules.query.domain.enums.QueryKeyEnum;
import modules.query.domain.enums.TableChangeKeyEnum;
import modules.query.domain.services.TableDataQueryBuilder;
import modules.query.application.applytablechanges.ApplyTableChangesDto;
import modules.query.application.executequery.ExecuteQueryDto;
import modules.query.application.executequery.ExecuteQueryResultDto;
import modules.query.application.applytablechanges.ApplyTableChangesService;
import modules.query.application.executequery.ExecuteQueryService;

/**
 * SQLite: sin servidor, asi que corre siempre, sobre un fichero en target/. Con JAVAYOG_TEST_HTTP y
 * JAVAYOG_TEST_SQLITE_TUNNEL=clave|carpetaDelHost|password (la carpeta que el tunel PHP ve con esa clave en su lista
 * de BDs), tambien por el tunel HTTP (.../sqlite).
 */
final class SqliteIntegrationTest extends AbstractTestCase {

    private static final String MAIN = "main";
    private static final String EXPLORER_SESSION = "ctab-lite:explorer";
    private static final String QUERY_SESSION = "ctab-lite:query-1";

    private static Path sqliteFile;
    private static String connectionUuid;

    @BeforeAll
    static void seedFile() throws Exception {
        bootFreshDatabase("SqliteIntegrationTest");
        sqliteFile = Path.of("target", "javayog-it-target.sqlite").toAbsolutePath();
        Files.deleteIfExists(sqliteFile);
        Files.createFile(sqliteFile);
        connectionUuid = createConnection(Primitives.of("connectionName", "lite-it", "dbEngine", "sqlite", "databaseName", sqliteFile.toString()));
        seed(connectionUuid, QUERY_SESSION);
    }

    @AfterAll
    static void closeSessions() throws SQLException {
        TargetSessionFactory.getInstance().closeAll();
    }

    @Test
    void testConnectionTestNeedsAnExistingFileAndNoServerFields() throws SQLException {
        String version = TestConnectionService.getInstance().invoke(TestConnectionDto.fromPrimitives(Primitives.of(
            "connectionName", "lite-test", "dbEngine", "sqlite", "databaseName", sqliteFile.toString()
        ))).getServerVersion();
        SQLException missingFile = assertThrows(SQLException.class, () -> TestConnectionService.getInstance().invoke(
            TestConnectionDto.fromPrimitives(Primitives.of("connectionName", "lite-missing", "dbEngine", "sqlite", "databaseName", sqliteFile + ".nope"))
        ));
        FieldsException withSsh = assertThrows(FieldsException.class, () -> CreateConnectionService.getInstance().invoke(CreateConnectionDto.fromPrimitives(Primitives.of(
            "connectionName", "lite-ssh", "dbEngine", "sqlite", "databaseName", "",
            "sshEnabled", true, "sshHost", "h", "sshUser", "u", "sshPassword", "p"
        ))));

        assertTrue(version.startsWith("SQLite 3."));
        assertTrue(missingFile.getMessage().contains("SQLite file not found"));
        assertFalse(Files.exists(Path.of(sqliteFile + ".nope")));
        assertEquals(2, withSsh.getErrors().size());
    }

    @Test
    void testExplorerReadsSqliteMasterAndPragmas() throws SQLException {
        List<String> databases = ListDatabasesService.getInstance().invoke(ListDatabasesDto.fromPrimitives(Primitives.of(
            "connectionUuid", connectionUuid, "sessionKey", EXPLORER_SESSION
        ))).getDatabaseNames();
        ListDatabaseObjectsResultDto objects = ListDatabaseObjectsService.getInstance().invoke(ListDatabaseObjectsDto.fromPrimitives(Primitives.of(
            "connectionUuid", connectionUuid, "sessionKey", EXPLORER_SESSION, "databaseName", MAIN
        )));
        List<Map<String, Object>> columns = ListTableColumnsService.getInstance().invoke(ListTableColumnsDto.fromPrimitives(Primitives.of(
            "connectionUuid", connectionUuid, "sessionKey", EXPLORER_SESSION, "databaseName", MAIN, "tableName", "customers"
        ))).getColumns();
        GetTableInfoResultDto tableInfo = GetTableInfoService.getInstance().invoke(GetTableInfoDto.fromPrimitives(Primitives.of(
            "connectionUuid", connectionUuid, "sessionKey", EXPLORER_SESSION, "databaseName", MAIN, "tableName", "customers"
        )));

        assertEquals(List.of(MAIN), databases);
        assertEquals(List.of("customers", "notes"), objects.getTables());
        assertEquals(List.of("v_active"), objects.getViews());
        assertEquals(List.of(), objects.getFunctions());
        assertEquals(List.of("tr_email"), objects.getTriggers());
        assertEquals(5, columns.size());
        assertEquals("PRI", columns.getFirst().get(ExplorerKeyEnum.COLUMN_KEY));
        assertEquals("auto_increment", columns.getFirst().get(ExplorerKeyEnum.EXTRA));
        assertEquals("UNI", columns.get(1).get(ExplorerKeyEnum.COLUMN_KEY));
        assertEquals("varchar", columns.get(1).get(ExplorerKeyEnum.DATA_TYPE));
        assertTrue(tableInfo.getCreateStatement().startsWith("CREATE TABLE customers ("));
        assertTrue(tableInfo.getCreateStatement().contains("CREATE TRIGGER tr_email"));
        assertEquals(3L, ((Number) tableInfo.getStatus().get("table_rows")).longValue());
        assertEquals("PRIMARY", tableInfo.getIndexes().getFirst().get("index_name"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testSelectBlobNullAndActiveDatabase() throws SQLException {
        ExecuteQueryResultDto result = execute(connectionUuid, QUERY_SESSION, MAIN, "SELECT name, avatar, active FROM customers ORDER BY id", 0);
        List<List<String>> rows = (List<List<String>>) result.getResults().getFirst().get(QueryKeyEnum.ROWS);

        assertEquals(MAIN, result.getCurrentDatabase());
        assertEquals("Ána", rows.getFirst().get(0));
        assertEquals("0xCAFEBABE", rows.getFirst().get(1));
        assertEquals(null, rows.get(1).get(0));
        assertEquals("Carla; con 'comillas'", rows.get(2).get(0));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testGridChangesAreAtomic() throws SQLException {
        int affectedRows = applyChanges(connectionUuid, "ctab-lite:data-1", List.of(
            change(TableChangeKeyEnum.TYPE_DELETE, Map.of("id", "3"), Map.of()),
            change(TableChangeKeyEnum.TYPE_UPDATE, Map.of("id", "1"), Map.of("txt", "it's new")),
            change(TableChangeKeyEnum.TYPE_INSERT, Map.of(), Map.of())
        ));
        SQLException rowGone = assertThrows(SQLException.class, () -> applyChanges(connectionUuid, "ctab-lite:data-1", List.of(
            change(TableChangeKeyEnum.TYPE_UPDATE, Map.of("id", "2"), Map.of("txt", "changed")),
            change(TableChangeKeyEnum.TYPE_DELETE, Map.of("id", "99"), Map.of())
        )));
        List<List<String>> rows = (List<List<String>>) execute(connectionUuid, QUERY_SESSION, MAIN, "SELECT id, txt FROM notes ORDER BY id", 0)
            .getResults().getFirst().get(QueryKeyEnum.ROWS);

        assertEquals(3, affectedRows);
        assertTrue(rowGone.getMessage().contains("Expected 1 row"));
        assertEquals(List.of("1", "it's new"), rows.getFirst());
        assertEquals(List.of("2", "dos"), rows.get(1));
        assertEquals(3, rows.size());
    }

    /**
     * Feature 13 con SQLite: el tunel PHP en .../sqlite abre un fichero de su lista por la clave.
     */
    @Test
    @SuppressWarnings("unchecked")
    void testHttpTunnelToSqlite() throws Exception {
        String testHttp = System.getenv("JAVAYOG_TEST_HTTP");
        String sqliteTunnel = System.getenv("JAVAYOG_TEST_SQLITE_TUNNEL");
        Assumptions.assumeTrue(testHttp != null && sqliteTunnel != null, "HTTP tunnel to SQLite not configured");
        String[] keyDirPassword = sqliteTunnel.split("\\|", 3);
        Path tunnelFile = Path.of(keyDirPassword[1], Path.of(keyDirPassword[0]).getFileName().toString());
        Files.deleteIfExists(tunnelFile);
        Files.createFile(tunnelFile);
        String localUuid = createConnection(Primitives.of("connectionName", "lite-it-seed", "dbEngine", "sqlite", "databaseName", tunnelFile.toString()));
        seed(localUuid, "ctab-lite-seed:query-1");
        TargetSessionFactory.getInstance().closeSessions("ctab-lite-seed");

        Map<String, Object> tunnelForm = Primitives.of(
            "connectionName", "lite-it-tunnel", "dbEngine", "sqlite", "databaseName", keyDirPassword[0], "userPassword", keyDirPassword[2],
            "httpEnabled", true, "httpUrl", testHttp.split("\\|")[0], "httpTimeoutS", 20
        );
        String version = TestConnectionService.getInstance().invoke(TestConnectionDto.fromPrimitives(tunnelForm)).getServerVersion();
        String tunnelUuid = createConnection(tunnelForm);
        ListDatabaseObjectsResultDto objects = ListDatabaseObjectsService.getInstance().invoke(ListDatabaseObjectsDto.fromPrimitives(Primitives.of(
            "connectionUuid", tunnelUuid, "sessionKey", "ctab-litet:explorer", "databaseName", MAIN
        )));
        List<Map<String, Object>> columns = ListTableColumnsService.getInstance().invoke(ListTableColumnsDto.fromPrimitives(Primitives.of(
            "connectionUuid", tunnelUuid, "sessionKey", "ctab-litet:explorer", "databaseName", MAIN, "tableName", "customers"
        ))).getColumns();
        List<List<String>> rows = (List<List<String>>) execute(tunnelUuid, "ctab-litet:query-1", MAIN,
            "SELECT name FROM customers ORDER BY id", 0).getResults().getFirst().get(QueryKeyEnum.ROWS);
        int affectedRows = applyChanges(tunnelUuid, "ctab-litet:data-1", List.of(
            change(TableChangeKeyEnum.TYPE_UPDATE, Map.of("id", "1"), Map.of("txt", "it's \\ ok")),
            change(TableChangeKeyEnum.TYPE_DELETE, Map.of("id", "3"), Map.of())
        ));
        SQLException rowGone = assertThrows(SQLException.class, () -> applyChanges(tunnelUuid, "ctab-litet:data-1", List.of(
            change(TableChangeKeyEnum.TYPE_UPDATE, Map.of("id", "2"), Map.of("txt", "lost")),
            change(TableChangeKeyEnum.TYPE_DELETE, Map.of("id", "99"), Map.of())
        )));
        List<List<String>> notes = (List<List<String>>) execute(tunnelUuid, "ctab-litet:query-1", MAIN, "SELECT id, txt FROM notes ORDER BY id", 0)
            .getResults().getFirst().get(QueryKeyEnum.ROWS);

        assertTrue(version.contains("SQLite"));
        assertEquals(List.of("customers", "notes"), objects.getTables());
        assertEquals("PRI", columns.getFirst().get(ExplorerKeyEnum.COLUMN_KEY));
        assertEquals("Ána", rows.getFirst().getFirst());
        assertEquals(2, affectedRows);
        assertTrue(rowGone.getMessage().contains("Expected 1 row"));
        assertEquals(List.of(List.of("1", "it's \\ ok"), List.of("2", "dos")), notes);
    }

    @Test
    @SuppressWarnings("unchecked")
    void testDuplicateTableCopiesStructureAndData() throws SQLException {
        String copyTableName = DuplicateTableService.getInstance().invoke(DuplicateTableDto.fromPrimitives(Primitives.of(
            "connectionUuid", connectionUuid, "sessionKey", EXPLORER_SESSION, "databaseName", MAIN, "tableName", "customers"
        ))).getCopyTableName();
        List<List<String>> counts = (List<List<String>>) execute(connectionUuid, QUERY_SESSION, MAIN,
            "SELECT (SELECT COUNT(*) FROM %s), (SELECT COUNT(*) FROM customers)".formatted(copyTableName), 0).getResults().getFirst().get(QueryKeyEnum.ROWS);
        execute(connectionUuid, QUERY_SESSION, MAIN, "DROP TABLE %s".formatted(copyTableName), 0);

        assertTrue(copyTableName.matches("customers_copy_[0-9]{14}"));
        assertEquals(counts.getFirst().get(0), counts.getFirst().get(1));
        assertNotEquals("0", counts.getFirst().get(0));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testFilterByAHostileCellValueOnlyFilters() {
        String hostileValue = "x\\'); DROP TABLE notes; --";
        String countSql = "SELECT COUNT(*) FROM notes";
        List<List<String>> countBefore = (List<List<String>>) execute(connectionUuid, QUERY_SESSION, MAIN, countSql, 0).getResults().getFirst().get(QueryKeyEnum.ROWS);
        execute(connectionUuid, QUERY_SESSION, MAIN, "INSERT INTO notes (txt) VALUES ('x' || char(92) || '''); DROP TABLE notes; --')", 0);
        String condition = TableDataQueryBuilder.getInstance("sqlite").getColumnCondition("txt", "=", hostileValue);

        ExecuteQueryResultDto filtered = execute(connectionUuid, QUERY_SESSION, MAIN, "SELECT txt FROM notes WHERE " + condition, 0);
        execute(connectionUuid, QUERY_SESSION, MAIN, "DELETE FROM notes WHERE " + condition, 0);
        List<List<String>> countAfter = (List<List<String>>) execute(connectionUuid, QUERY_SESSION, MAIN, countSql, 0).getResults().getFirst().get(QueryKeyEnum.ROWS);

        assertEquals(1, filtered.getResults().size());
        assertEquals(List.of(List.of(hostileValue)), filtered.getResults().getFirst().get(QueryKeyEnum.ROWS));
        assertEquals(countBefore, countAfter);
    }

    @Test
    @SuppressWarnings("unchecked")
    void testDuplicateTableOntoAnExistingTableKeepsThatTable() {
        assertThrows(SQLException.class, () -> DuplicateTableService.getInstance().invoke(DuplicateTableDto.fromPrimitives(Primitives.of(
            "connectionUuid", connectionUuid, "sessionKey", EXPLORER_SESSION, "databaseName", MAIN, "tableName", "customers",
            "copyTableName", "notes"
        ))));
        List<List<String>> notesCount = (List<List<String>>) execute(connectionUuid, QUERY_SESSION, MAIN,
            "SELECT COUNT(*) FROM notes", 0).getResults().getFirst().get(QueryKeyEnum.ROWS);

        assertEquals("3", notesCount.getFirst().getFirst());
    }

    @Test
    @SuppressWarnings("unchecked")
    void testDropTableAsksForConfirmationOnProductionAndDropsTheTable() throws SQLException {
        String copyTableName = DuplicateTableService.getInstance().invoke(DuplicateTableDto.fromPrimitives(Primitives.of(
            "connectionUuid", connectionUuid, "sessionKey", EXPLORER_SESSION, "databaseName", MAIN, "tableName", "customers"
        ))).getCopyTableName();
        String productionUuid = createConnection(Primitives.of(
            "connectionName", "lite-prod", "dbEngine", "sqlite", "databaseName", sqliteFile.toString(), "isProduction", true
        ));
        String countSql = "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = '%s'".formatted(copyTableName);

        ExplorerException notConfirmed = assertThrows(ExplorerException.class, () -> dropTable(productionUuid, copyTableName, false));
        String countAfterRefusal = ((List<List<String>>) execute(connectionUuid, QUERY_SESSION, MAIN, countSql, 0).getResults().getFirst().get(QueryKeyEnum.ROWS)).getFirst().getFirst();
        String dropSql = dropTable(connectionUuid, copyTableName, false);
        String countAfterDrop = ((List<List<String>>) execute(connectionUuid, QUERY_SESSION, MAIN, countSql, 0).getResults().getFirst().get(QueryKeyEnum.ROWS)).getFirst().getFirst();

        assertEquals(ExceptionEnum.CODE_PRECONDITION_REQUIRED, notConfirmed.getCode());
        assertEquals("1", countAfterRefusal);
        assertEquals("DROP TABLE \"%s\"".formatted(copyTableName), dropSql);
        assertEquals("0", countAfterDrop);
        assertThrows(SQLException.class, () -> dropTable(productionUuid, copyTableName, true));
        ExplorerException withoutDatabase = assertThrows(ExplorerException.class, () -> DropTableService.getInstance().invoke(DropTableDto.fromPrimitives(Primitives.of(
            "connectionUuid", connectionUuid, "sessionKey", EXPLORER_SESSION, "databaseName", "", "tableName", "notes", "isConfirmed", true
        ))));
        assertEquals(ExceptionEnum.CODE_BAD_REQUEST, withoutDatabase.getCode());
    }

    private static String dropTable(String connectionUuid, String tableName, boolean isConfirmed) throws SQLException {
        return DropTableService.getInstance().invoke(DropTableDto.fromPrimitives(Primitives.of(
            "connectionUuid", connectionUuid, "sessionKey", EXPLORER_SESSION + ":" + connectionUuid, "databaseName", MAIN,
            "tableName", tableName, "isConfirmed", isConfirmed
        ))).getDropSql();
    }

    private static void seed(String connectionUuid, String sessionKey) {
        ExecuteQueryResultDto seedResult = execute(connectionUuid, sessionKey, MAIN, """
            CREATE TABLE customers (
                id INTEGER PRIMARY KEY,
                email VARCHAR(100) NOT NULL UNIQUE,
                name TEXT,
                avatar BLOB,
                active BOOLEAN NOT NULL DEFAULT 1
            );
            INSERT INTO customers (email, name, avatar, active) VALUES
                ('ana@x.es', 'Ána', X'CAFEBABE', 1),
                ('bob@y.es', NULL, NULL, 0),
                ('carla@z.es', 'Carla; con ''comillas''', NULL, 1);
            CREATE VIEW v_active AS SELECT id, email FROM customers WHERE active;
            CREATE TRIGGER tr_email BEFORE INSERT ON customers BEGIN
                SELECT 1;
                SELECT 2;
            END;
            CREATE TABLE notes (id INTEGER PRIMARY KEY, txt VARCHAR(50));
            INSERT INTO notes (txt) VALUES ('uno'), ('dos'), ('tres');
            """, 0);
        for (Map<String, Object> result : seedResult.getResults()) {
            org.junit.jupiter.api.Assertions.assertNull(result.get(QueryKeyEnum.ERROR_MESSAGE), String.valueOf(result.get(QueryKeyEnum.ERROR_MESSAGE)));
        }
    }

    private static Map<String, Object> change(String changeType, Map<String, String> keyValues, Map<String, String> newValues) {
        return Primitives.of(
            TableChangeKeyEnum.CHANGE_TYPE, changeType,
            TableChangeKeyEnum.KEY_VALUES, keyValues,
            TableChangeKeyEnum.NEW_VALUES, newValues
        );
    }

    private static int applyChanges(String connectionUuid, String sessionKey, List<Map<String, Object>> changes) throws SQLException {
        return ApplyTableChangesService.getInstance().invoke(ApplyTableChangesDto.fromPrimitives(Primitives.of(
            "connectionUuid", connectionUuid,
            "sessionKey", sessionKey,
            "databaseName", MAIN,
            "tableName", "notes",
            "changes", changes,
            "isConfirmed", false
        ))).getAffectedRows();
    }

    private static String createConnection(Map<String, Object> form) {
        return String.valueOf(CreateConnectionService.getInstance().invoke(CreateConnectionDto.fromPrimitives(form))
            .getConnection().get(ConnectionsKeyEnum.CONNECTION_UUID));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testCreateTableWithIndexesAndForeignKey() throws SQLException {
        String createdTableName = CreateTableService.getInstance().invoke(CreateTableDto.fromPrimitives(Primitives.of(
            "connectionUuid", connectionUuid, "sessionKey", EXPLORER_SESSION, "databaseName", MAIN,
            "edited", TableDefinitionMother.getOrders("INTEGER", "", "")
        ))).getTableName();
        execute(connectionUuid, QUERY_SESSION, MAIN, "INSERT INTO orders (customer_id, total) VALUES (NULL, 9.5)", 0);
        List<List<String>> checks = (List<List<String>>) execute(connectionUuid, QUERY_SESSION, MAIN,
            "SELECT (SELECT MAX(id) FROM orders), (SELECT COUNT(*) FROM sqlite_master WHERE type = 'index' AND name = 'ix_orders_customer')", 0
        ).getResults().getFirst().get(QueryKeyEnum.ROWS);
        execute(connectionUuid, QUERY_SESSION, MAIN, "DROP TABLE orders", 0);

        assertEquals("orders", createdTableName);
        assertEquals(List.of("1", "1"), checks.getFirst());
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
