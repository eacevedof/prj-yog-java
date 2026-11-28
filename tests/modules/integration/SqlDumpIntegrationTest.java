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

import testcase.AbstractTestCase;

import modules.connections.domain.enums.ConnectionsKeyEnum;
import modules.connections.application.createconnection.CreateConnectionDto;
import modules.connections.application.createconnection.CreateConnectionService;
import modules.connections.infrastructure.factories.TargetSessionFactory;

import modules.query.domain.enums.QueryKeyEnum;
import modules.query.application.executequery.ExecuteQueryDto;
import modules.query.application.executequery.ExecuteQueryResultDto;
import modules.query.application.executesqlfile.ExecuteSqlFileDto;
import modules.query.application.executesqlfile.ExecuteSqlFileResultDto;
import modules.query.application.executequery.ExecuteQueryService;
import modules.query.application.executesqlfile.ExecuteSqlFileService;
import modules.query.domain.exceptions.QueryException;

import modules.dbcopy.domain.enums.DbCopyObjectTypeEnum;
import modules.dbcopy.domain.enums.SqlDumpModeEnum;
import modules.dbcopy.application.exportsqldump.ExportSqlDumpDto;
import modules.dbcopy.application.exportsqldump.ExportSqlDumpResultDto;
import modules.dbcopy.application.exportsqldump.ExportSqlDumpService;

/**
 * Volcado .sql y "Ejecutar fichero" contra un MySQL de verdad (JAVAYOG_TEST_MYSQL; make test-mysql): se vuelca
 * javayog_dump_src (tipos raros, NULL, binarios, una vista sobre otra vista, un procedimiento y un trigger con ; en el
 * cuerpo) y el fichero se ejecuta en javayog_dump_dst; lo de alli tiene que ser igual que el origen.
 */
final class SqlDumpIntegrationTest extends AbstractTestCase {

    private static final String SOURCE = "javayog_dump_src";
    private static final String TARGET = "javayog_dump_dst";
    private static final String QUERY_SESSION = "ctab-dump-it:query-1";
    private static final Path DUMP_DIRECTORY = Path.of("target", "dump-it");

    private static String connectionUuid;
    private static String productionConnectionUuid;

    @BeforeAll
    static void seedSource() throws Exception {
        String testMysql = System.getenv("JAVAYOG_TEST_MYSQL");
        Assumptions.assumeTrue(testMysql != null && !testMysql.isBlank(), "JAVAYOG_TEST_MYSQL not set: dump integration tests skipped");
        String[] mysql = testMysql.split(":", 4);
        bootFreshDatabase("SqlDumpIntegrationTest");
        connectionUuid = createConnection(Primitives.of(
            "connectionName", "dump-it", "host", mysql[0], "port", mysql[1], "userName", mysql[2], "userPassword", mysql[3]
        ));
        productionConnectionUuid = createConnection(Primitives.of(
            "connectionName", "dump-it-prod", "host", mysql[0], "port", mysql[1], "userName", mysql[2], "userPassword", mysql[3], "isProduction", true
        ));
        Files.createDirectories(DUMP_DIRECTORY);
        ExecuteQueryResultDto seedResult = execute("", """
            DROP DATABASE IF EXISTS javayog_dump_src;
            DROP DATABASE IF EXISTS javayog_dump_dst;
            CREATE DATABASE javayog_dump_src CHARACTER SET utf8mb4;
            CREATE DATABASE javayog_dump_dst CHARACTER SET utf8mb4;
            USE javayog_dump_src;
            CREATE TABLE customers (
                id INT AUTO_INCREMENT PRIMARY KEY,
                name VARCHAR(50) NOT NULL,
                note TEXT NULL,
                avatar VARBINARY(8) NULL,
                flags BIT(3) NULL,
                amount DECIMAL(10,2) NULL,
                created_at DATETIME NULL
            );
            INSERT INTO customers (name, note, avatar, flags, amount, created_at) VALUES
                ('Ána', 'it''s; \\\\ "raro"', X'CAFE00', b'101', 10.50, '2026-01-02 03:04:05'),
                ('Bob', NULL, NULL, NULL, NULL, NULL),
                ('Carla', 'línea\\nnueva', '', b'0', 0, '2026-11-26 00:00:00');
            CREATE TABLE audit (id INT AUTO_INCREMENT PRIMARY KEY, txt VARCHAR(50));
            CREATE VIEW v_names AS SELECT id, name FROM customers;
            CREATE VIEW a_short AS SELECT id FROM v_names WHERE id < 3;
            DELIMITER $$
            CREATE PROCEDURE count_customers() BEGIN SELECT COUNT(*) AS total FROM customers; END$$
            CREATE TRIGGER tr_customers AFTER INSERT ON customers FOR EACH ROW BEGIN INSERT INTO audit (txt) VALUES (NEW.name); END$$
            DELIMITER ;
            """);
        for (Map<String, Object> result : seedResult.getResults()) {
            org.junit.jupiter.api.Assertions.assertNull(result.get(QueryKeyEnum.ERROR_MESSAGE), String.valueOf(result.get(QueryKeyEnum.ERROR_MESSAGE)));
        }
    }

