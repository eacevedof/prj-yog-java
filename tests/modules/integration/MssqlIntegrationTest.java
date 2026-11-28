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
import modules.explorer.domain.enums.ExplorerNodeTypeEnum;
import modules.explorer.domain.services.ObjectDefinitionSqlBuilder;
import modules.explorer.domain.mothers.TableDefinitionMother;
import modules.explorer.application.createtable.CreateTableDto;
import modules.explorer.application.duplicatetable.DuplicateTableDto;
import modules.explorer.application.gettableinfo.GetTableInfoDto;
import modules.explorer.application.gettableinfo.GetTableInfoResultDto;
import modules.explorer.application.listdatabaseobjects.ListDatabaseObjectsDto;
import modules.explorer.application.listdatabaseobjects.ListDatabaseObjectsResultDto;
import modules.explorer.application.listdatabases.ListDatabasesDto;
import modules.explorer.application.listtablecolumns.ListTableColumnsDto;
import modules.explorer.application.createtable.CreateTableService;
import modules.explorer.application.duplicatetable.DuplicateTableService;
import modules.explorer.application.gettableinfo.GetTableInfoService;
import modules.explorer.application.listdatabaseobjects.ListDatabaseObjectsService;
import modules.explorer.application.listdatabases.ListDatabasesService;
import modules.explorer.application.listtablecolumns.ListTableColumnsService;

import modules.query.domain.enums.QueryKeyEnum;
import modules.query.domain.enums.TableChangeKeyEnum;
import modules.query.domain.services.TableDataQueryBuilder;
import modules.query.application.applytablechanges.ApplyTableChangesDto;
import modules.query.application.executequery.ExecuteQueryDto;
import modules.query.application.executequery.ExecuteQueryResultDto;
import modules.query.application.applytablechanges.ApplyTableChangesService;
import modules.query.application.executequery.ExecuteQueryService;

/**
 * SQL Server: solo si existe JAVAYOG_TEST_MSSQL=host:puerto:usuario:password (make test-mysql levanta un SQL Server
 * 2022 desechable). El arbol lista BDs, como en MySQL: aqui jy_it, con una tabla en dbo y otra en el esquema ventas.
 */
final class MssqlIntegrationTest extends AbstractTestCase {

    private static final String DATABASE = "jy_it";
    private static final String EXPLORER_SESSION = "ctab-ms:explorer";
    private static final String QUERY_SESSION = "ctab-ms:query-1";

    private static String[] mssql;
    private static String connectionUuid;

    @BeforeAll
    static void seedDatabase() throws SQLException {
        String testMssql = System.getenv("JAVAYOG_TEST_MSSQL");
        Assumptions.assumeTrue(testMssql != null && !testMssql.isBlank(), "JAVAYOG_TEST_MSSQL not set: SQL Server tests skipped");
        mssql = testMssql.split(":", 4);

        bootFreshDatabase("MssqlIntegrationTest");
        connectionUuid = createConnection(Primitives.of(
            "connectionName", "ms-it", "dbEngine", "mssql",
            "host", mssql[0], "port", mssql[1], "userName", mssql[2], "userPassword", mssql[3]
        ));

        // el propio editor siembra, en lotes separados por GO (un CREATE PROCEDURE va solo en su lote)
        ExecuteQueryResultDto seedResult = execute(connectionUuid, QUERY_SESSION, "", """
            IF DB_ID('jy_it') IS NOT NULL BEGIN ALTER DATABASE jy_it SET SINGLE_USER WITH ROLLBACK IMMEDIATE; DROP DATABASE jy_it; END
            GO
            CREATE DATABASE jy_it
            GO
            USE jy_it
            GO
            CREATE SCHEMA ventas
            GO
            CREATE TABLE dbo.customers (
                id int IDENTITY(1,1) CONSTRAINT pk_customers PRIMARY KEY,
                email nvarchar(100) NOT NULL CONSTRAINT uq_customers_email UNIQUE,
                [full name] nvarchar(max) NULL,
                avatar varbinary(16) NULL,
                active bit NOT NULL CONSTRAINT df_customers_active DEFAULT 1,
                amount decimal(10,2) NULL
            );
            CREATE INDEX ix_customers_name ON dbo.customers (email DESC) INCLUDE (active);
            INSERT INTO dbo.customers (email, [full name], avatar, active, amount) VALUES
                (N'ana@x.es', N'Ána', 0xCAFEBABE, 1, 10.50),
                (N'bob@y.es', NULL, NULL, 0, NULL),
                (N'carla@z.es', N'Carla; con ''comillas''', NULL, 1, 3);
            CREATE TABLE ventas.pedidos (
                id int NOT NULL PRIMARY KEY,
                customer_id int NOT NULL CONSTRAINT fk_pedidos_customer REFERENCES dbo.customers (id) ON DELETE CASCADE,
                txt varchar(50) NULL
            );
            INSERT INTO ventas.pedidos VALUES (1, 1, 'uno'), (2, 1, 'dos'), (3, 2, 'tres');
            GO
            CREATE VIEW dbo.v_active AS SELECT id, email FROM dbo.customers WHERE active = 1
            GO
            CREATE FUNCTION dbo.plus_one(@n int) RETURNS int AS
            BEGIN
                RETURN @n + 1;
            END
            GO
            CREATE PROCEDURE ventas.touch AS
            BEGIN
                SET NOCOUNT ON;
                SELECT 1 AS one;
            END
            GO
            CREATE TRIGGER dbo.tr_email ON dbo.customers AFTER INSERT AS
            BEGIN
                SET NOCOUNT ON;
                UPDATE c SET email = LOWER(c.email) FROM dbo.customers c JOIN inserted i ON i.id = c.id;
            END
            """, 0);
        for (Map<String, Object> result : seedResult.getResults()) {
            org.junit.jupiter.api.Assertions.assertNull(result.get(QueryKeyEnum.ERROR_MESSAGE), String.valueOf(result.get(QueryKeyEnum.ERROR_MESSAGE)));
        }
    }

