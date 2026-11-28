package modules.connections.application;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import modules.shared.domain.enums.ExceptionEnum;
import modules.shared.application.Primitives;
import modules.shared.infrastructure.exceptions.FieldsException;

import testcase.AbstractTestCase;

import modules.connections.domain.enums.ConnectionsKeyEnum;
import modules.connections.domain.entities.ConnectionEntity;
import modules.connections.application.cloneconnection.CloneConnectionDto;
import modules.connections.application.createconnection.CreateConnectionDto;
import modules.connections.application.createconnection.CreateConnectionResultDto;
import modules.connections.application.deleteconnection.DeleteConnectionDto;
import modules.connections.application.getconnection.GetConnectionDto;
import modules.connections.application.listconnections.ListConnectionsDto;
import modules.connections.application.testconnection.TestConnectionDto;
import modules.connections.application.updateconnection.UpdateConnectionDto;
import modules.connections.application.cloneconnection.CloneConnectionService;
import modules.connections.application.createconnection.CreateConnectionService;
import modules.connections.application.deleteconnection.DeleteConnectionService;
import modules.connections.application.getconnection.GetConnectionService;
import modules.connections.application.listconnections.ListConnectionsService;
import modules.connections.application.testconnection.TestConnectionService;
import modules.connections.application.updateconnection.UpdateConnectionService;
import modules.connections.application.mothers.ConnectionPrimitivesMother;
import modules.connections.infrastructure.repositories.ConnectionsReaderSqliteRepository;
import modules.connections.domain.exceptions.ConnectionsException;

/**
 * Los casos de uso del gestor de conexiones contra una BD local nueva en cada test. TestConnection solo se prueba
 * en sus caminos sin red (validacion y servidor inalcanzable): un MySQL real no se da por supuesto.
 */
final class ConnectionsServicesTest extends AbstractTestCase {

    @BeforeEach
    void bootDatabase() {
        bootFreshDatabase("ConnectionsServicesTest");
    }

    @Test
    void testCreateConnectionSavesItWithoutReturningThePassword() {
        Map<String, Object> connection = createConnection("loc-mi-app").getConnection();

        assertTrue(String.valueOf(connection.get(ConnectionsKeyEnum.CONNECTION_UUID)).startsWith(ConnectionEntity.CONNECTION_UUID_PREFIX));
        assertEquals("loc-mi-app", connection.get(ConnectionsKeyEnum.CONNECTION_NAME));
        assertEquals("#e53935", connection.get(ConnectionsKeyEnum.COLOR_HEX));
        assertFalse(connection.containsKey(ConnectionsKeyEnum.USER_PASSWORD));
        assertEquals("Secret.1234", getSavedPassword(connection));
    }

    @Test
    void testSqliteWithAFolderCreatesAnEmptySqliteFileInIt() throws SQLException, IOException {
        Path sqliteFolder = Files.createDirectories(Path.of("target", "test-home", "ConnectionsServicesTest", "sqlite-folder").toAbsolutePath());
        Files.deleteIfExists(sqliteFolder.resolve("loc-lite.sqlite"));
        Map<String, Object> sqlitePrimitives = Primitives.merge(ConnectionPrimitivesMother.getValidLocal("Loc Lite"), Primitives.of(
            "dbEngine", "sqlite", "host", "", "port", "", "userName", "", "databaseName", sqliteFolder.toString()
        ));

        String testMessage = TestConnectionService.getInstance().invoke(TestConnectionDto.fromPrimitives(sqlitePrimitives)).getMessage();
        Map<String, Object> connection = CreateConnectionService.getInstance().invoke(CreateConnectionDto.fromPrimitives(sqlitePrimitives)).getConnection();

        assertTrue(testMessage.contains(sqliteFolder.toString()));
        assertEquals(sqliteFolder.resolve("loc-lite.sqlite").toString(), connection.get(ConnectionsKeyEnum.DATABASE_NAME));
        assertTrue(Files.isRegularFile(sqliteFolder.resolve("loc-lite.sqlite")));
        assertEquals(0L, Files.size(sqliteFolder.resolve("loc-lite.sqlite")));
    }

