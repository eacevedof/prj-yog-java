package modules.integration;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import modules.shared.domain.enums.ExceptionEnum;
import modules.shared.application.Primitives;

import testcase.AbstractTestCase;

import modules.connections.domain.enums.ConnectionsKeyEnum;
import modules.connections.application.createconnection.CreateConnectionDto;
import modules.connections.application.createconnection.CreateConnectionService;
import modules.connections.infrastructure.factories.TargetSessionFactory;

import modules.query.domain.enums.QueryKeyEnum;
import modules.query.application.executequery.ExecuteQueryDto;
import modules.query.application.executequery.ExecuteQueryResultDto;
import modules.query.application.executequery.ExecuteQueryService;

import modules.dbcopy.domain.enums.DbCopyKeyEnum;
import modules.dbcopy.domain.enums.DbCopyObjectTypeEnum;
import modules.dbcopy.domain.services.DbCopyProgressListener;
import modules.dbcopy.application.copydatabase.CopyDatabaseDto;
import modules.dbcopy.application.copydatabase.CopyDatabaseResultDto;
import modules.dbcopy.application.listcopyobjects.ListCopyObjectsDto;
import modules.dbcopy.application.listcopyobjects.ListCopyObjectsResultDto;
import modules.dbcopy.application.copydatabase.CopyDatabaseService;
import modules.dbcopy.application.listcopyobjects.ListCopyObjectsService;
import modules.dbcopy.infrastructure.components.DbCopyProgressTracker;
import modules.dbcopy.domain.exceptions.DbCopyException;

/**
 * Feature 29 contra un MySQL de verdad (JAVAYOG_TEST_MYSQL; make test-mysql). Siembra javayog_copy_src con tipos
 * dificiles, claves foraneas, una tabla sin clave, una vista sobre otra vista, rutinas, trigger y evento; copia y
 * compara con CHECKSUM TABLE (origen y destino estan en el mismo servidor). Con JAVAYOG_TEST_HTTP repite la copia
 * por el tunel HTTP, como origen y como destino.
 */
final class DbCopyIntegrationTest extends AbstractTestCase {

    private static final String SOURCE = "javayog_copy_src";
    private static final String QUERY_SESSION = "ctab-copy-it:query-1";
    private static final List<String> TABLES = List.of("customers", "no_pk", "orders");
    private static final List<String> TARGETS = List.of(
        "javayog_copy_dst", "javayog_copy_dst2", "javayog_copy_trg", "javayog_copy_tun", "javayog_copy_tun2", "javayog_copy_prod"
    );

    private static String connectionUuid;
    private static String productionConnectionUuid;
    private static String tunnelConnectionUuid;