    @AfterAll
    static void dropDatabase() throws SQLException {
        if (connectionUuid == null) return;
        execute(connectionUuid, QUERY_SESSION, "master",
            "ALTER DATABASE jy_it SET SINGLE_USER WITH ROLLBACK IMMEDIATE; DROP DATABASE jy_it", 0);
        TargetSessionFactory.getInstance().closeAll();
    }

    @Test
    void testConnectionTestAndExplorer() throws SQLException {
        String serverVersion = TestConnectionService.getInstance().invoke(TestConnectionDto.fromPrimitives(Primitives.of(
            "connectionName", "ms-it-test", "dbEngine", "mssql",
            "host", mssql[0], "port", mssql[1], "userName", mssql[2], "userPassword", mssql[3]
        ))).getServerVersion();
        List<String> databases = ListDatabasesService.getInstance().invoke(ListDatabasesDto.fromPrimitives(Primitives.of(
            "connectionUuid", connectionUuid, "sessionKey", EXPLORER_SESSION
        ))).getDatabaseNames();
        ListDatabaseObjectsResultDto objects = ListDatabaseObjectsService.getInstance().invoke(ListDatabaseObjectsDto.fromPrimitives(Primitives.of(
            "connectionUuid", connectionUuid, "sessionKey", EXPLORER_SESSION, "databaseName", DATABASE
        )));
        List<Map<String, Object>> columns = ListTableColumnsService.getInstance().invoke(ListTableColumnsDto.fromPrimitives(Primitives.of(
            "connectionUuid", connectionUuid, "sessionKey", EXPLORER_SESSION, "databaseName", DATABASE, "tableName", "customers"
        ))).getColumns();

        assertTrue(serverVersion.startsWith("SQL Server 16."));
        assertTrue(databases.contains(DATABASE) && databases.contains("master"));
        assertEquals(List.of("customers", "ventas.pedidos"), objects.getTables());
        assertEquals(List.of("v_active"), objects.getViews());
        assertEquals(List.of("ventas.touch"), objects.getProcedures());
        assertEquals(List.of("plus_one"), objects.getFunctions());
        assertEquals(List.of("tr_email"), objects.getTriggers());
        assertEquals(6, columns.size());
        assertEquals("PRI", columns.getFirst().get(ExplorerKeyEnum.COLUMN_KEY));
        assertEquals("auto_increment", columns.getFirst().get(ExplorerKeyEnum.EXTRA));
        assertEquals("UNI", columns.get(1).get(ExplorerKeyEnum.COLUMN_KEY));
        assertEquals("nvarchar(100)", columns.get(1).get(ExplorerKeyEnum.COLUMN_TYPE));
        assertEquals("nvarchar(max)", columns.get(2).get(ExplorerKeyEnum.COLUMN_TYPE));
        assertEquals(false, columns.get(1).get(ExplorerKeyEnum.IS_NULLABLE));
        assertEquals("decimal(10,2)", columns.get(5).get(ExplorerKeyEnum.COLUMN_TYPE));
    }