    @Test
    void testCreateConnectionAppliesDefaultEngineAndPort() {
        Map<String, Object> connection = CreateConnectionService.getInstance().invoke(CreateConnectionDto.fromPrimitives(
            Primitives.merge(ConnectionPrimitivesMother.getValidLocal("loc-defaults"), Primitives.of("dbEngine", "", "port", ""))
        )).getConnection();

        assertEquals("mysql", connection.get(ConnectionsKeyEnum.DB_ENGINE));
        assertEquals(3306, connection.get(ConnectionsKeyEnum.PORT));
    }

    @Test
    void testHttpTunnelNeedsAnHttpUrlAndExcludesTheSshTunnel() {
        FieldsException fieldsException = assertThrows(FieldsException.class, () -> CreateConnectionService.getInstance().invoke(
            CreateConnectionDto.fromPrimitives(Primitives.merge(ConnectionPrimitivesMother.getValidLocal("loc-http"), Primitives.of(
                "httpEnabled", true,
                "httpUrl", "ftp://yog.example.com/tok",
                "httpTimeoutS", "0",
                "sshEnabled", true, "sshHost", "bastion", "sshUser", "deploy", "sshPassword", "x"
            )))
        ));
        Map<String, Object> saved = CreateConnectionService.getInstance().invoke(CreateConnectionDto.fromPrimitives(Primitives.merge(
            ConnectionPrimitivesMother.getValidLocal("loc-http-ok"),
            Primitives.of("httpEnabled", true, "httpUrl", "https://yog.example.com/tok-123", "httpTimeoutS", "45")
        ))).getConnection();

        List<String> errors = fieldsException.getErrors();
        assertEquals(2, errors.size());
        assertTrue(errors.contains("http_enabled: use either the SSH tunnel or the HTTP tunnel, not both"));
        assertTrue(errors.contains("http_url: must start with http:// or https://"));
        assertEquals(true, saved.get(ConnectionsKeyEnum.HTTP_ENABLED));
        assertEquals(45, saved.get(ConnectionsKeyEnum.HTTP_TIMEOUT_S));
        // la URL lleva el token: no sale de la entidad hacia la UI
        assertFalse(saved.containsKey(ConnectionsKeyEnum.HTTP_URL));
    }

    @Test
    void testCreateConnectionFailsWithOneErrorPerWrongField() {
        FieldsException fieldsException = assertThrows(FieldsException.class, () -> CreateConnectionService.getInstance().invoke(
            CreateConnectionDto.fromPrimitives(Primitives.merge(ConnectionPrimitivesMother.getValidLocal(""), Primitives.of(
                "host", "",
                "port", "70000",
                "colorHex", "red"
            )))
        ));

        List<String> errors = fieldsException.getErrors();
        assertEquals(4, errors.size());
        assertTrue(errors.contains("connection_name: field is required"));
        assertTrue(errors.contains("host: field is required"));
        assertTrue(errors.contains("port: value must be between 1 and 65535"));
        assertTrue(errors.contains("color_hex: invalid color, expected format #RRGGBB"));
    }

    @Test
    void testCreateConnectionFailsIfNameExistsIgnoringCase() {
        createConnection("prod-mi-app");

        ConnectionsException connectionsException = assertThrows(ConnectionsException.class, () -> createConnection("PROD-mi-app"));
        assertEquals(ExceptionEnum.CODE_CONFLICT, connectionsException.getCode());
    }

    @Test
    void testUpdateConnectionKeepsPasswordIfNotChanged() {
        String connectionUuid = getConnectionUuid(createConnection("pre-mi-app").getConnection());

        Map<String, Object> connection = UpdateConnectionService.getInstance().invoke(UpdateConnectionDto.fromPrimitives(Primitives.merge(
            ConnectionPrimitivesMother.getValidLocal("pre-mi-app-renamed"),
            Primitives.of("connectionUuid", connectionUuid, "isPasswordChanged", false, "userPassword", "", "isProduction", true)
        ))).getConnection();

        assertEquals("pre-mi-app-renamed", connection.get(ConnectionsKeyEnum.CONNECTION_NAME));
        assertEquals(true, connection.get(ConnectionsKeyEnum.IS_PRODUCTION));
        assertEquals("Secret.1234", getSavedPassword(connection));
    }

