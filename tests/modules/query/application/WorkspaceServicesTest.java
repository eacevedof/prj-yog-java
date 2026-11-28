package modules.query.application;

import java.sql.SQLException;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import modules.shared.domain.enums.ExceptionEnum;
import modules.shared.application.Primitives;

import testcase.AbstractTestCase;

import modules.connections.domain.enums.ConnectionsKeyEnum;
import modules.connections.application.createconnection.CreateConnectionDto;
import modules.connections.application.createconnection.CreateConnectionService;
import modules.connections.application.mothers.ConnectionPrimitivesMother;
import modules.connections.domain.exceptions.ConnectionsException;

import modules.explorer.application.listdatabaseobjects.ListDatabaseObjectsDto;
import modules.explorer.application.listdatabases.ListDatabasesDto;
import modules.explorer.application.listdatabaseobjects.ListDatabaseObjectsService;
import modules.explorer.application.listdatabases.ListDatabasesService;
import modules.explorer.domain.exceptions.ExplorerException;

import modules.query.application.cancelquery.CancelQueryDto;
import modules.query.application.executequery.ExecuteQueryDto;
import modules.query.application.cancelquery.CancelQueryService;
import modules.query.application.executequery.ExecuteQueryService;
import modules.query.domain.exceptions.QueryException;

/**
 * Los casos de uso del arbol y del editor en sus caminos sin MySQL: validacion de la entrada, conexion que no existe y
 * servidor inalcanzable (puerto 1 de localhost: rechaza al instante). Contra un MySQL real se prueban a mano.
 */
final class WorkspaceServicesTest extends AbstractTestCase {

    private static final String SESSION_KEY = "ctab-test:query-1";

    @BeforeEach
    void bootDatabase() {
        bootFreshDatabase("WorkspaceServicesTest");
    }

    @Test
    void testExecuteQueryRejectsAnEditorWithoutStatements() {
        QueryException queryException = assertThrows(QueryException.class, () -> executeQuery("conn-x", "-- solo un comentario"));

        assertEquals(ExceptionEnum.CODE_BAD_REQUEST, queryException.getCode());
    }

    @Test
    void testExecuteQueryRequiresTheSession() {
        assertThrows(QueryException.class, () -> ExecuteQueryService.getInstance().invoke(ExecuteQueryDto.fromPrimitives(
            Primitives.of("connectionUuid", "conn-x", "sqlQuery", "SELECT 1")
        )));
    }

    @Test
    void testExecuteQueryOnAnUnknownConnectionIsNotFound() {
        ConnectionsException connectionsException = assertThrows(ConnectionsException.class, () -> executeQuery("conn-no-existe", "SELECT 1"));

        assertEquals(ExceptionEnum.CODE_NOT_FOUND, connectionsException.getCode());
    }

    @Test
    void testExecuteQueryOnAnUnreachableServerIsServiceUnavailable() {
        String connectionUuid = createUnreachableConnection();

        ConnectionsException connectionsException = assertThrows(ConnectionsException.class, () -> executeQuery(connectionUuid, "SELECT 1"));

        assertEquals(ExceptionEnum.CODE_SERVICE_UNAVAILABLE, connectionsException.getCode());
    }

    @Test
    void testProductionDestructiveStatementsNeedConfirmationBeforeConnecting() {
        String connectionUuid = createUnreachableConnection(true);

        QueryException queryException = assertThrows(QueryException.class, () -> executeQuery(connectionUuid, "SELECT 1; DELETE FROM users"));

        assertEquals(ExceptionEnum.CODE_PRECONDITION_REQUIRED, queryException.getCode());
        assertTrue(queryException.getMessage().contains("DELETE FROM users;"));
    }

    @Test
    void testProductionConfirmedOrSafeStatementsGoToTheServer() {
        String connectionUuid = createUnreachableConnection(true);

        ConnectionsException confirmed = assertThrows(ConnectionsException.class, () -> ExecuteQueryService.getInstance().invoke(
            ExecuteQueryDto.fromPrimitives(Primitives.of(
                "connectionUuid", connectionUuid, "sessionKey", SESSION_KEY, "sqlQuery", "DELETE FROM users", "isConfirmed", true
            ))
        ));
        ConnectionsException safe = assertThrows(ConnectionsException.class, () -> executeQuery(connectionUuid, "DELETE FROM users WHERE id = 1"));

        assertEquals(ExceptionEnum.CODE_SERVICE_UNAVAILABLE, confirmed.getCode());
        assertEquals(ExceptionEnum.CODE_SERVICE_UNAVAILABLE, safe.getCode());
    }

    @Test
    void testNonProductionDestructiveStatementsDoNotAsk() {
        String connectionUuid = createUnreachableConnection();

        ConnectionsException connectionsException = assertThrows(ConnectionsException.class, () -> executeQuery(connectionUuid, "DROP TABLE users"));

        assertEquals(ExceptionEnum.CODE_SERVICE_UNAVAILABLE, connectionsException.getCode());
    }

    @Test
    void testListDatabasesOnAnUnreachableServerIsServiceUnavailable() {
        String connectionUuid = createUnreachableConnection();

        ConnectionsException connectionsException = assertThrows(ConnectionsException.class, () -> ListDatabasesService.getInstance().invoke(
            ListDatabasesDto.fromPrimitives(Primitives.of("connectionUuid", connectionUuid, "sessionKey", "ctab-test:explorer"))
        ));

        assertEquals(ExceptionEnum.CODE_SERVICE_UNAVAILABLE, connectionsException.getCode());
    }

    @Test
    void testListDatabaseObjectsRequiresTheDatabase() throws SQLException {
        assertThrows(ExplorerException.class, () -> ListDatabaseObjectsService.getInstance().invoke(
            ListDatabaseObjectsDto.fromPrimitives(Primitives.of("connectionUuid", "conn-x", "sessionKey", "ctab-test:explorer"))
        ));
    }

    @Test
    void testCancelWithNothingRunningSaysSo() throws SQLException {
        assertFalse(CancelQueryService.getInstance().invoke(CancelQueryDto.fromPrimitives(Primitives.of("sessionKey", SESSION_KEY))).wasRunning());
    }

    private void executeQuery(String connectionUuid, String sql) {
        ExecuteQueryService.getInstance().invoke(ExecuteQueryDto.fromPrimitives(Primitives.of(
            "connectionUuid", connectionUuid,
            "sessionKey", SESSION_KEY,
            "sqlQuery", sql,
            "maxRows", 100
        )));
    }

    private String createUnreachableConnection() {
        return createUnreachableConnection(false);
    }

    private String createUnreachableConnection(boolean isProduction) {
        Map<String, Object> connection = CreateConnectionService.getInstance().invoke(CreateConnectionDto.fromPrimitives(
            Primitives.merge(ConnectionPrimitivesMother.getValidLocal("loc-inalcanzable"), Primitives.of("port", "1", "isProduction", isProduction))
        )).getConnection();
        return String.valueOf(connection.get(ConnectionsKeyEnum.CONNECTION_UUID));
    }
}
