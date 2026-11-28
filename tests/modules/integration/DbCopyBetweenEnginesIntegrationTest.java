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
import modules.dbcopy.application.copydatabase.CopyDatabaseDto;
import modules.dbcopy.application.copydatabase.CopyDatabaseResultDto;
import modules.dbcopy.application.listcopyobjects.ListCopyObjectsDto;
import modules.dbcopy.application.listcopyobjects.ListCopyObjectsResultDto;
import modules.dbcopy.application.copydatabase.CopyDatabaseService;
import modules.dbcopy.application.listcopyobjects.ListCopyObjectsService;

/**
 * Feature 29 entre motores (make test-mysql): una BD MySQL con tipos dificiles se copia a PostgreSQL (esquema), a SQL
 * Server (BD) y a SQLite (main), y de PostgreSQL vuelve a MySQL. Comprueba filas, valores de cada tipo, que la clave
 * autoincremental sigue funcionando en el destino y que las vistas salen como no copiadas.
 */
final class DbCopyBetweenEnginesIntegrationTest extends AbstractTestCase {

    private static final String SOURCE = "javayog_xcopy_src";
    private static final String PG_SCHEMA = "jy_xcopy";
    private static final String MSSQL_DATABASE = "jy_xcopy";
    private static final String BACK_TO_MYSQL = "javayog_xcopy_back";
    private static final String SESSION = "ctab-xcopy-it:query-1";

    private static String mysqlUuid;
    private static String pgsqlUuid;
    private static String mssqlUuid;
    private static String sqliteUuid;

    @BeforeAll
    static void seed() throws Exception {
        String testMysql = System.getenv("JAVAYOG_TEST_MYSQL");
        Assumptions.assumeTrue(testMysql != null && !testMysql.isBlank(), "JAVAYOG_TEST_MYSQL not set: copy between engines skipped");
        String[] mysql = testMysql.split(":", 4);
        bootFreshDatabase("DbCopyBetweenEnginesIntegrationTest");
        mysqlUuid = createConnection(Primitives.of(
            "connectionName", "xcopy-my", "host", mysql[0], "port", mysql[1], "userName", mysql[2], "userPassword", mysql[3]
        ));
        String testPgsql = System.getenv("JAVAYOG_TEST_PGSQL");
        if (testPgsql != null && !testPgsql.isBlank()) {
            String[] pgsql = testPgsql.split(":", 4);
            pgsqlUuid = createConnection(Primitives.of(
                "connectionName", "xcopy-pg", "dbEngine", "pgsql", "host", pgsql[0], "port", pgsql[1], "userName", pgsql[2], "userPassword", pgsql[3]
            ));
            execute(pgsqlUuid, "DROP SCHEMA IF EXISTS " + PG_SCHEMA + " CASCADE");
        }
        String testMssql = System.getenv("JAVAYOG_TEST_MSSQL");
        if (testMssql != null && !testMssql.isBlank()) {
            String[] mssql = testMssql.split(":", 4);
            mssqlUuid = createConnection(Primitives.of(
                "connectionName", "xcopy-ms", "dbEngine", "mssql", "host", mssql[0], "port", mssql[1], "userName", mssql[2], "userPassword", mssql[3]
            ));
            execute(mssqlUuid, "IF DB_ID('" + MSSQL_DATABASE + "') IS NOT NULL BEGIN ALTER DATABASE " + MSSQL_DATABASE
                + " SET SINGLE_USER WITH ROLLBACK IMMEDIATE; DROP DATABASE " + MSSQL_DATABASE + "; END");
        }
        Path sqliteFile = Path.of("target", "javayog-it-xcopy.sqlite").toAbsolutePath();
        Files.deleteIfExists(sqliteFile);
        Files.createFile(sqliteFile);
        sqliteUuid = createConnection(Primitives.of("connectionName", "xcopy-lite", "dbEngine", "sqlite", "databaseName", sqliteFile.toString()));

        StringBuilder seedSql = new StringBuilder("""
            SET SESSION time_zone = '+00:00';
            DROP DATABASE IF EXISTS javayog_xcopy_src;
            DROP DATABASE IF EXISTS javayog_xcopy_back;
            CREATE DATABASE javayog_xcopy_src DEFAULT CHARACTER SET utf8mb4;
            USE javayog_xcopy_src;
            CREATE TABLE customers (
                id INT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
                email VARCHAR(100) NOT NULL UNIQUE,
                name VARCHAR(100) NULL,
                bio TEXT NULL,
                prefs JSON NULL,
                avatar BLOB NULL,
                active BIT(1) NOT NULL DEFAULT b'1',
                is_vip TINYINT(1) NOT NULL DEFAULT 0,
                born DATE NULL,
                seen DATETIME(3) NULL,
                at_time TIME NULL,
                year_of YEAR NULL,
                amount DECIMAL(12,4) NULL,
                ratio DOUBLE NULL,
                size ENUM('s','m','l') NULL,
                email_domain VARCHAR(100) GENERATED ALWAYS AS (SUBSTRING_INDEX(email, '@', -1)) VIRTUAL,
                KEY ix_name (name)
            );
            CREATE TABLE orders (
                order_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                customer_id INT UNSIGNED NOT NULL,
                total DECIMAL(10,2) NOT NULL,
                KEY ix_customer (customer_id)
            );
            CREATE TABLE no_pk (n INT NOT NULL, label VARCHAR(20) NULL);
            INSERT INTO customers (id, email, name, bio, prefs, avatar, active, is_vip, born, seen, at_time, year_of, amount, ratio, size) VALUES
                (1, 'ana@x.es', 'O''Brien \\\\ "quoted"; ünïcode 😀', 'línea 1\\nlínea 2', '{"lang":"es","tags":[1,2]}', 0xCAFEBABE, b'1', 2,
                 '1999-12-31', '2024-02-29 23:59:59.123', '08:30:15', 2024, 12345678.1234, 0.1, 'm'),
                (2, 'bob@y.es', NULL, NULL, NULL, NULL, b'0', 0, NULL, NULL, NULL, NULL, NULL, NULL, NULL),
                (7, 'eve@z.es', '', '', '[]', '', b'1', 1, '2000-01-01', '2000-01-01 00:00:00', '00:00:00', 1901, -0.0001, -1.5e-10, 's');
            CREATE VIEW v_vip AS SELECT id, email FROM customers WHERE is_vip > 0;
            """);
        seedSql.append("INSERT INTO orders (customer_id, total) SELECT 1 + (seq % 2), seq / 100 FROM (").append(getSequenceSql(2500)).append(") s;\n");
        seedSql.append("INSERT INTO no_pk (n, label) SELECT seq, CONCAT('fila ', seq) FROM (").append(getSequenceSql(1500)).append(") s;\n");
        assertNoErrors(execute(mysqlUuid, seedSql.toString()));
    }