    /**
     * Feature 13 con SQL Server: el tunel PHP en .../mssql (contrato sqlyog-tunnel-multidbs §3). Sin estado: cada
     * peticion lleva su BD.
     */
    @Test
    @SuppressWarnings("unchecked")
    void testHttpTunnelToSqlServer() throws SQLException {
        String testHttp = System.getenv("JAVAYOG_TEST_HTTP");
        String mssqlFromTunnel = System.getenv("JAVAYOG_TEST_MSSQL_FROM_TUNNEL");
        Assumptions.assumeTrue(testHttp != null && mssqlFromTunnel != null, "HTTP tunnel to SQL Server not configured");
        String[] tunnelTarget = mssqlFromTunnel.split(":", 2);
        Map<String, Object> tunnelForm = Primitives.of(
            "connectionName", "ms-it-tunnel", "dbEngine", "mssql",
            "host", tunnelTarget[0], "port", tunnelTarget[1], "userName", mssql[2], "userPassword", mssql[3],
            "httpEnabled", true, "httpUrl", testHttp.split("\\|")[0], "httpTimeoutS", 30
        );

        String serverVersion = TestConnectionService.getInstance().invoke(TestConnectionDto.fromPrimitives(tunnelForm)).getServerVersion();
        String tunnelUuid = createConnection(tunnelForm);
        List<String> databases = ListDatabasesService.getInstance().invoke(ListDatabasesDto.fromPrimitives(Primitives.of(
            "connectionUuid", tunnelUuid, "sessionKey", "ctab-mst:explorer"
        ))).getDatabaseNames();
        ListDatabaseObjectsResultDto objects = ListDatabaseObjectsService.getInstance().invoke(ListDatabaseObjectsDto.fromPrimitives(Primitives.of(
            "connectionUuid", tunnelUuid, "sessionKey", "ctab-mst:explorer", "databaseName", DATABASE
        )));
        List<List<String>> rows = (List<List<String>>) execute(tunnelUuid, "ctab-mst:query-1", DATABASE,
            "SELECT email FROM dbo.customers ORDER BY id", 0).getResults().getFirst().get(QueryKeyEnum.ROWS);

        assertTrue(serverVersion.contains("HTTP tunnel") && serverVersion.startsWith("16."));
        assertTrue(databases.contains(DATABASE));
        assertEquals(List.of("customers", "ventas.pedidos"), objects.getTables());
        assertEquals("ana@x.es", rows.getFirst().getFirst());
    }

