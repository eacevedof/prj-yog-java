package modules.integration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import modules.shared.application.Primitives;

import testcase.AbstractTestCase;

import modules.connections.domain.enums.ConnectionsKeyEnum;
import modules.connections.application.createconnection.CreateConnectionDto;
import modules.connections.application.testconnection.TestConnectionDto;
import modules.connections.application.createconnection.CreateConnectionService;
import modules.connections.application.testconnection.TestConnectionService;
import modules.connections.infrastructure.factories.TargetSessionFactory;

import modules.files.application.savefilesfolder.SaveFilesFolderDto;
import modules.files.application.savefilesfolder.SaveFilesFolderResultDto;
import modules.files.application.syncfilesfolder.SyncFilesFolderDto;
import modules.files.application.syncfilesfolder.SyncFilesFolderResultDto;
import modules.files.application.savefilesfolder.SaveFilesFolderService;
import modules.files.application.syncfilesfolder.SyncFilesFolderService;

/**
 * Motor "Ficheros" de punta a punta, sin servidor: una carpeta en target/ con un CSV, un JSON y un JSON Lines se carga
 * en el SQLite de trabajo, se edita con SQL y se guarda; cada fichero conserva su formato y solo se reescribe lo que
 * cambio.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
final class FilesIntegrationTest extends AbstractTestCase {

    private static final String SESSION = "ctab-files:query-1";
    private static final String BOM = String.valueOf((char) 0xFEFF);

    private static Path folder;
    private static String connectionUuid;

    @BeforeAll
    static void seedFolder() throws IOException {
        bootFreshDatabase("FilesIntegrationTest");
        folder = Path.of("target", "files-it").toAbsolutePath();
        if (Files.isDirectory(folder)) {
            try (var paths = Files.list(folder)) {
                for (Path path : paths.toList()) Files.delete(path);
            }
        }
        Files.createDirectories(folder);
        write("clients.csv", BOM + "id;name;price;code;notes\r\n1;Ana;1.5;1.50;\"dice \"\"hola\"\"; y se va\"\r\n2;Bob;10.25;010;\r\n3;Cleo;7;007;x\r\n");
        write("orders.json", """
            [
                {"id": 1, "paid": true, "total": 9.5, "address": {"city": "Madrid", "zip": "28001"}},
                {"id": 2, "paid": false, "total": 12, "tags": ["a", "b"]}
            ]
            """);
        write("events.jsonl", "{\"at\": \"2026-11-27\", \"kind\": \"login\"}\n{\"at\": \"2026-11-28\", \"kind\": \"logout\"}\n");
        connectionUuid = String.valueOf(CreateConnectionService.getInstance().invoke(CreateConnectionDto.fromPrimitives(Primitives.of(
            "connectionName", "files-it", "dbEngine", "files", "databaseName", folder.toString()
        ))).getConnection().get(ConnectionsKeyEnum.CONNECTION_UUID));
    }

    @AfterAll
    static void closeSessions() {
        TargetSessionFactory.getInstance().closeAll();
    }

    @Test
    @Order(1)
    void testConnectionCountsTheDataFiles() throws SQLException {
        String summary = TestConnectionService.getInstance().invoke(TestConnectionDto.fromPrimitives(Primitives.of(
            "connectionName", "files-it", "dbEngine", "files", "databaseName", folder.toString()
        ))).getServerVersion();

        assertTrue(summary.contains("3"));
    }

    @Test
    @Order(2)
    void testEachFileIsATableWithTypedColumns() throws SQLException {
        SyncFilesFolderResultDto firstSync = sync(false);
        SyncFilesFolderResultDto secondSync = sync(false);

        assertEquals(3, firstSync.getLoadedCount());
        assertEquals(List.of(), firstSync.getErrors());
        assertEquals(0, secondSync.getLoadedCount());
        assertEquals(3, secondSync.getUnchangedCount());
        assertEquals("2", getValue("SELECT COUNT(*) FROM clients WHERE price > 5"));
        assertEquals("text", getValue("SELECT typeof(code) FROM clients WHERE id = 3"));
        assertEquals("007", getValue("SELECT code FROM clients WHERE id = 3"));
        assertEquals("dice \"hola\"; y se va", getValue("SELECT notes FROM clients WHERE id = 1"));
        assertEquals("Madrid", getValue("SELECT json_extract(address, '$.city') FROM orders WHERE id = 1"));
        assertEquals("1", getValue("SELECT paid FROM orders WHERE id = 1"));
        assertEquals("2", getValue("SELECT COUNT(*) FROM events"));
    }

    @Test
    @Order(3)
    void testSaveWritesOnlyChangedTablesInTheirFormat() throws SQLException, IOException {
        execute("UPDATE clients SET name = 'Bea' WHERE id = 2");
        execute("INSERT INTO orders (id, paid, total, address) VALUES (3, 1, 4.25, '{\"city\": \"Bilbao\"}')");
        execute("CREATE TABLE notes (title TEXT, body TEXT)");
        execute("INSERT INTO notes VALUES ('uno', 'a,b')");

        SaveFilesFolderResultDto firstSave = save();
        SaveFilesFolderResultDto secondSave = save();

        assertEquals(List.of("clients.csv", "orders.json"), firstSave.getSavedFileNames());
        assertEquals(List.of("notes.csv"), firstSave.getCreatedFileNames());
        assertEquals(1, firstSave.getUnchangedCount());
        assertEquals(List.of(), secondSave.getSavedFileNames());
        assertEquals(List.of(), secondSave.getCreatedFileNames());

        String clients = read("clients.csv");
        assertEquals(BOM + "id;name;price;code;notes\r\n1;Ana;1.5;1.50;\"dice \"\"hola\"\"; y se va\"\r\n2;Bea;10.25;010;\r\n3;Cleo;7;007;x\r\n", clients);
        String orders = read("orders.json");
        assertTrue(orders.contains("\"paid\": true"));
        assertTrue(orders.contains("\"total\": 12,"));
        assertTrue(orders.contains("\"city\": \"Bilbao\""));
        assertTrue(orders.contains("\"tags\": ["));
        assertTrue(orders.contains("\"id\": 3"));
        assertFalse(orders.contains("null"));
        assertEquals("title,body\nuno,\"a,b\"\n", read("notes.csv"));
        assertEquals("{\"at\": \"2026-11-27\", \"kind\": \"login\"}\n{\"at\": \"2026-11-28\", \"kind\": \"logout\"}\n", read("events.jsonl"));
    }

    @Test
    @Order(4)
    void testChangedOnDiskReloadsAndBrokenJsonDoesNotBlockTheRest() throws SQLException, IOException {
        write("events.jsonl", "{\"at\": \"2026-11-29\", \"kind\": \"login\"}\n");
        write("broken.json", "[\n{\"id\": 1},\n{\"id\": tru}\n]");

        SyncFilesFolderResultDto sync = sync(false);

        assertEquals(1, sync.getLoadedCount());
        assertEquals(1, sync.getErrors().size());
        assertTrue(sync.getErrors().getFirst().contains("broken.json"));
        assertTrue(sync.getErrors().getFirst().contains("line 3"));
        assertEquals("1", getValue("SELECT COUNT(*) FROM events"));
        assertEquals("Bea", getValue("SELECT name FROM clients WHERE id = 2"));
    }

    private static void write(String fileName, String text) throws IOException {
        Files.writeString(folder.resolve(fileName), text, StandardCharsets.UTF_8);
    }

    private static String read(String fileName) throws IOException {
        return Files.readString(folder.resolve(fileName), StandardCharsets.UTF_8);
    }

    private SyncFilesFolderResultDto sync(boolean isReloadForced) throws SQLException {
        return SyncFilesFolderService.getInstance().invoke(SyncFilesFolderDto.fromPrimitives(Primitives.of(
            "connectionUuid", connectionUuid, "sessionKey", SESSION, "isReloadForced", isReloadForced
        )));
    }

    private SaveFilesFolderResultDto save() throws SQLException, IOException {
        return SaveFilesFolderService.getInstance().invoke(SaveFilesFolderDto.fromPrimitives(Map.of(
            "connectionUuid", connectionUuid, "sessionKey", SESSION
        )));
    }

    private void execute(String sql) throws SQLException {
        try (Statement statement = getPdo().createStatement()) {
            statement.execute(sql);
        }
    }

    private String getValue(String sql) throws SQLException {
        try (Statement statement = getPdo().createStatement(); ResultSet resultSet = statement.executeQuery(sql)) {
            return resultSet.next() ? resultSet.getString(1) : null;
        }
    }

    private Connection getPdo() {
        return TargetSessionFactory.getInstance().getSessionPdo(connectionUuid, SESSION);
    }
}