    @BeforeAll
    static void seedDatabase() throws SQLException {
        String testMysql = System.getenv("JAVAYOG_TEST_MYSQL");
        Assumptions.assumeTrue(testMysql != null && !testMysql.isBlank(), "JAVAYOG_TEST_MYSQL not set: copy integration tests skipped");
        String[] mysql = testMysql.split(":", 4);

        bootFreshDatabase("DbCopyIntegrationTest");
        connectionUuid = createConnection(Primitives.of(
            "connectionName", "copy-it", "host", mysql[0], "port", mysql[1], "userName", mysql[2], "userPassword", mysql[3]
        ));
        productionConnectionUuid = createConnection(Primitives.of(
            "connectionName", "copy-it-prod", "host", mysql[0], "port", mysql[1], "userName", mysql[2], "userPassword", mysql[3],
            "isProduction", true
        ));
        String testHttp = System.getenv("JAVAYOG_TEST_HTTP");
        if (testHttp != null && !testHttp.isBlank()) {
            String[] http = testHttp.split("\\|", 3);
            tunnelConnectionUuid = createConnection(Primitives.of(
                "connectionName", "copy-it-tunnel", "host", http[1], "port", http[2], "userName", mysql[2], "userPassword", mysql[3],
                "httpEnabled", true, "httpUrl", http[0], "httpTimeoutS", 30
            ));
        }

        StringBuilder seedSql = new StringBuilder("""
            SET SESSION time_zone = '+00:00';
            SET SESSION sql_mode = CONCAT(@@sql_mode, ',NO_AUTO_VALUE_ON_ZERO');
            DROP DATABASE IF EXISTS javayog_copy_src;
            CREATE DATABASE javayog_copy_src DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
            USE javayog_copy_src;
            CREATE TABLE customers (
                id INT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
                email VARCHAR(100) NOT NULL UNIQUE,
                name VARCHAR(100) NULL,
                prefs JSON NULL,
                avatar BLOB NULL,
                uid BINARY(4) NULL,
                active BIT(1) NOT NULL DEFAULT b'1',
                flags BIT(10) NULL,
                born DATE NULL,
                seen DATETIME(3) NULL,
                stamp TIMESTAMP NULL,
                at_time TIME NULL,
                year_of YEAR NULL,
                amount DECIMAL(12,4) NULL,
                ratio DOUBLE NULL,
                size ENUM('s','m','l') NULL,
                tags SET('a','b','c') NULL,
                email_domain VARCHAR(100) GENERATED ALWAYS AS (SUBSTRING_INDEX(email, '@', -1)) VIRTUAL,
                KEY ix_name (name)
            ) COMMENT 'clientes; con punto y coma';
            CREATE TABLE orders (
                id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                customer_id INT UNSIGNED NOT NULL,
                total DECIMAL(10,2) NOT NULL,
                CONSTRAINT fk_orders_customer FOREIGN KEY (customer_id) REFERENCES customers (id)
            );
            CREATE TABLE no_pk (n INT NOT NULL, label VARCHAR(20) NULL);
            INSERT INTO customers (id, email, name, prefs, avatar, uid, active, flags, born, seen, stamp, at_time, year_of, amount, ratio, size, tags) VALUES
                (1, 'ana@x.es', 'O''Brien \\\\ "quoted"\\nline; ünïcode 😀', '{"lang":"es","tags":[1,2]}', REPEAT('a', 100), 0xCAFEBABE, b'1', b'1010101010',
                 '1999-12-31', '2024-02-29 23:59:59.123', '2024-06-01 10:00:00', '-12:30:00', 2024, 12345678.1234, 0.1, 'm', 'a,c'),
                (2, 'bob@y.es', NULL, NULL, NULL, 0x00000000, b'0', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL),
                (0, 'zero@z.es', '', '[]', '', NULL, b'1', b'0', NULL, NULL, NULL, '00:00:00', 1901, -0.0001, -1.5e-10, 's', '');
            """);
        // filas de sobra para varias paginas (1000 por pagina): por clave (orders) y sin clave (no_pk)
        seedSql.append("INSERT INTO orders (customer_id, total) SELECT 1 + (seq % 2), seq / 100 FROM (")
            .append(getSequenceSql(2500)).append(") s;\n");
        seedSql.append("INSERT INTO no_pk (n, label) SELECT seq, CONCAT('fila ', seq) FROM (").append(getSequenceSql(2500)).append(") s;\n");
        seedSql.append("""
            CREATE VIEW v_active AS SELECT id, email FROM customers WHERE active = 1;
            CREATE VIEW a_view_of_view AS SELECT COUNT(*) AS n FROM v_active;
            DELIMITER $$
            CREATE PROCEDURE count_customers()
            BEGIN
                SELECT COUNT(*) AS total FROM customers;
                SELECT 'a;b' AS literal;
            END$$
            CREATE FUNCTION plus_one(n INT) RETURNS INT DETERMINISTIC
            BEGIN
                RETURN n + 1;
            END$$
            CREATE TRIGGER tr_email BEFORE INSERT ON customers FOR EACH ROW
            BEGIN
                SET NEW.email = LOWER(NEW.email);
            END$$
            CREATE EVENT ev_cleanup ON SCHEDULE EVERY 1 DAY DISABLE DO
            BEGIN
                DELETE FROM no_pk WHERE n < 0;
            END$$
            DELIMITER ;
            """);
        assertNoErrors(execute(seedSql.toString()));
    }

    @AfterAll
    static void dropDatabases() throws SQLException {
        if (connectionUuid == null) return;
        StringBuilder dropSql = new StringBuilder("DROP DATABASE IF EXISTS " + SOURCE + ";\n");
        for (String target : TARGETS) dropSql.append("DROP DATABASE IF EXISTS ").append(target).append(";\n");
        execute(dropSql.toString());
        TargetSessionFactory.getInstance().closeAll();
    }

