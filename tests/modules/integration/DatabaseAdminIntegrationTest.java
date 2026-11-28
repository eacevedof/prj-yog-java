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

import modules.explorer.domain.entities.DatabaseOptionsEntity;
import modules.explorer.application.alterdatabase.AlterDatabaseDto;
import modules.explorer.application.getdatabaseoptions.GetDatabaseOptionsDto;
import modules.explorer.application.alterdatabase.AlterDatabaseService;
import modules.explorer.application.getdatabaseoptions.GetDatabaseOptionsService;
import modules.explorer.domain.exceptions.ExplorerException;

import modules.query.domain.enums.QueryKeyEnum;
import modules.query.application.executequery.ExecuteQueryDto;
import modules.query.application.executequery.ExecuteQueryResultDto;
import modules.query.application.executequery.ExecuteQueryService;

import modules.dbcopy.application.renamedatabase.RenameDatabaseDto;
import modules.dbcopy.application.renamedatabase.RenameDatabaseResultDto;
import modules.dbcopy.application.renamedatabase.RenameDatabaseService;
import modules.dbcopy.domain.exceptions.DbCopyException;

/**
 * Alterar BD y Renombrar BD contra un MySQL de verdad (JAVAYOG_TEST_MYSQL; make test-mysql). Cada test siembra su
 * propia BD (tablas con datos, una vista sobre otra vista, un procedimiento y un trigger) y la borra al acabar.
 */
final class DatabaseAdminIntegrationTest extends AbstractTestCase {

    private static final String QUERY_SESSION = "ctab-dbadmin-it:query-1";
    private static final String EXPLORER_SESSION = "ctab-dbadmin-it:explorer";
    private static final Path DUMP_DIRECTORY = Path.of("target", "dbadmin-it");
    private static final List<String> DATABASES = List.of(
        "javayog_alter_it", "javayog_rename_src", "javayog_rename_dst", "javayog_keep_src", "javayog_keep_copy"
    );

    private static String connectionUuid;
    private static String productionConnectionUuid;

    @BeforeAll
    static void createConnections() throws Exception {
        String testMysql = System.getenv("JAVAYOG_TEST_MYSQL");
        Assumptions.assumeTrue(testMysql != null && !testMysql.isBlank(), "JAVAYOG_TEST_MYSQL not set: database admin integration tests skipped");
        String[] mysql = testMysql.split(":", 4);
        bootFreshDatabase("DatabaseAdminIntegrationTest");
        connectionUuid = createConnection(Primitives.of(
            "connectionName", "dbadmin-it", "host", mysql[0], "port", mysql[1], "userName", mysql[2], "userPassword", mysql[3]
        ));
        productionConnectionUuid = createConnection(Primitives.of(
            "connectionName", "dbadmin-it-prod", "host", mysql[0], "port", mysql[1], "userName", mysql[2], "userPassword", mysql[3], "isProduction", true
        ));
        Files.createDirectories(DUMP_DIRECTORY);
        dropDatabases();
    }

    @AfterAll
    static void cleanUp() {
        if (connectionUuid == null) return;
        dropDatabases();
        TargetSessionFactory.getInstance().closeAll();
    }