    @AfterAll
    static void dropDatabases() {
        if (connectionUuid == null) return;
        execute("", "DROP DATABASE IF EXISTS javayog_dump_src; DROP DATABASE IF EXISTS javayog_dump_dst");
        TargetSessionFactory.getInstance().closeAll();
    }

    @Test
    @SuppressWarnings("unchecked")
    void testDumpAndExecuteFileRoundTrip() throws Exception {
        Path dumpFile = DUMP_DIRECTORY.resolve("roundtrip.sql");
        ExportSqlDumpResultDto dumpResult = ExportSqlDumpService.getInstance().invoke(ExportSqlDumpDto.fromPrimitives(Primitives.of(
            "dumpId", "dump-it-1",
            "connectionUuid", connectionUuid,
            "databaseName", SOURCE,
            "filePath", dumpFile.toString(),
            "dumpMode", SqlDumpModeEnum.STRUCTURE_AND_DATA,
            "objects", Map.of(
                DbCopyObjectTypeEnum.TABLE, List.of("audit", "customers"),
                DbCopyObjectTypeEnum.VIEW, List.of("a_short", "v_names"),
                DbCopyObjectTypeEnum.PROCEDURE, List.of("count_customers"),
                DbCopyObjectTypeEnum.TRIGGER, List.of("tr_customers")
            ),
            "isSingleTransaction", true,
            "isForeignKeyChecksOff", true,
            "isDropIncluded", true,
            "isIgnoreDefiner", true,
            "isBulkInsert", true,
            "isVersionIncluded", true
        )));
        String dump = Files.readString(dumpFile);

        ExecuteSqlFileResultDto executeResult = ExecuteSqlFileService.getInstance().invoke(ExecuteSqlFileDto.fromPrimitives(Primitives.of(
            "connectionUuid", connectionUuid,
            "sessionKey", "ctab-dump-it:file-1",
            "databaseName", TARGET,
            "filePath", dumpFile.toString(),
            "isAbortOnError", true
        )));
        String compareSql = "SELECT id, name, note, HEX(avatar), flags + 0, amount, created_at FROM %s.customers ORDER BY id";
        List<List<String>> sourceRows = getRows(compareSql.formatted(SOURCE));
        List<List<String>> targetRows = getRows(compareSql.formatted(TARGET));
        List<List<String>> shortView = getRows("SELECT id FROM javayog_dump_dst.a_short ORDER BY id");
        List<List<String>> procedureRows = getRows("CALL javayog_dump_dst.count_customers()");
        execute(TARGET, "INSERT INTO customers (name) VALUES ('Dani')");
        List<List<String>> auditRows = getRows("SELECT txt FROM javayog_dump_dst.audit");

        assertEquals(6, dumpResult.getOkCount());
        assertEquals(0, dumpResult.getErrorCount());
        assertEquals(3L, dumpResult.getDumpedRows());
        assertTrue(dump.contains("DELIMITER ;;"));
        assertFalse(dump.contains("DEFINER="));
        // la vista que depende de otra va despues de ella aunque se marque antes
        assertTrue(dump.indexOf("CREATE ALGORITHM") < dump.lastIndexOf("a_short"));
        assertTrue(dump.indexOf("`v_names` AS") < dump.indexOf("`a_short` AS"));
        assertEquals(0L, executeResult.getErrorCount());
        assertFalse(executeResult.isAborted());
        assertEquals(sourceRows, targetRows);
        assertEquals(List.of(List.of("1"), List.of("2")), shortView);
        assertEquals(List.of(List.of("3")), procedureRows);
        assertEquals(List.of(List.of("Dani")), auditRows);
    }