    @Test
    void lists_every_kind_of_object_with_the_table_of_each_trigger() throws SQLException {
        ListCopyObjectsResultDto result = ListCopyObjectsService.getInstance().invoke(ListCopyObjectsDto.fromPrimitives(Primitives.of(
            "connectionUuid", connectionUuid, "sessionKey", "ctab-copy-it:copy-list", "databaseName", SOURCE
        )));

        assertEquals(TABLES, result.getNames(DbCopyObjectTypeEnum.TABLE));
        assertEquals(List.of("a_view_of_view", "v_active"), result.getNames(DbCopyObjectTypeEnum.VIEW));
        assertEquals(List.of("count_customers"), result.getNames(DbCopyObjectTypeEnum.PROCEDURE));
        assertEquals(List.of("plus_one"), result.getNames(DbCopyObjectTypeEnum.FUNCTION));
        assertEquals(List.of("tr_email"), result.getNames(DbCopyObjectTypeEnum.TRIGGER));
        assertEquals(List.of("ev_cleanup"), result.getNames(DbCopyObjectTypeEnum.EVENT));
        assertEquals("customers", result.getTriggerTables().get("tr_email"));
    }

    @Test
    void copies_structure_and_data_of_everything_to_a_new_database() throws SQLException {
        List<String> startedObjects = new ArrayList<>();
        CopyDatabaseResultDto result = copy(connectionUuid, connectionUuid, "javayog_copy_dst", getAllObjects(), Primitives.of(
            "isDropIfExists", true, "isBulkInsert", true, "isIgnoreDefiner", true
        ), new DbCopyProgressListener() {
            @Override
            public void onObjectStarted(String objectType, String objectName, int objectIndex, int objectCount) {
                startedObjects.add(objectType + ":" + objectName);
            }
        });

        assertAllOk(result);
        assertEquals(9, result.getObjects().size());
        assertEquals(3L + 2500 + 2500, result.getCopiedRows());
        // tablas primero, el trigger despues de los datos
        assertEquals("table:customers", startedObjects.getFirst());
        assertTrue(startedObjects.indexOf("trigger:tr_email") > startedObjects.indexOf("table:orders"));
        assertSameData("javayog_copy_dst");
        assertObjectsExist("javayog_copy_dst");
        // la vista copiada apunta a la BD destino, no al origen
        assertEquals("2", getFirstCell("SELECT COUNT(*) FROM javayog_copy_dst.v_active"));
        assertEquals("0", getFirstCell("SELECT COUNT(*) FROM information_schema.VIEWS WHERE TABLE_SCHEMA = 'javayog_copy_dst' AND VIEW_DEFINITION LIKE '%" + SOURCE + "%'"));
        assertEquals("2", getFirstCell("SELECT n FROM javayog_copy_dst.a_view_of_view"));
        assertEquals("8", getFirstCell("SELECT javayog_copy_dst.plus_one(7)"));
        // la columna generada se recalcula en destino
        assertEquals("x.es", getFirstCell("SELECT email_domain FROM javayog_copy_dst.customers WHERE id = 1"));

        // otra vez encima, borrando lo que hay: mismo resultado
        CopyDatabaseResultDto again = copy(connectionUuid, connectionUuid, "javayog_copy_dst", getAllObjects(), Primitives.of(
            "isDropIfExists", true, "isBulkInsert", false
        ), DbCopyProgressListener.NONE);
        assertAllOk(again);
        assertSameData("javayog_copy_dst");
    }

