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
import modules.query.application.applytablechanges.ApplyTableChangesDto;
import modules.query.application.executequery.ExecuteQueryDto;
import modules.query.application.executequery.ExecuteQueryResultDto;
import modules.query.application.applytablechanges.ApplyTableChangesService;
import modules.query.application.executequery.ExecuteQueryService;

/**
 * PostgreSQL: solo si existe JAVAYOG_TEST_PGSQL=host:puerto:usuario:password (make test-mysql levanta un PostgreSQL 16
 * desechable). Con JAVAYOG_TEST_HTTP y JAVAYOG_TEST_PGSQL_FROM_TUNNEL=host:puerto (el pg visto desde el tunel PHP),
 * tambien por el tunel HTTP (.../pg). En pg, la "BD" del arbol es un esquema: aqui jy_it.
 */
final class PgsqlIntegrationTest extends AbstractTestCase {

    private static final String SCHEMA = "jy_it";
    private static final String EXPLORER_SESSION = "ctab-pg:explorer";
    private static final String QUERY_SESSION = "ctab-pg:query-1";

    private static String[] pgsql;
    private static String connectionUuid;

    @BeforeAll
    static void seedSchema() throws SQLException {
        String testPgsql = System.getenv("JAVAYOG_TEST_PGSQL");
        Assumptions.assumeTrue(testPgsql != null && !testPgsql.isBlank(), "JAVAYOG_TEST_PGSQL not set: PostgreSQL tests skipped");
        pgsql = testPgsql.split(":", 4);

        bootFreshDatabase("PgsqlIntegrationTest");
        connectionUuid = createConnection(Primitives.of(
            "connectionName", "pg-it", "dbEngine", "pgsql",
            "host", pgsql[0], "port", pgsql[1], "userName", pgsql[2], "userPassword", pgsql[3]
        ));

        // el propio editor siembra: $$ con ; dentro, comillas dobles y varias sentencias
        ExecuteQueryResultDto seedResult = execute(connectionUuid, QUERY_SESSION, "", """
            DROP SCHEMA IF EXISTS jy_it CASCADE;
            CREATE SCHEMA jy_it;
            CREATE TABLE jy_it.customers (
                id serial PRIMARY KEY,
                email varchar(100) NOT NULL UNIQUE,
                "full name" text,
                prefs jsonb,
                avatar bytea,
                active boolean NOT NULL DEFAULT true
            );
            CREATE INDEX ix_customers_name ON jy_it.customers ("full name", email);
            INSERT INTO jy_it.customers (email, "full name", prefs, avatar, active) VALUES
                ('ana@x.es', 'Ána', '{"lang": "es"}', '\\xcafebabe', true),
                ('bob@y.es', NULL, NULL, NULL, false),
                ('carla@z.es', 'Carla; con ''comillas''', NULL, NULL, true);
            CREATE VIEW jy_it.v_active AS SELECT id, email FROM jy_it.customers WHERE active;
            CREATE FUNCTION jy_it.plus_one(n int) RETURNS int AS $$
            BEGIN
                RETURN n + 1;
            END;
            $$ LANGUAGE plpgsql;
            CREATE PROCEDURE jy_it.touch() LANGUAGE sql AS $body$ SELECT 1; $body$;
            CREATE FUNCTION jy_it.lower_email() RETURNS trigger AS $$
            BEGIN
                NEW.email := lower(NEW.email);
                RETURN NEW;
            END;
            $$ LANGUAGE plpgsql;
            CREATE TRIGGER tr_email BEFORE INSERT ON jy_it.customers FOR EACH ROW EXECUTE FUNCTION jy_it.lower_email();
            CREATE TABLE jy_it.notes (id int GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY, txt varchar(50));
            INSERT INTO jy_it.notes (txt) VALUES ('uno'), ('dos'), ('tres');
            """, 0);
        for (Map<String, Object> result : seedResult.getResults()) {
            org.junit.jupiter.api.Assertions.assertNull(result.get(QueryKeyEnum.ERROR_MESSAGE), String.valueOf(result.get(QueryKeyEnum.ERROR_MESSAGE)));
        }
    }

    @AfterAll
    static void dropSchema() throws SQLException {
        if (connectionUuid == null) return;
        execute(connectionUuid, QUERY_SESSION, "", "DROP SCHEMA IF EXISTS jy_it CASCADE", 0);
        TargetSessionFactory.getInstance().closeAll();
    }