    @Test
    void testStructureOnlyAndExecuteFileStopsOrGoesOnAfterErrors() throws Exception {
        Path structureFile = DUMP_DIRECTORY.resolve("structure.sql");
        ExportSqlDumpService.getInstance().invoke(ExportSqlDumpDto.fromPrimitives(Primitives.of(
            "dumpId", "dump-it-2", "connectionUuid", connectionUuid, "databaseName", SOURCE, "filePath", structureFile.toString(),
            "dumpMode", SqlDumpModeEnum.STRUCTURE_ONLY, "objects", Map.of(DbCopyObjectTypeEnum.TABLE, List.of("customers")),
            "isCreateDatabaseIncluded", true
        )));
        Path brokenFile = DUMP_DIRECTORY.resolve("broken.sql");
        Files.writeString(brokenFile, "CREATE TABLE ok_1 (id INT);\nSELECT * FROM nope;\nCREATE TABLE ok_2 (id INT);\n");
        ExecuteSqlFileResultDto abortedResult = executeFile(brokenFile, true);
        execute(TARGET, "DROP TABLE IF EXISTS ok_1");
        ExecuteSqlFileResultDto goOnResult = executeFile(brokenFile, false);
        QueryException productionException = assertThrows(QueryException.class, () -> ExecuteSqlFileService.getInstance().invoke(
            ExecuteSqlFileDto.fromPrimitives(Primitives.of(
                "connectionUuid", productionConnectionUuid, "sessionKey", "ctab-dump-it:file-prod", "databaseName", TARGET, "filePath", brokenFile.toString()
            ))
        ));

        String structure = Files.readString(structureFile);
        assertTrue(structure.contains("CREATE DATABASE IF NOT EXISTS `javayog_dump_src`"));
        assertTrue(structure.contains("USE `javayog_dump_src`;"));
        assertFalse(structure.contains("INSERT INTO"));
        assertTrue(structure.contains("CREATE TABLE IF NOT EXISTS `customers`"));
        assertTrue(abortedResult.isAborted());
        assertEquals(1L, abortedResult.getExecutedCount());
        assertEquals(1L, abortedResult.getErrorCount());
        assertFalse(goOnResult.isAborted());
        assertEquals(2L, goOnResult.getExecutedCount());
        assertTrue(String.valueOf(goOnResult.getErrors().getFirst().get("message")).contains("nope"));
        assertEquals(ExceptionEnum.CODE_PRECONDITION_REQUIRED, productionException.getCode());
    }

    private static ExecuteSqlFileResultDto executeFile(Path filePath, boolean isAbortOnError) throws SQLException {
        return ExecuteSqlFileService.getInstance().invoke(ExecuteSqlFileDto.fromPrimitives(Primitives.of(
            "connectionUuid", connectionUuid, "sessionKey", "ctab-dump-it:file-2", "databaseName", TARGET,
            "filePath", filePath.toString(), "isAbortOnError", isAbortOnError
        )));
    }

    @SuppressWarnings("unchecked")
    private static List<List<String>> getRows(String sql) {
        return (List<List<String>>) execute("", sql).getResults().getFirst().get(QueryKeyEnum.ROWS);
    }

    private static String createConnection(Map<String, Object> form) {
        return String.valueOf(CreateConnectionService.getInstance().invoke(CreateConnectionDto.fromPrimitives(form))
            .getConnection().get(ConnectionsKeyEnum.CONNECTION_UUID));
    }

    private static ExecuteQueryResultDto execute(String databaseName, String sql) {
        return ExecuteQueryService.getInstance().invoke(ExecuteQueryDto.fromPrimitives(Primitives.of(
            "connectionUuid", connectionUuid,
            "sessionKey", QUERY_SESSION,
            "databaseName", databaseName,
            "sqlQuery", sql,
            "maxRows", 0,
            "isConfirmed", true
        )));
    }
}