    @Test
    void structure_only_and_without_drop_keeps_what_is_there() throws SQLException {
        CopyDatabaseResultDto result = copy(connectionUuid, connectionUuid, "javayog_copy_dst2", getAllObjects(), Primitives.of(
            "isStructureOnly", true, "isDropIfExists", true
        ), DbCopyProgressListener.NONE);

        assertAllOk(result);
        assertEquals(0L, result.getCopiedRows());
        assertEquals("0", getFirstCell("SELECT COUNT(*) FROM javayog_copy_dst2.orders"));
        assertObjectsExist("javayog_copy_dst2");

        // sin "Drop if exists": la tabla se queda y los datos se añaden; la vista ya existe y falla, sin parar la copia
        CopyDatabaseResultDto withoutDrop = copy(connectionUuid, connectionUuid, "javayog_copy_dst2", Primitives.of(
            DbCopyObjectTypeEnum.TABLE, List.of("no_pk"), DbCopyObjectTypeEnum.VIEW, List.of("v_active"), DbCopyObjectTypeEnum.FUNCTION, List.of("plus_one")
        ), Primitives.of("isDropIfExists", false, "isBulkInsert", true), DbCopyProgressListener.NONE);
        assertEquals(2500L, withoutDrop.getCopiedRows());
        assertEquals(2, withoutDrop.getErrorCount());
        assertEquals("2500", getFirstCell("SELECT COUNT(*) FROM javayog_copy_dst2.no_pk"));
    }

    @Test
    void copies_the_triggers_of_the_marked_tables_only_when_asked() throws SQLException {
        CopyDatabaseResultDto result = copy(connectionUuid, connectionUuid, "javayog_copy_trg", Primitives.of(
            DbCopyObjectTypeEnum.TABLE, List.of("customers")
        ), Primitives.of("isDropIfExists", true, "isCopyAssociatedTriggers", true), DbCopyProgressListener.NONE);

        assertAllOk(result);
        assertEquals(List.of("customers", "tr_email"), result.getObjects().stream().map(object -> object.get(DbCopyKeyEnum.OBJECT_NAME)).toList());
        assertEquals("1", getFirstCell("SELECT COUNT(*) FROM information_schema.TRIGGERS WHERE TRIGGER_SCHEMA = 'javayog_copy_trg'"));
    }

    @Test
    void a_production_target_asks_for_confirmation_and_the_same_database_is_refused() throws SQLException {
        DbCopyException productionException = assertThrows(DbCopyException.class, () -> copy(
            connectionUuid, productionConnectionUuid, "javayog_copy_prod", Primitives.of(DbCopyObjectTypeEnum.TABLE, List.of("no_pk")),
            Primitives.of(), DbCopyProgressListener.NONE
        ));
        assertEquals(ExceptionEnum.CODE_PRECONDITION_REQUIRED, productionException.getCode());
        assertEquals("0", getFirstCell("SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME = 'javayog_copy_prod'"));

        CopyDatabaseResultDto confirmed = copy(
            connectionUuid, productionConnectionUuid, "javayog_copy_prod", Primitives.of(DbCopyObjectTypeEnum.TABLE, List.of("no_pk")),
            Primitives.of("isConfirmed", true, "isDropIfExists", true), DbCopyProgressListener.NONE
        );
        assertAllOk(confirmed);

        DbCopyException sameException = assertThrows(DbCopyException.class, () -> copy(
            connectionUuid, connectionUuid, SOURCE, getAllObjects(), Primitives.of(), DbCopyProgressListener.NONE
        ));
        assertEquals(ExceptionEnum.CODE_BAD_REQUEST, sameException.getCode());
    }

    @Test
    void another_connection_to_the_same_server_cannot_copy_a_database_onto_itself() throws SQLException {
        // otra conexion guardada (aqui la de produccion) que llega al mismo MySQL: con "Drop if exists" borraria el origen
        DbCopyException twinException = assertThrows(DbCopyException.class, () -> copy(
            connectionUuid, productionConnectionUuid, SOURCE, getAllObjects(), Primitives.of("isConfirmed", true, "isDropIfExists", true),
            DbCopyProgressListener.NONE
        ));
        assertEquals(ExceptionEnum.CODE_BAD_REQUEST, twinException.getCode());
        if (tunnelConnectionUuid != null) {
            DbCopyException tunnelException = assertThrows(DbCopyException.class, () -> copy(
                tunnelConnectionUuid, connectionUuid, SOURCE, getAllObjects(), Primitives.of("isDropIfExists", true), DbCopyProgressListener.NONE
            ));
            assertEquals(ExceptionEnum.CODE_BAD_REQUEST, tunnelException.getCode());
        }
        assertEquals("2500", getFirstCell("SELECT COUNT(*) FROM " + SOURCE + ".orders"));
    }