    @Test
    void testConnectionTestAndExplorer() throws SQLException {
        String serverVersion = TestConnectionService.getInstance().invoke(TestConnectionDto.fromPrimitives(Primitives.of(
            "connectionName", "pg-it-test", "dbEngine", "pgsql",
            "host", pgsql[0], "port", pgsql[1], "userName", pgsql[2], "userPassword", pgsql[3]
        ))).getServerVersion();
        List<String> schemas = ListDatabasesService.getInstance().invoke(ListDatabasesDto.fromPrimitives(Primitives.of(
            "connectionUuid", connectionUuid, "sessionKey", EXPLORER_SESSION
        ))).getDatabaseNames();
        ListDatabaseObjectsResultDto objects = ListDatabaseObjectsService.getInstance().invoke(ListDatabaseObjectsDto.fromPrimitives(Primitives.of(
            "connectionUuid", connectionUuid, "sessionKey", EXPLORER_SESSION, "databaseName", SCHEMA
        )));
        List<Map<String, Object>> columns = ListTableColumnsService.getInstance().invoke(ListTableColumnsDto.fromPrimitives(Primitives.of(
            "connectionUuid", connectionUuid, "sessionKey", EXPLORER_SESSION, "databaseName", SCHEMA, "tableName", "customers"
        ))).getColumns();

        assertTrue(serverVersion.startsWith("PostgreSQL 16"));
        assertTrue(schemas.contains(SCHEMA) && schemas.contains("public"));
        assertEquals(List.of("customers", "notes"), objects.getTables());
        assertEquals(List.of("v_active"), objects.getViews());
        assertEquals(List.of("touch"), objects.getProcedures());
        assertEquals(List.of("lower_email", "plus_one"), objects.getFunctions());
        assertEquals(List.of("tr_email"), objects.getTriggers());
        assertEquals(6, columns.size());
        assertEquals("PRI", columns.getFirst().get(ExplorerKeyEnum.COLUMN_KEY));
        assertEquals("auto_increment", columns.getFirst().get(ExplorerKeyEnum.EXTRA));
        assertEquals("UNI", columns.get(1).get(ExplorerKeyEnum.COLUMN_KEY));
        assertEquals("character varying(100)", columns.get(1).get(ExplorerKeyEnum.COLUMN_TYPE));
        assertEquals(false, columns.get(1).get(ExplorerKeyEnum.IS_NULLABLE));
        assertEquals("bytea", columns.get(4).get(ExplorerKeyEnum.DATA_TYPE));
    }