    @AfterAll
    static void dropDatabases() {
        if (mysqlUuid == null) return;
        execute(mysqlUuid, "DROP DATABASE IF EXISTS " + SOURCE + "; DROP DATABASE IF EXISTS " + BACK_TO_MYSQL);
        if (pgsqlUuid != null) execute(pgsqlUuid, "DROP SCHEMA IF EXISTS " + PG_SCHEMA + " CASCADE");
        if (mssqlUuid != null) {
            execute(mssqlUuid, "IF DB_ID('" + MSSQL_DATABASE + "') IS NOT NULL BEGIN ALTER DATABASE " + MSSQL_DATABASE
                + " SET SINGLE_USER WITH ROLLBACK IMMEDIATE; DROP DATABASE " + MSSQL_DATABASE + "; END");
        }
        TargetSessionFactory.getInstance().closeAll();
    }

    @Test
    void mysql_to_postgresql_and_back_keeps_rows_values_and_identity() throws SQLException {
        Assumptions.assumeTrue(pgsqlUuid != null, "JAVAYOG_TEST_PGSQL not set");
        CopyDatabaseResultDto result = copy(mysqlUuid, SOURCE, pgsqlUuid, PG_SCHEMA, true);
        assertTablesOkAndViewNotCopied(result);
        assertEquals(4003L, result.getCopiedRows());

        assertEquals("2500", getFirstCell(pgsqlUuid, "SELECT COUNT(*) FROM jy_xcopy.orders"));
        assertEquals("1500", getFirstCell(pgsqlUuid, "SELECT COUNT(*) FROM jy_xcopy.no_pk"));
        assertEquals(
            "O'Brien \\ \"quoted\"; ünïcode 😀|línea 1\nlínea 2|es|cafebabe|t|2|1999-12-31|2024-02-29 23:59:59.123|08:30:15|2024|12345678.1234|0.1|m",
            getFirstCell(pgsqlUuid, """
                SELECT concat_ws('|', name, bio, prefs->>'lang', encode(avatar, 'hex'), active, is_vip, born, seen, at_time, year_of, amount, ratio, size)
                FROM jy_xcopy.customers WHERE id = 1""")
        );
        assertEquals("f|0", getFirstCell(pgsqlUuid, "SELECT concat_ws('|', active, is_vip) FROM jy_xcopy.customers WHERE id = 2"));
        // la columna calculada no viaja; el indice unico si
        assertEquals("0", getFirstCell(pgsqlUuid,
            "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = 'jy_xcopy' AND table_name = 'customers' AND column_name = 'email_domain'"));
        // el IDENTITY sigue tras el maximo copiado
        assertNoErrors(execute(pgsqlUuid, "INSERT INTO jy_xcopy.customers (email, active, is_vip) VALUES ('new@x.es', TRUE, 0)"));
        assertEquals("8", getFirstCell(pgsqlUuid, "SELECT id FROM jy_xcopy.customers WHERE email = 'new@x.es'"));
        org.junit.jupiter.api.Assertions.assertNotNull(execute(pgsqlUuid, "INSERT INTO jy_xcopy.customers (email) VALUES ('ana@x.es')").getResults().getFirst().get(QueryKeyEnum.ERROR_MESSAGE));

        // y de vuelta: PostgreSQL como origen, MySQL como destino
        ListCopyObjectsResultDto objects = ListCopyObjectsService.getInstance().invoke(ListCopyObjectsDto.fromPrimitives(Primitives.of(
            "connectionUuid", pgsqlUuid, "sessionKey", "ctab-xcopy-it:list", "databaseName", PG_SCHEMA
        )));
        assertEquals(List.of("customers", "no_pk", "orders"), objects.getNames(DbCopyObjectTypeEnum.TABLE));
        CopyDatabaseResultDto backResult = copy(pgsqlUuid, PG_SCHEMA, mysqlUuid, BACK_TO_MYSQL, false);
        assertTablesOkAndViewNotCopied(backResult);
        assertEquals(
            getFirstCell(mysqlUuid, "SELECT CONCAT_WS('|', name, bio, HEX(avatar), born, seen, TIME_FORMAT(at_time, '%H:%i:%s'), amount, ratio, size) FROM " + SOURCE + ".customers WHERE id = 1"),
            getFirstCell(mysqlUuid, "SELECT CONCAT_WS('|', name, bio, HEX(avatar), born, seen, TIME_FORMAT(at_time, '%H:%i:%s'), amount, ratio, size) FROM " + BACK_TO_MYSQL + ".customers WHERE id = 1")
        );
        assertEquals("1|1", getFirstCell(mysqlUuid, "SELECT CONCAT_WS('|', active, is_vip > 0) FROM " + BACK_TO_MYSQL + ".customers WHERE id = 1"));
        assertEquals("auto_increment", getFirstCell(mysqlUuid,
            "SELECT EXTRA FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = '" + BACK_TO_MYSQL + "' AND TABLE_NAME = 'orders' AND COLUMN_NAME = 'order_id'"));
    }