    @Test
    void testAlterDatabaseChangesCharsetAndReadOnlyAndAsksOnProduction() throws SQLException {
        seed("javayog_alter_it");
        DatabaseOptionsEntity original = getOptions("javayog_alter_it");
        DatabaseOptionsEntity latin1ReadOnly = original.withEdited("latin1", "latin1_swedish_ci", original.isEncrypted(), true, original.comment());

        ExplorerException notConfirmed = assertThrows(ExplorerException.class, () -> alter(productionConnectionUuid, original, latin1ReadOnly, false));
        String alterSql = alter(connectionUuid, original, latin1ReadOnly, false);
        DatabaseOptionsEntity altered = getOptions("javayog_alter_it");
        // de solo lectura a escribible y de vuelta a utf8mb4 en la misma sentencia
        alter(connectionUuid, altered, altered.withEdited("utf8mb4", "utf8mb4_0900_ai_ci", altered.isEncrypted(), false, altered.comment()), false);
        DatabaseOptionsEntity restored = getOptions("javayog_alter_it");

        assertEquals(ExceptionEnum.CODE_PRECONDITION_REQUIRED, notConfirmed.getCode());
        assertEquals("ALTER DATABASE `javayog_alter_it` CHARACTER SET = latin1 COLLATE = latin1_swedish_ci READ ONLY = 1", alterSql);
        assertEquals("latin1", altered.characterSet());
        assertTrue(altered.isReadOnlySupported());
        assertTrue(altered.isReadOnly());
        assertEquals("utf8mb4", restored.characterSet());
        assertFalse(restored.isReadOnly());
    }

    @Test
    void testRenameDatabaseCopiesEverythingChecksItAndDropsTheOriginal() throws Exception {
        seed("javayog_rename_src");
        Path backupFile = DUMP_DIRECTORY.resolve("rename_src_backup.sql");

        RenameDatabaseResultDto renamed = rename("javayog_rename_src", "javayog_rename_dst", backupFile, true);

        assertEquals(List.of("0"), getRows("SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME = 'javayog_rename_src'").getFirst());
        assertEquals(List.of("3", "2"), getRows("SELECT (SELECT COUNT(*) FROM javayog_rename_dst.customers), (SELECT COUNT(*) FROM javayog_rename_dst.a_short)").getFirst());
        assertEquals(List.of("1", "1"), getRows("""
            SELECT
                (SELECT COUNT(*) FROM information_schema.ROUTINES WHERE ROUTINE_SCHEMA = 'javayog_rename_dst'),
                (SELECT COUNT(*) FROM information_schema.TRIGGERS WHERE TRIGGER_SCHEMA = 'javayog_rename_dst')
            """).getFirst());
        assertTrue(renamed.isOriginalDropped());
        assertEquals(6, renamed.getObjectCount());
        assertEquals(4L, renamed.getRowCount());
        assertTrue(Files.readString(Path.of(renamed.getFilePath())).contains("INSERT INTO `customers`"));
    }

    @Test
    void testRenameCanKeepTheOriginalAndRefusesWrongTargetsAndUnconfirmedProduction() throws Exception {
        seed("javayog_keep_src");
        Path backupFile = DUMP_DIRECTORY.resolve("keep_src_backup.sql");

        DbCopyException sameName = assertThrows(DbCopyException.class, () -> rename("javayog_keep_src", "JAVAYOG_KEEP_SRC", backupFile, false));
        DbCopyException wrongName = assertThrows(DbCopyException.class, () -> rename("javayog_keep_src", "bad name", backupFile, false));
        DbCopyException notConfirmed = assertThrows(DbCopyException.class, () -> RenameDatabaseService.getInstance().invoke(RenameDatabaseDto.fromPrimitives(Primitives.of(
            "renameId", "rename-it-prod", "connectionUuid", productionConnectionUuid, "databaseName", "javayog_keep_src",
            "newDatabaseName", "javayog_keep_copy", "filePath", backupFile.toString(), "isDropOriginal", true
        ))));
        RenameDatabaseResultDto copied = rename("javayog_keep_src", "javayog_keep_copy", backupFile, false);
        DbCopyException alreadyExists = assertThrows(DbCopyException.class, () -> rename("javayog_keep_src", "javayog_keep_copy", backupFile, false));

        assertEquals(ExceptionEnum.CODE_BAD_REQUEST, sameName.getCode());
        assertEquals(ExceptionEnum.CODE_BAD_REQUEST, wrongName.getCode());
        assertEquals(ExceptionEnum.CODE_PRECONDITION_REQUIRED, notConfirmed.getCode());
        assertTrue(notConfirmed.getMessage().contains("DROPPED"));
        assertFalse(copied.isOriginalDropped());
        assertEquals(List.of("3", "3"), getRows("SELECT (SELECT COUNT(*) FROM javayog_keep_src.customers), (SELECT COUNT(*) FROM javayog_keep_copy.customers)").getFirst());
        assertEquals(ExceptionEnum.CODE_BAD_REQUEST, alreadyExists.getCode());
    }