    @Test
    void testUpdateConnectionChangesPasswordIfChanged() {
        String connectionUuid = getConnectionUuid(createConnection("pre-mi-app").getConnection());

        Map<String, Object> connection = UpdateConnectionService.getInstance().invoke(UpdateConnectionDto.fromPrimitives(Primitives.merge(
            ConnectionPrimitivesMother.getValidLocal("pre-mi-app"),
            Primitives.of("connectionUuid", connectionUuid, "isPasswordChanged", true, "userPassword", "Other.5678")
        ))).getConnection();

        assertEquals("Other.5678", getSavedPassword(connection));
    }

    @Test
    void testUpdateConnectionFailsIfNotFound() {
        ConnectionsException connectionsException = assertThrows(ConnectionsException.class, () -> UpdateConnectionService.getInstance().invoke(
            UpdateConnectionDto.fromPrimitives(Primitives.merge(
                ConnectionPrimitivesMother.getValidLocal("x"), Primitives.of("connectionUuid", "conn-does-not-exist")
            ))
        ));
        assertEquals(ExceptionEnum.CODE_NOT_FOUND, connectionsException.getCode());
    }

    @Test
    void testServicesByUuidFailWithBadRequestIfTheUuidIsEmpty() {
        Map<String, Object> primitives = Primitives.of("connectionUuid", " ");

        ConnectionsException getException = assertThrows(ConnectionsException.class, () -> GetConnectionService.getInstance().invoke(
            GetConnectionDto.fromPrimitives(primitives)
        ));
        ConnectionsException deleteException = assertThrows(ConnectionsException.class, () -> DeleteConnectionService.getInstance().invoke(
            DeleteConnectionDto.fromPrimitives(primitives)
        ));
        ConnectionsException cloneException = assertThrows(ConnectionsException.class, () -> CloneConnectionService.getInstance().invoke(
            CloneConnectionDto.fromPrimitives(primitives)
        ));
        assertEquals(ExceptionEnum.CODE_BAD_REQUEST, getException.getCode());
        assertEquals(ExceptionEnum.CODE_BAD_REQUEST, deleteException.getCode());
        assertEquals(ExceptionEnum.CODE_BAD_REQUEST, cloneException.getCode());
    }

    @Test
    void testCloneConnectionCopiesWithFreeNameAndWithoutProductionMark() {
        String connectionUuid = getConnectionUuid(CreateConnectionService.getInstance().invoke(CreateConnectionDto.fromPrimitives(
            Primitives.merge(ConnectionPrimitivesMother.getValidLocal("prod-mi-app"), Primitives.of("isProduction", true))
        )).getConnection());

        Map<String, Object> firstClone = cloneConnection(connectionUuid);
        Map<String, Object> secondClone = cloneConnection(connectionUuid);

        assertEquals("prod-mi-app (copy)", firstClone.get(ConnectionsKeyEnum.CONNECTION_NAME));
        assertEquals("prod-mi-app (copy) (copy)", secondClone.get(ConnectionsKeyEnum.CONNECTION_NAME));
        assertNotEquals(connectionUuid, getConnectionUuid(firstClone));
        assertEquals(false, firstClone.get(ConnectionsKeyEnum.IS_PRODUCTION));
        assertEquals("Secret.1234", getSavedPassword(firstClone));
    }

    @Test
    void testListConnectionsIsSortedByNameIgnoringCase() {
        createConnection("pre-b");
        createConnection("Loc-a");
        createConnection("prod-c");

        List<Object> names = ListConnectionsService.getInstance().invoke(ListConnectionsDto.fromPrimitives(Primitives.of())).getConnections().stream()
            .map(connection -> connection.get(ConnectionsKeyEnum.CONNECTION_NAME))
            .toList();
        assertEquals(List.of("Loc-a", "pre-b", "prod-c"), names);
    }

    @Test
    void testDeleteConnectionRemovesIt() {
        String connectionUuid = getConnectionUuid(createConnection("loc-borrar").getConnection());

        DeleteConnectionService.getInstance().invoke(DeleteConnectionDto.fromPrimitives(Primitives.of("connectionUuid", connectionUuid)));

        ConnectionsException connectionsException = assertThrows(ConnectionsException.class, () -> GetConnectionService.getInstance().invoke(
            GetConnectionDto.fromPrimitives(Primitives.of("connectionUuid", connectionUuid))
        ));
        assertEquals(ExceptionEnum.CODE_NOT_FOUND, connectionsException.getCode());
    }