    @Test
    void testInfoBuildsTheDdlWithConstraintsAndIndexes() throws SQLException {
        GetTableInfoResultDto tableInfo = GetTableInfoService.getInstance().invoke(GetTableInfoDto.fromPrimitives(Primitives.of(
            "connectionUuid", connectionUuid, "sessionKey", EXPLORER_SESSION, "databaseName", DATABASE, "tableName", "customers"
        )));
        GetTableInfoResultDto ordersInfo = GetTableInfoService.getInstance().invoke(GetTableInfoDto.fromPrimitives(Primitives.of(
            "connectionUuid", connectionUuid, "sessionKey", EXPLORER_SESSION, "databaseName", DATABASE, "tableName", "ventas.pedidos"
        )));
        GetTableInfoResultDto viewInfo = GetTableInfoService.getInstance().invoke(GetTableInfoDto.fromPrimitives(Primitives.of(
            "connectionUuid", connectionUuid, "sessionKey", EXPLORER_SESSION, "databaseName", DATABASE, "tableName", "v_active"
        )));

        String ddl = tableInfo.getCreateStatement();
        assertTrue(ddl.startsWith("CREATE TABLE [dbo].[customers] ("));
        assertTrue(ddl.contains("[id] int IDENTITY(1, 1) NOT NULL"));
        assertTrue(ddl.contains("[full name] nvarchar(max) NULL"));
        assertTrue(ddl.contains("DEFAULT ((1))"));
        assertTrue(ddl.contains("CONSTRAINT [pk_customers] PRIMARY KEY CLUSTERED ([id])"));
        assertTrue(ddl.contains("CREATE NONCLUSTERED INDEX [ix_customers_name] ON [dbo].[customers] ([email] DESC) INCLUDE ([active]);"));
        assertTrue(ordersInfo.getCreateStatement().contains("REFERENCES [dbo].[customers] ([id]) ON DELETE CASCADE"));
        assertEquals("BASE TABLE", tableInfo.getStatus().get("table_type"));
        assertEquals("3", String.valueOf(tableInfo.getStatus().get("table_rows")));
        // PRIMARY + uq email + ix_customers_name (sin la INCLUDE)
        assertEquals(3, tableInfo.getIndexes().size());
        assertEquals("PRIMARY", tableInfo.getIndexes().getFirst().get("index_name"));
        assertTrue(viewInfo.isView());
        assertTrue(viewInfo.getCreateStatement().startsWith("CREATE VIEW dbo.v_active"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testSelectTopReadsBitBinaryDecimalAndNull() throws SQLException {
        String selectSql = TableDataQueryBuilder.getInstance("mssql").getSelectSql(DATABASE, "customers", "", "id", 100);
        Map<String, Object> result = execute(connectionUuid, QUERY_SESSION, DATABASE, selectSql, 0).getResults().getFirst();
        List<List<String>> rows = (List<List<String>>) result.get(QueryKeyEnum.ROWS);

        assertEquals("SELECT TOP 100 * FROM [jy_it].[dbo].[customers]\nORDER BY id", selectSql);
        assertEquals("Ána", rows.getFirst().get(2));
        assertEquals("0xCAFEBABE", rows.getFirst().get(3));
        assertEquals("1", rows.getFirst().get(4));
        assertEquals("10.50", rows.getFirst().get(5));
        assertEquals(null, rows.get(1).get(2));
        assertEquals("0", rows.get(1).get(4));
        assertEquals("Carla; con 'comillas'", rows.get(2).get(2));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testActiveDatabaseIsUseAndBatchesAndDefinitions() throws SQLException {
        ExecuteQueryResultDto inDatabase = execute(connectionUuid, "ctab-ms:query-2", DATABASE,
            "SELECT dbo.plus_one(41) AS n, DB_NAME() AS db; SELECT COUNT(*) FROM ventas.pedidos", 0);
        ExecuteQueryResultDto procedure = execute(connectionUuid, "ctab-ms:query-2", DATABASE, "EXEC ventas.touch", 0);
        ExecuteQueryResultDto definition = execute(connectionUuid, "ctab-ms:query-2", DATABASE,
            ObjectDefinitionSqlBuilder.getInstance("mssql").getDefinitionSql(ExplorerNodeTypeEnum.FUNCTION, DATABASE, "plus_one"), 0);
        ExecuteQueryResultDto failed = execute(connectionUuid, "ctab-ms:query-2", DATABASE, "SELECT * FROM nope", 0);

        List<String> firstRow = ((List<List<String>>) inDatabase.getResults().getFirst().get(QueryKeyEnum.ROWS)).getFirst();
        assertEquals(List.of("42", DATABASE), firstRow);
        // un lote sin GO con dos SELECT: dos resultados
        assertEquals(List.of(List.of("3")), inDatabase.getResults().get(1).get(QueryKeyEnum.ROWS));
        assertEquals(DATABASE, inDatabase.getCurrentDatabase());
        assertEquals(List.of(List.of("1")), procedure.getResults().getFirst().get(QueryKeyEnum.ROWS));
        assertTrue(((List<List<String>>) definition.getResults().getFirst().get(QueryKeyEnum.ROWS)).getFirst().getFirst().contains("RETURN @n + 1"));
        assertTrue(String.valueOf(failed.getResults().getLast().get(QueryKeyEnum.ERROR_MESSAGE)).contains("Invalid object name"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testGridChangesInOneTransactionInAnotherSchema() throws SQLException {
        int affectedRows = applyChanges("ventas.pedidos", List.of(
            change(TableChangeKeyEnum.TYPE_DELETE, Map.of("id", "3"), Map.of()),
            change(TableChangeKeyEnum.TYPE_UPDATE, Map.of("id", "1"), Map.of("txt", "it's new")),
            change(TableChangeKeyEnum.TYPE_INSERT, Map.of(), Map.of("id", "50", "customer_id", "1", "txt", "con id"))
        ));
        SQLException rowGone = assertThrows(SQLException.class, () -> applyChanges("ventas.pedidos", List.of(
            change(TableChangeKeyEnum.TYPE_UPDATE, Map.of("id", "2"), Map.of("txt", "changed")),
            change(TableChangeKeyEnum.TYPE_DELETE, Map.of("id", "99"), Map.of())
        )));
        int identityInsert = applyChanges("customers", List.of(change(TableChangeKeyEnum.TYPE_INSERT, Map.of(), Map.of("email", "NEW@X.ES"))));
        List<List<String>> rows = (List<List<String>>) execute(connectionUuid, QUERY_SESSION, DATABASE, "SELECT id, txt FROM ventas.pedidos ORDER BY id", 0)
            .getResults().getFirst().get(QueryKeyEnum.ROWS);
        List<List<String>> lowered = (List<List<String>>) execute(connectionUuid, QUERY_SESSION, DATABASE,
            "SELECT email FROM customers WHERE email = 'new@x.es'", 0).getResults().getFirst().get(QueryKeyEnum.ROWS);

        assertEquals(3, affectedRows);
        assertTrue(rowGone.getMessage().contains("rolled back") || rowGone.getMessage().contains("Expected 1 row"));
        assertEquals(List.of(List.of("1", "it's new"), List.of("2", "dos"), List.of("50", "con id")), rows);
        assertEquals(1, identityInsert);
        assertEquals(List.of(List.of("new@x.es")), lowered);
    }

    @Test
    @SuppressWarnings("unchecked")
    void testDuplicateTableCopiesStructureAndData() throws SQLException {
        String copyTableName = DuplicateTableService.getInstance().invoke(DuplicateTableDto.fromPrimitives(Primitives.of(
            "connectionUuid", connectionUuid, "sessionKey", EXPLORER_SESSION, "databaseName", DATABASE, "tableName", "ventas.pedidos"
        ))).getCopyTableName();
        List<List<String>> counts = (List<List<String>>) execute(connectionUuid, QUERY_SESSION, DATABASE,
            "SELECT (SELECT COUNT(*) FROM %s), (SELECT COUNT(*) FROM ventas.pedidos)".formatted(copyTableName), 0).getResults().getFirst().get(QueryKeyEnum.ROWS);
        // la copia lleva la clave primaria: se puede editar en la rejilla
        List<Map<String, Object>> copyColumns = ListTableColumnsService.getInstance().invoke(ListTableColumnsDto.fromPrimitives(Primitives.of(
            "connectionUuid", connectionUuid, "sessionKey", EXPLORER_SESSION, "databaseName", DATABASE, "tableName", copyTableName
        ))).getColumns();
        execute(connectionUuid, QUERY_SESSION, DATABASE, "DROP TABLE %s".formatted(copyTableName), 0);

        assertTrue(copyTableName.matches("ventas\\.pedidos_copy_\\d{14}"));
        assertEquals("PRI", copyColumns.getFirst().get(ExplorerKeyEnum.COLUMN_KEY));
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

    private static int applyChanges(String tableName, List<Map<String, Object>> changes) throws SQLException {
        return ApplyTableChangesService.getInstance().invoke(ApplyTableChangesDto.fromPrimitives(Primitives.of(
            "connectionUuid", connectionUuid,
            "sessionKey", "ctab-ms:data-1",
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

    @Test
    @SuppressWarnings("unchecked")
    void testCreateTableRunsInsideItsDatabase() throws SQLException {
        String createdTableName = CreateTableService.getInstance().invoke(CreateTableDto.fromPrimitives(Primitives.of(
            "connectionUuid", connectionUuid, "sessionKey", EXPLORER_SESSION, "databaseName", DATABASE,
            "edited", TableDefinitionMother.getOrders("int", "", "")
        ))).getTableName();
        execute(connectionUuid, QUERY_SESSION, DATABASE, "INSERT INTO dbo.orders (customer_id, total) VALUES (NULL, 9.5)", 0);
        List<List<String>> checks = (List<List<String>>) execute(connectionUuid, QUERY_SESSION, DATABASE,
            "SELECT (SELECT MAX(id) FROM dbo.orders), (SELECT COUNT(*) FROM sys.indexes WHERE name = 'ix_orders_customer')", 0
        ).getResults().getFirst().get(QueryKeyEnum.ROWS);
        execute(connectionUuid, QUERY_SESSION, DATABASE, "DROP TABLE dbo.orders", 0);

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
