package modules.history.application;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
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
import modules.query.application.executesqlfile.ExecuteSqlFileDto;
import modules.query.application.executesqlfile.ExecuteSqlFileResultDto;
import modules.query.application.executequery.ExecuteQueryService;
import modules.query.application.executesqlfile.ExecuteSqlFileService;
import modules.query.domain.exceptions.QueryException;

import modules.history.application.exportqueryhistory.ExportQueryHistoryDto;
import modules.history.application.exportqueryhistory.ExportQueryHistoryResultDto;
import modules.history.application.exportqueryhistory.ExportQueryHistoryService;

/**
 * La idea de "Exportar SQL" del historial: lanzar el fichero en una BD vacia (Importar) y llegar a la misma foto. Sobre
 * dos ficheros SQLite: lo hecho en el primero se exporta y se importa en el segundo.
 */
final class QueryHistoryExportTest extends AbstractTestCase {

    private static final String MAIN = "main";
    private static final String SESSION = "history-export";

    private static String sourceConnectionUuid;
    private static String targetConnectionUuid;

    @BeforeAll
    static void createSqliteConnections() throws Exception {
        bootFreshDatabase("QueryHistoryExportTest");
        sourceConnectionUuid = getNewSqliteConnectionUuid("lite-history-source");
        targetConnectionUuid = getNewSqliteConnectionUuid("lite-history-target");
    }

    @AfterAll
    static void closeSessions() {
        TargetSessionFactory.getInstance().closeAll();
    }

    @Test
    void testExportedHistoryReplayedOnAnEmptyDatabaseReachesTheSameState() throws Exception {
        execute(sourceConnectionUuid, "CREATE TABLE items (id INTEGER PRIMARY KEY, name TEXT)");
        execute(sourceConnectionUuid, "INSERT INTO items (name) VALUES ('uno'), ('dos'), ('tres');");
        execute(sourceConnectionUuid, "UPDATE items SET nope = 1");
        execute(sourceConnectionUuid, "-- la segunda fuera\nDELETE FROM items WHERE id = 2");
        execute(sourceConnectionUuid, "UPDATE items SET name = 'TRES */' WHERE id = 3");
        execute(sourceConnectionUuid, "SELECT * FROM items");
        Path scriptFile = Path.of("target", "javayog-history-export.sql").toAbsolutePath();

        ExportQueryHistoryResultDto exportQueryHistoryResultDto = ExportQueryHistoryService.getInstance().invoke(ExportQueryHistoryDto.fromPrimitives(Primitives.of(
            "connectionUuid", sourceConnectionUuid,
            "searchText", "",
            "dbEngine", "sqlite",
            "filePath", scriptFile.toString()
        )));
        ExecuteSqlFileResultDto executeSqlFileResultDto = ExecuteSqlFileService.getInstance().invoke(ExecuteSqlFileDto.fromPrimitives(Primitives.of(
            "connectionUuid", targetConnectionUuid,
            "sessionKey", SESSION,
            "databaseName", MAIN,
            "filePath", scriptFile.toString(),
            "isAbortOnError", true
        )));

        assertEquals(6, exportQueryHistoryResultDto.getExportedCount());
        assertEquals(0L, executeSqlFileResultDto.getErrorCount());
        assertEquals(5L, executeSqlFileResultDto.getExecutedCount());
        assertEquals(getRows(sourceConnectionUuid), getRows(targetConnectionUuid));
        assertTrue(Files.readString(scriptFile).contains("ERROR, not replayed\nUPDATE items SET nope = 1"));
    }

    @Test
    void testAnInvalidFilePathIsABadRequest() {
        QueryException invalidPath = assertThrows(QueryException.class, () -> ExecuteSqlFileService.getInstance().invoke(ExecuteSqlFileDto.fromPrimitives(Primitives.of(
            "connectionUuid", targetConnectionUuid,
            "sessionKey", SESSION,
            "databaseName", MAIN,
            "filePath", "bad\0name.sql"
        ))));

        assertEquals(ExceptionEnum.CODE_BAD_REQUEST, invalidPath.getCode());
    }

    private static String getNewSqliteConnectionUuid(String connectionName) throws Exception {
        Path sqliteFile = Path.of("target", "javayog-" + connectionName + ".sqlite").toAbsolutePath();
        Files.deleteIfExists(sqliteFile);
        Files.createFile(sqliteFile);
        return String.valueOf(CreateConnectionService.getInstance().invoke(CreateConnectionDto.fromPrimitives(Primitives.of(
            "connectionName", connectionName, "dbEngine", "sqlite", "databaseName", sqliteFile.toString()
        ))).getConnection().get(ConnectionsKeyEnum.CONNECTION_UUID));
    }

    private static Map<String, Object> execute(String connectionUuid, String sql) {
        return ExecuteQueryService.getInstance().invoke(ExecuteQueryDto.fromPrimitives(Primitives.of(
            "connectionUuid", connectionUuid,
            "sessionKey", SESSION + "-" + connectionUuid,
            "databaseName", MAIN,
            "sqlQuery", sql,
            "maxRows", 100
        ))).getResults().getFirst();
    }

    @SuppressWarnings("unchecked")
    private static List<List<String>> getRows(String connectionUuid) {
        return (List<List<String>>) execute(connectionUuid, "SELECT id, name FROM items ORDER BY id").get(QueryKeyEnum.ROWS);
    }
}