    @Test
    void cancelling_stops_between_pages() throws SQLException {
        CopyDatabaseResultDto result = copy(connectionUuid, connectionUuid, "javayog_copy_dst2", Primitives.of(
            DbCopyObjectTypeEnum.TABLE, List.of("orders", "no_pk")
        ), Primitives.of("isDropIfExists", true, "isBulkInsert", true), new DbCopyProgressListener() {
            private long copiedRows;

            @Override
            public void onRowsCopied(String tableName, long copiedRows, long estimatedRows) {
                this.copiedRows = copiedRows;
            }

            @Override
            public boolean isCancelled() {
                return copiedRows >= 1000;
            }
        });

        assertTrue(result.isCancelled());
        assertEquals(1, result.getObjects().size());
        assertEquals(1000L, result.getCopiedRows());
    }

    @Test
    void copies_through_the_http_tunnel_as_source_and_as_target() throws SQLException {
        Assumptions.assumeTrue(tunnelConnectionUuid != null, "JAVAYOG_TEST_HTTP not set: tunnel copy skipped");

        CopyDatabaseResultDto toTunnel = copy(connectionUuid, tunnelConnectionUuid, "javayog_copy_tun", getAllObjects(), Primitives.of(
            "isDropIfExists", true, "isBulkInsert", true, "isIgnoreDefiner", true
        ), DbCopyProgressListener.NONE);
        assertAllOk(toTunnel);
        assertSameData("javayog_copy_tun");
        assertObjectsExist("javayog_copy_tun");

        CopyDatabaseResultDto fromTunnel = copy(tunnelConnectionUuid, connectionUuid, "javayog_copy_tun2", getAllObjects(), Primitives.of(
            "isDropIfExists", true, "isBulkInsert", false, "isIgnoreDefiner", true
        ), DbCopyProgressListener.NONE);
        assertAllOk(fromTunnel);
        assertSameData("javayog_copy_tun2");
        assertObjectsExist("javayog_copy_tun2");
    }

    private static Map<String, Object> getAllObjects() {
        return Primitives.of(
            DbCopyObjectTypeEnum.TABLE, TABLES,
            DbCopyObjectTypeEnum.VIEW, List.of("a_view_of_view", "v_active"),
            DbCopyObjectTypeEnum.PROCEDURE, List.of("count_customers"),
            DbCopyObjectTypeEnum.FUNCTION, List.of("plus_one"),
            DbCopyObjectTypeEnum.TRIGGER, List.of("tr_email"),
            DbCopyObjectTypeEnum.EVENT, List.of("ev_cleanup")
        );
    }

    private static CopyDatabaseResultDto copy(
        String sourceUuid, String targetUuid, String targetDatabase, Map<String, Object> objects, Map<String, Object> options,
        DbCopyProgressListener dbCopyProgressListener
    ) throws SQLException {
        // como la UI: el listener se registra por id de copia y el id va en el DTO
        String copyId = "copy-it-" + System.nanoTime();
        DbCopyProgressTracker dbCopyProgressTracker = DbCopyProgressTracker.getInstance();
        dbCopyProgressTracker.register(copyId, dbCopyProgressListener);
        try {
            return CopyDatabaseService.getInstance().invoke(CopyDatabaseDto.fromPrimitives(Primitives.merge(Primitives.of(
                "copyId", copyId,
                "sourceConnectionUuid", sourceUuid,
                "sourceDatabase", SOURCE,
                "targetConnectionUuid", targetUuid,
                "targetDatabase", targetDatabase,
                "objects", objects
            ), options)));
        }
        finally {
            dbCopyProgressTracker.unregister(copyId);
        }
    }

    private void assertAllOk(CopyDatabaseResultDto result) {
        for (Map<String, Object> object : result.getObjects()) {
            assertTrue(Boolean.TRUE.equals(object.get(DbCopyKeyEnum.IS_OK)), String.valueOf(object));
        }
        assertFalse(result.isCancelled());
    }

    private void assertTrue(boolean condition, String message) {
        org.junit.jupiter.api.Assertions.assertTrue(condition, message);
    }