    @Test
    void mysql_to_sql_server_creates_the_database_and_keeps_the_identity() throws SQLException {
        Assumptions.assumeTrue(mssqlUuid != null, "JAVAYOG_TEST_MSSQL not set");
        CopyDatabaseResultDto result = copy(mysqlUuid, SOURCE, mssqlUuid, MSSQL_DATABASE, true);
        assertTablesOkAndViewNotCopied(result);

        assertEquals("2500", getFirstCell(mssqlUuid, "SELECT COUNT(*) FROM jy_xcopy.dbo.orders"));
        assertEquals("O'Brien \\ \"quoted\"; ünïcode 😀|1|2|2024-02-29 23:59:59.123|12345678.1234", getFirstCell(mssqlUuid, """
            SELECT CONCAT_WS('|', name, CAST(active AS int), is_vip, CONVERT(varchar(30), seen, 121), amount)
            FROM jy_xcopy.dbo.customers WHERE id = 1"""));
        assertNoErrors(execute(mssqlUuid, "INSERT INTO jy_xcopy.dbo.orders (customer_id, total) VALUES (1, 9.99)"));
        assertEquals("2501", getFirstCell(mssqlUuid, "SELECT MAX(order_id) FROM jy_xcopy.dbo.orders"));
    }

    @Test
    void mysql_to_sqlite_goes_into_main() throws SQLException {
        CopyDatabaseResultDto result = copy(mysqlUuid, SOURCE, sqliteUuid, "main", true);
        assertTablesOkAndViewNotCopied(result);

        assertEquals("2500", getFirstCell(sqliteUuid, "SELECT COUNT(*) FROM orders"));
        assertEquals("1|2|2024-02-29 23:59:59.123|cafebabe", getFirstCell(sqliteUuid,
            "SELECT active || '|' || is_vip || '|' || seen || '|' || lower(hex(avatar)) FROM customers WHERE id = 1"));
        assertNoErrors(execute(sqliteUuid, "INSERT INTO orders (customer_id, total) VALUES (1, 9.99)"));
        assertEquals("2501", getFirstCell(sqliteUuid, "SELECT MAX(order_id) FROM orders"));
    }