    @Test
    void testInfoBuildsTheDdlWithConstraintsAndIndexes() throws SQLException {
        GetTableInfoResultDto tableInfo = GetTableInfoService.getInstance().invoke(GetTableInfoDto.fromPrimitives(Primitives.of(
            "connectionUuid", connectionUuid, "sessionKey", EXPLORER_SESSION, "databaseName", SCHEMA, "tableName", "customers"
        )));
        GetTableInfoResultDto viewInfo = GetTableInfoService.getInstance().invoke(GetTableInfoDto.fromPrimitives(Primitives.of(
            "connectionUuid", connectionUuid, "sessionKey", EXPLORER_SESSION, "databaseName", SCHEMA, "tableName", "v_active"
        )));

        String ddl = tableInfo.getCreateStatement();
        assertTrue(ddl.startsWith("CREATE TABLE \"jy_it\".\"customers\" ("));
        assertTrue(ddl.contains("\"full name\" text"));
        assertTrue(ddl.contains("PRIMARY KEY (id)"));
        assertTrue(ddl.contains("CREATE INDEX ix_customers_name"));
        assertEquals("BASE TABLE", tableInfo.getStatus().get("table_type"));
        // PRIMARY + email UNIQUE + ix_customers_name (2 columnas)
        assertEquals(4, tableInfo.getIndexes().size());
        assertEquals("PRIMARY", tableInfo.getIndexes().getFirst().get("index_name"));
        assertTrue(viewInfo.isView());
        assertTrue(viewInfo.getCreateStatement().startsWith("CREATE VIEW \"jy_it\".\"v_active\" AS"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testSelectReadsBooleanByteaJsonbAndNull() throws SQLException {
        Map<String, Object> result = execute(connectionUuid, QUERY_SESSION, SCHEMA,
            "SELECT \"full name\", prefs, avatar, active FROM customers ORDER BY id", 0).getResults().getFirst();
        List<List<String>> rows = (List<List<String>>) result.get(QueryKeyEnum.ROWS);

        assertEquals("Ána", rows.getFirst().get(0));
        assertTrue(rows.getFirst().get(1).contains("\"lang\""));
        assertEquals("0xCAFEBABE", rows.getFirst().get(2));
        assertEquals("true", rows.getFirst().get(3));
        assertEquals(null, rows.get(1).get(0));
        assertEquals("false", rows.get(1).get(3));
        assertEquals("Carla; con 'comillas'", rows.get(2).get(0));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testActiveSchemaIsTheSearchPathAndErrorsCarryTheSqlState() throws SQLException {
        ExecuteQueryResultDto inSchema = execute(connectionUuid, "ctab-pg:query-2", SCHEMA, "SELECT plus_one(41), current_schema()", 0);
        ExecuteQueryResultDto definition = execute(connectionUuid, "ctab-pg:query-2", SCHEMA,
            ObjectDefinitionSqlBuilder.getInstance("pgsql").getDefinitionSql(ExplorerNodeTypeEnum.FUNCTION, SCHEMA, "plus_one"), 0);
        ExecuteQueryResultDto failed = execute(connectionUuid, "ctab-pg:query-2", SCHEMA, "SELECT * FROM nope", 0);

        List<String> firstRow = ((List<List<String>>) inSchema.getResults().getFirst().get(QueryKeyEnum.ROWS)).getFirst();
        assertEquals(List.of("42", SCHEMA), firstRow);
        assertEquals(SCHEMA, inSchema.getCurrentDatabase());
        assertTrue(((List<List<String>>) definition.getResults().getFirst().get(QueryKeyEnum.ROWS)).getFirst().getFirst().contains("RETURN n + 1"));
        assertTrue(String.valueOf(failed.getResults().getLast().get(QueryKeyEnum.ERROR_MESSAGE)).contains("does not exist"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testGridChangesInOneTransactionWithTypedValues() throws SQLException {
        int affectedRows = applyChanges(connectionUuid, "ctab-pg:data-1", "notes", List.of(
            change(TableChangeKeyEnum.TYPE_DELETE, Map.of("id", "3"), Map.of()),
            change(TableChangeKeyEnum.TYPE_UPDATE, Map.of("id", "1"), Map.of("txt", "it's new")),
            change(TableChangeKeyEnum.TYPE_INSERT, Map.of(), Map.of()),
            change(TableChangeKeyEnum.TYPE_INSERT, Map.of(), Map.of("id", "50", "txt", "con id"))
        ));
        SQLException rowGone = assertThrows(SQLException.class, () -> applyChanges(connectionUuid, "ctab-pg:data-1", "notes", List.of(
            change(TableChangeKeyEnum.TYPE_UPDATE, Map.of("id", "2"), Map.of("txt", "changed")),
            change(TableChangeKeyEnum.TYPE_DELETE, Map.of("id", "99"), Map.of())
        )));
        List<List<String>> rows = (List<List<String>>) execute(connectionUuid, QUERY_SESSION, SCHEMA, "SELECT id, txt FROM notes ORDER BY id", 0)
            .getResults().getFirst().get(QueryKeyEnum.ROWS);

        assertEquals(4, affectedRows);
        assertTrue(rowGone.getMessage().contains("rolled back") || rowGone.getMessage().contains("Expected 1 row"));
        assertEquals(List.of("1", "it's new"), rows.getFirst());
        assertEquals(List.of("2", "dos"), rows.get(1));
        assertEquals(List.of("50", "con id"), rows.getLast());
        assertEquals(4, rows.size());
    }

    /**
     * Feature 13 con PostgreSQL: el tunel PHP en .../pg.
     */
    @Test
    @SuppressWarnings("unchecked")
    void testHttpTunnelToPostgresql() throws SQLException {
        String testHttp = System.getenv("JAVAYOG_TEST_HTTP");
        String pgsqlFromTunnel = System.getenv("JAVAYOG_TEST_PGSQL_FROM_TUNNEL");
        Assumptions.assumeTrue(testHttp != null && pgsqlFromTunnel != null, "HTTP tunnel to PostgreSQL not configured");
        String[] tunnelTarget = pgsqlFromTunnel.split(":", 2);
        Map<String, Object> tunnelForm = Primitives.of(
            "connectionName", "pg-it-tunnel", "dbEngine", "pgsql",
            "host", tunnelTarget[0], "port", tunnelTarget[1], "userName", pgsql[2], "userPassword", pgsql[3],
            "httpEnabled", true, "httpUrl", testHttp.split("\\|")[0], "httpTimeoutS", 20
        );

        String serverVersion = TestConnectionService.getInstance().invoke(TestConnectionDto.fromPrimitives(tunnelForm)).getServerVersion();
        String tunnelUuid = createConnection(tunnelForm);
        ListDatabaseObjectsResultDto objects = ListDatabaseObjectsService.getInstance().invoke(ListDatabaseObjectsDto.fromPrimitives(Primitives.of(
            "connectionUuid", tunnelUuid, "sessionKey", "ctab-pgt:explorer", "databaseName", SCHEMA
        )));
        List<List<String>> rows = (List<List<String>>) execute(tunnelUuid, "ctab-pgt:query-1", SCHEMA,
            "SELECT email FROM \"jy_it\".\"customers\" ORDER BY id", 0).getResults().getFirst().get(QueryKeyEnum.ROWS);
        execute(connectionUuid, QUERY_SESSION, SCHEMA, "CREATE TABLE tags (id int PRIMARY KEY, name text); INSERT INTO tags VALUES (1, 'a'), (2, 'b')", 0);
        int affectedRows = applyChanges(tunnelUuid, "ctab-pgt:data-1", "tags", List.of(
            change(TableChangeKeyEnum.TYPE_UPDATE, Map.of("id", "1"), Map.of("name", "it's \\ ok")),
            change(TableChangeKeyEnum.TYPE_DELETE, Map.of("id", "2"), Map.of())
        ));
        SQLException rowGone = assertThrows(SQLException.class, () -> applyChanges(tunnelUuid, "ctab-pgt:data-1", "tags", List.of(
            change(TableChangeKeyEnum.TYPE_UPDATE, Map.of("id", "1"), Map.of("name", "lost")),
            change(TableChangeKeyEnum.TYPE_DELETE, Map.of("id", "99"), Map.of())
        )));
        List<List<String>> tags = (List<List<String>>) execute(connectionUuid, QUERY_SESSION, SCHEMA, "SELECT id, name FROM tags ORDER BY id", 0)
            .getResults().getFirst().get(QueryKeyEnum.ROWS);

        assertTrue(serverVersion.startsWith("16") || serverVersion.contains("PostgreSQL"));
        assertTrue(objects.getTables().containsAll(List.of("customers", "notes")));
        assertEquals("ana@x.es", rows.getFirst().getFirst());
        assertEquals(2, affectedRows);
        assertTrue(rowGone.getMessage().contains("Expected 1 row"));
        assertEquals(List.of(List.of("1", "it's \\ ok")), tags);
    }

    @Test
    @SuppressWarnings("unchecked")
    void testDuplicateTableCopiesStructureAndData() throws SQLException {
        String copyTableName = DuplicateTableService.getInstance().invoke(DuplicateTableDto.fromPrimitives(Primitives.of(
            "connectionUuid", connectionUuid, "sessionKey", EXPLORER_SESSION, "databaseName", SCHEMA, "tableName", "customers"
        ))).getCopyTableName();
        List<List<String>> counts = (List<List<String>>) execute(connectionUuid, QUERY_SESSION, SCHEMA,
            "SELECT (SELECT COUNT(*) FROM jy_it.%s), (SELECT COUNT(*) FROM jy_it.customers)".formatted(copyTableName), 0).getResults().getFirst().get(QueryKeyEnum.ROWS);
        execute(connectionUuid, QUERY_SESSION, SCHEMA, "DROP TABLE jy_it.%s".formatted(copyTableName), 0);

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

    private static int applyChanges(String connectionUuid, String sessionKey, String tableName, List<Map<String, Object>> changes) throws SQLException {
        return ApplyTableChangesService.getInstance().invoke(ApplyTableChangesDto.fromPrimitives(Primitives.of(
            "connectionUuid", connectionUuid,
            "sessionKey", sessionKey,
            "databaseName", SCHEMA,
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
    void testCreateTableWithIdentityIndexesAndComments() throws SQLException {
        String createdTableName = CreateTableService.getInstance().invoke(CreateTableDto.fromPrimitives(Primitives.of(
            "connectionUuid", connectionUuid, "sessionKey", EXPLORER_SESSION, "databaseName", SCHEMA,
            "edited", TableDefinitionMother.getOrders("int", "con IVA", "pedidos")
        ))).getTableName();
        execute(connectionUuid, QUERY_SESSION, SCHEMA, "INSERT INTO jy_it.orders (customer_id, total) VALUES (NULL, 9.5)", 0);
        List<List<String>> checks = (List<List<String>>) execute(connectionUuid, QUERY_SESSION, SCHEMA, """
            SELECT (SELECT MAX(id) FROM jy_it.orders),
                (SELECT COUNT(*) FROM pg_indexes WHERE schemaname = 'jy_it' AND indexname = 'ix_orders_customer'),
                obj_description('jy_it.orders'::regclass, 'pg_class')
            """, 0).getResults().getFirst().get(QueryKeyEnum.ROWS);
        execute(connectionUuid, QUERY_SESSION, SCHEMA, "DROP TABLE jy_it.orders", 0);

        assertEquals("orders", createdTableName);
        assertEquals(List.of("1", "1", "pedidos"), checks.getFirst());
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