    private void assertSameData(String targetDatabase) {
        for (String table : TABLES) {
            String sourceChecksum = getChecksum(SOURCE + "." + table);
            String targetChecksum = getChecksum(targetDatabase + "." + table);
            org.junit.jupiter.api.Assertions.assertEquals(sourceChecksum, targetChecksum, table + " in " + targetDatabase);
        }
        assertEquals("0", getFirstCell("SELECT id FROM " + targetDatabase + ".customers WHERE email = 'zero@z.es'"));
        assertEquals(
            getFirstCell("SELECT CONCAT_WS('|', name, HEX(uid), active + 0, flags + 0, seen, stamp, at_time, year_of, amount, ratio) FROM " + SOURCE + ".customers WHERE id = 1"),
            getFirstCell("SELECT CONCAT_WS('|', name, HEX(uid), active + 0, flags + 0, seen, stamp, at_time, year_of, amount, ratio) FROM " + targetDatabase + ".customers WHERE id = 1")
        );
    }

    private void assertObjectsExist(String targetDatabase) {
        String literal = "'" + targetDatabase + "'";
        assertEquals("2", getFirstCell("SELECT COUNT(*) FROM information_schema.VIEWS WHERE TABLE_SCHEMA = " + literal));
        assertEquals("2", getFirstCell("SELECT COUNT(*) FROM information_schema.ROUTINES WHERE ROUTINE_SCHEMA = " + literal));
        assertEquals("1", getFirstCell("SELECT COUNT(*) FROM information_schema.TRIGGERS WHERE TRIGGER_SCHEMA = " + literal));
        assertEquals("1", getFirstCell("SELECT COUNT(*) FROM information_schema.EVENTS WHERE EVENT_SCHEMA = " + literal));
        assertEquals("1", getFirstCell("SELECT COUNT(*) FROM information_schema.REFERENTIAL_CONSTRAINTS WHERE CONSTRAINT_SCHEMA = " + literal));
        assertEquals("clientes; con punto y coma", getFirstCell(
            "SELECT TABLE_COMMENT FROM information_schema.TABLES WHERE TABLE_SCHEMA = " + literal + " AND TABLE_NAME = 'customers'"
        ));
    }

    private static String getChecksum(String qualifiedTable) {
        ExecuteQueryResultDto result = execute("CHECKSUM TABLE " + qualifiedTable + " EXTENDED");
        @SuppressWarnings("unchecked")
        List<List<Object>> rows = (List<List<Object>>) result.getResults().getFirst().get(QueryKeyEnum.ROWS);
        return String.valueOf(rows.getFirst().get(1));
    }

    @SuppressWarnings("unchecked")
    private static String getFirstCell(String sql) {
        ExecuteQueryResultDto result = execute(sql);
        assertNoErrors(result);
        List<List<Object>> rows = (List<List<Object>>) result.getResults().getFirst().get(QueryKeyEnum.ROWS);
        return rows.isEmpty() ? null : String.valueOf(rows.getFirst().getFirst());
    }

    private static String getSequenceSql(int count) {
        return "SELECT a.n + b.n * 10 + c.n * 100 + d.n * 1000 + 1 AS seq FROM "
            + "(SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) a, "
            + "(SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) b, "
            + "(SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) c, "
            + "(SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2) d "
            + "WHERE a.n + b.n * 10 + c.n * 100 + d.n * 1000 < " + count;
    }

    private static void assertNoErrors(ExecuteQueryResultDto result) {
        for (Map<String, Object> statementResult : result.getResults()) {
            org.junit.jupiter.api.Assertions.assertNull(statementResult.get(QueryKeyEnum.ERROR_MESSAGE), String.valueOf(statementResult.get(QueryKeyEnum.ERROR_MESSAGE)));
        }
    }

    private static ExecuteQueryResultDto execute(String sql) {
        return ExecuteQueryService.getInstance().invoke(ExecuteQueryDto.fromPrimitives(Primitives.of(
            "connectionUuid", connectionUuid,
            "sessionKey", QUERY_SESSION,
            "databaseName", "",
            "sqlQuery", sql,
            "maxRows", 0,
            "isConfirmed", true
        )));
    }

    private static String createConnection(Map<String, Object> form) {
        Map<String, Object> connection = CreateConnectionService.getInstance().invoke(CreateConnectionDto.fromPrimitives(form)).getConnection();
        return String.valueOf(connection.get(ConnectionsKeyEnum.CONNECTION_UUID));
    }
}