    private static void seed(String databaseName) {
        ExecuteQueryResultDto seedResult = execute("""
            CREATE DATABASE %1$s CHARACTER SET utf8mb4;
            USE %1$s;
            CREATE TABLE customers (id INT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(50) NOT NULL, note TEXT NULL);
            INSERT INTO customers (name, note) VALUES ('Ána', 'it''s; raro'), ('Bob', NULL), ('Carla', 'x');
            CREATE TABLE audit (id INT AUTO_INCREMENT PRIMARY KEY, txt VARCHAR(50));
            INSERT INTO audit (txt) VALUES ('seed');
            CREATE VIEW v_names AS SELECT id, name FROM customers;
            CREATE VIEW a_short AS SELECT id FROM v_names WHERE id < 3;
            DELIMITER $$
            CREATE PROCEDURE count_customers() BEGIN SELECT COUNT(*) AS total FROM customers; END$$
            CREATE TRIGGER tr_customers AFTER INSERT ON customers FOR EACH ROW BEGIN INSERT INTO audit (txt) VALUES (NEW.name); END$$
            DELIMITER ;
            """.formatted(databaseName));
        for (Map<String, Object> result : seedResult.getResults()) {
            org.junit.jupiter.api.Assertions.assertNull(result.get(QueryKeyEnum.ERROR_MESSAGE), String.valueOf(result.get(QueryKeyEnum.ERROR_MESSAGE)));
        }
    }

    private static DatabaseOptionsEntity getOptions(String databaseName) throws SQLException {
        return GetDatabaseOptionsService.getInstance().invoke(GetDatabaseOptionsDto.fromPrimitives(Primitives.of(
            "connectionUuid", connectionUuid, "sessionKey", EXPLORER_SESSION, "databaseName", databaseName
        ))).getDatabaseOptions();
    }

    private static String alter(String uuid, DatabaseOptionsEntity original, DatabaseOptionsEntity edited, boolean isConfirmed) throws SQLException {
        return AlterDatabaseService.getInstance().invoke(AlterDatabaseDto.fromPrimitives(Primitives.of(
            "connectionUuid", uuid, "sessionKey", EXPLORER_SESSION + ":" + uuid, "original", original, "edited", edited, "isConfirmed", isConfirmed
        ))).getAlterSql();
    }

    private static RenameDatabaseResultDto rename(String databaseName, String newDatabaseName, Path backupFile, boolean isDropOriginal) throws SQLException {
        return RenameDatabaseService.getInstance().invoke(RenameDatabaseDto.fromPrimitives(Primitives.of(
            "renameId", "rename-it",
            "connectionUuid", connectionUuid,
            "databaseName", databaseName,
            "newDatabaseName", newDatabaseName,
            "filePath", backupFile.toString(),
            "isDropOriginal", isDropOriginal
        )));
    }

    private static void dropDatabases() {
        for (String databaseName : DATABASES) {
            // una BD de solo lectura no se deja borrar; si no existe, el error se queda en el resultado
            execute("ALTER DATABASE " + databaseName + " READ ONLY = 0");
            execute("DROP DATABASE IF EXISTS " + databaseName);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<List<String>> getRows(String sql) {
        return (List<List<String>>) execute(sql).getResults().getFirst().get(QueryKeyEnum.ROWS);
    }

    private static String createConnection(Map<String, Object> form) {
        return String.valueOf(CreateConnectionService.getInstance().invoke(CreateConnectionDto.fromPrimitives(form))
            .getConnection().get(ConnectionsKeyEnum.CONNECTION_UUID));
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
}