    private void assertTablesOkAndViewNotCopied(CopyDatabaseResultDto result) {
        for (Map<String, Object> object : result.getObjects()) {
            boolean isTable = DbCopyObjectTypeEnum.TABLE.equals(object.get(DbCopyKeyEnum.OBJECT_TYPE));
            org.junit.jupiter.api.Assertions.assertEquals(isTable, Boolean.TRUE.equals(object.get(DbCopyKeyEnum.IS_OK)), String.valueOf(object));
        }
        assertEquals(1, result.getErrorCount());
    }

    private static CopyDatabaseResultDto copy(String sourceUuid, String sourceDatabase, String targetUuid, String targetDatabase, boolean withView)
        throws SQLException {
        return CopyDatabaseService.getInstance().invoke(CopyDatabaseDto.fromPrimitives(Primitives.of(
            "sourceConnectionUuid", sourceUuid,
            "sourceDatabase", sourceDatabase,
            "targetConnectionUuid", targetUuid,
            "targetDatabase", targetDatabase,
            "objects", Primitives.of(
                DbCopyObjectTypeEnum.TABLE, List.of("customers", "no_pk", "orders"),
                DbCopyObjectTypeEnum.VIEW, withView ? List.of("v_vip") : List.of("v_missing")
            ),
            "isDropIfExists", true,
            "isBulkInsert", true
        )));
    }

    private static String createConnection(Map<String, Object> form) {
        return String.valueOf(CreateConnectionService.getInstance().invoke(CreateConnectionDto.fromPrimitives(form)).getConnection().get(ConnectionsKeyEnum.CONNECTION_UUID));
    }

    @SuppressWarnings("unchecked")
    private static String getFirstCell(String connectionUuid, String sql) {
        ExecuteQueryResultDto result = execute(connectionUuid, sql);
        assertNoErrors(result);
        List<List<Object>> rows = (List<List<Object>>) result.getResults().getFirst().get(QueryKeyEnum.ROWS);
        return rows.isEmpty() ? null : String.valueOf(rows.getFirst().getFirst());
    }

    private static void assertNoErrors(ExecuteQueryResultDto result) {
        for (Map<String, Object> statementResult : result.getResults()) {
            org.junit.jupiter.api.Assertions.assertNull(statementResult.get(QueryKeyEnum.ERROR_MESSAGE), String.valueOf(statementResult.get(QueryKeyEnum.ERROR_MESSAGE)));
        }
    }

    private static ExecuteQueryResultDto execute(String connectionUuid, String sql) {
        return ExecuteQueryService.getInstance().invoke(ExecuteQueryDto.fromPrimitives(Primitives.of(
            "connectionUuid", connectionUuid,
            "sessionKey", SESSION + "-" + connectionUuid,
            "databaseName", "",
            "sqlQuery", sql,
            "maxRows", 0,
            "isConfirmed", true
        )));
    }

    private static String getSequenceSql(int count) {
        return "SELECT a.n + b.n * 10 + c.n * 100 + d.n * 1000 + 1 AS seq FROM "
            + "(SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) a, "
            + "(SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) b, "
            + "(SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) c, "
            + "(SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2) d "
            + "WHERE a.n + b.n * 10 + c.n * 100 + d.n * 1000 < " + count;
    }
}