    @Test
    void testTestConnectionValidatesBeforeConnecting() {
        assertThrows(FieldsException.class, () -> TestConnectionService.getInstance().invoke(TestConnectionDto.fromPrimitives(
            Primitives.merge(ConnectionPrimitivesMother.getValidLocal("x"), Primitives.of("host", ""))
        )));
    }

    @Test
    void testTestConnectionFailsWithTheSqlErrorIfServerIsUnreachable() {
        // puerto 1 de localhost: nadie escucha, el rechazo es inmediato (no espera al timeout)
        // el SQLException sube tal cual hasta el controller (DDD 19), que lo ensena con su codigo
        SQLException unreachableServerException = assertThrows(SQLException.class, () -> TestConnectionService.getInstance().invoke(
            TestConnectionDto.fromPrimitives(Primitives.merge(ConnectionPrimitivesMother.getValidLocal("loc-off"), Primitives.of("port", "1")))
        ));
        assertTrue(unreachableServerException.getMessage().contains("Communications link failure"));
    }

    @Test
    void testSshTunnelNeedsHostUserAndPasswordOrKey() {
        FieldsException fieldsException = assertThrows(FieldsException.class, () -> CreateConnectionService.getInstance().invoke(
            CreateConnectionDto.fromPrimitives(Primitives.merge(ConnectionPrimitivesMother.getValidLocal("loc-ssh"), Primitives.of("sshEnabled", true)))
        ));

        assertEquals(List.of("ssh_host", "ssh_user", "ssh_password"), fieldsException.getErrors().stream().map(error -> error.split(":")[0]).toList());
    }

    @Test
    void testSshSecretsAreHiddenAndKeptWhenNotChanged() {
        Map<String, Object> connection = CreateConnectionService.getInstance().invoke(CreateConnectionDto.fromPrimitives(Primitives.merge(
            ConnectionPrimitivesMother.getValidLocal("loc-ssh-keep"),
            Primitives.of("sshEnabled", true, "sshHost", "bastion", "sshUser", "deploy", "sshPassword", "Ssh.Secret")
        ))).getConnection();
        String connectionUuid = String.valueOf(connection.get(ConnectionsKeyEnum.CONNECTION_UUID));

        UpdateConnectionService.getInstance().invoke(UpdateConnectionDto.fromPrimitives(Primitives.merge(
            ConnectionPrimitivesMother.getValidLocal("loc-ssh-keep"),
            Primitives.of("connectionUuid", connectionUuid, "isPasswordChanged", false,
                "sshEnabled", true, "sshHost", "bastion2", "sshUser", "deploy", "sshPassword", "", "isSshSecretsChanged", false)
        )));
        ConnectionEntity savedConnectionEntity = ConnectionsReaderSqliteRepository.getInstance().getConnectionByUuid(connectionUuid);

        assertFalse(connection.containsKey(ConnectionsKeyEnum.SSH_PASSWORD));
        assertEquals("bastion2", savedConnectionEntity.getSshHost());
        assertEquals("Ssh.Secret", savedConnectionEntity.getSshPassword());
        assertEquals(22, savedConnectionEntity.getSshPort());
    }

    private CreateConnectionResultDto createConnection(String connectionName) {
        return CreateConnectionService.getInstance().invoke(
            CreateConnectionDto.fromPrimitives(ConnectionPrimitivesMother.getValidLocal(connectionName))
        );
    }

    private Map<String, Object> cloneConnection(String connectionUuid) {
        return CloneConnectionService.getInstance().invoke(
            CloneConnectionDto.fromPrimitives(Primitives.of("connectionUuid", connectionUuid))
        ).getConnection();
    }

    private String getConnectionUuid(Map<String, Object> connection) {
        return String.valueOf(connection.get(ConnectionsKeyEnum.CONNECTION_UUID));
    }

    private String getSavedPassword(Map<String, Object> connection) {
        return ConnectionsReaderSqliteRepository.getInstance().getConnectionByUuid(getConnectionUuid(connection)).getUserPassword();
    }
}
