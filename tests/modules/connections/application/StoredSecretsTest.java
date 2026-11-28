package modules.connections.application;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import modules.shared.application.Primitives;
import modules.shared.infrastructure.components.secrets.SecretCipherComponent;
import modules.shared.infrastructure.factories.DbFactory;

import testcase.AbstractTestCase;

import modules.connections.domain.enums.ConnectionsKeyEnum;
import modules.connections.application.createconnection.CreateConnectionDto;
import modules.connections.application.encryptstoredsecrets.EncryptStoredSecretsDto;
import modules.connections.application.renameconnection.RenameConnectionDto;
import modules.connections.application.updateconnection.UpdateConnectionDto;
import modules.connections.application.createconnection.CreateConnectionService;
import modules.connections.application.encryptstoredsecrets.EncryptStoredSecretsService;
import modules.connections.application.renameconnection.RenameConnectionService;
import modules.connections.application.updateconnection.UpdateConnectionService;
import modules.connections.application.mothers.ConnectionPrimitivesMother;
import modules.connections.infrastructure.repositories.ConnectionsReaderSqliteRepository;

/**
 * Feature 47: las passwords nunca quedan en claro en la BD local.
 */
final class StoredSecretsTest extends AbstractTestCase {

    @BeforeEach
    void bootDatabase() {
        bootFreshDatabase("StoredSecretsTest");
    }

    @Test
    void testAesRoundTripAndEveryEncryptionIsDifferent() {
        SecretCipherComponent secretCipherComponent = SecretCipherComponent.getInstance(SecretCipherComponent.MODE_AES);

        String first = secretCipherComponent.encrypt("Sécret.1234 con espacios ");
        String second = secretCipherComponent.encrypt("Sécret.1234 con espacios ");

        assertTrue(first.startsWith("aes:"));
        assertNotEquals(first, second);
        assertEquals("Sécret.1234 con espacios ", secretCipherComponent.decrypt(first));
        assertEquals("", secretCipherComponent.encrypt(""));
        assertEquals("legacy-plain", secretCipherComponent.decrypt("legacy-plain"));
    }

    @Test
    void testDefaultModeRoundTrip() {
        SecretCipherComponent secretCipherComponent = SecretCipherComponent.getInstance();

        String encrypted = secretCipherComponent.encrypt("Secret.1234");

        assertTrue(secretCipherComponent.isEncrypted(encrypted));
        assertEquals("Secret.1234", secretCipherComponent.decrypt(encrypted));
    }

    @Test
    void testSavedConnectionsKeepNoPlainSecrets() throws Exception {
        String connectionUuid = createConnection("loc-cifrada");

        Map<String, Object> storedRow = getStoredSecrets(connectionUuid);

        assertTrue(String.valueOf(storedRow.get("user_password")).matches("^(dpapi|aes):.+"));
        assertTrue(String.valueOf(storedRow.get("ssh_password")).matches("^(dpapi|aes):.+"));
        assertEquals("Secret.1234", ConnectionsReaderSqliteRepository.getInstance().getConnectionByUuid(connectionUuid).getUserPassword());
        assertEquals("Ssh.1234", ConnectionsReaderSqliteRepository.getInstance().getConnectionByUuid(connectionUuid).getSshPassword());
        assertTrue(String.valueOf(storedRow.get("http_url")).matches("^(dpapi|aes):.+"));
        assertEquals("https://yog.example.com/tok-secret", ConnectionsReaderSqliteRepository.getInstance().getConnectionByUuid(connectionUuid).getHttpUrl());
    }

    @Test
    void testLegacyPlainSecretsAreEncryptedAtBoot() throws Exception {
        String connectionUuid = createConnection("loc-antigua");
        try (var statement = DbFactory.getInstance().getSqlitePdo().prepareStatement(
            "UPDATE app_connections SET user_password = 'plain.pass', ssh_password = NULL WHERE connection_uuid = ?"
        )) {
            statement.setString(1, connectionUuid);
            statement.executeUpdate();
        }

        int encryptedConnections = EncryptStoredSecretsService.getInstance().invoke(EncryptStoredSecretsDto.fromPrimitives(Primitives.of())).getEncryptedConnections();
        int secondRun = EncryptStoredSecretsService.getInstance().invoke(EncryptStoredSecretsDto.fromPrimitives(Primitives.of())).getEncryptedConnections();

        assertEquals(1, encryptedConnections);
        assertEquals(0, secondRun);
        assertTrue(String.valueOf(getStoredSecrets(connectionUuid).get("user_password")).matches("^(dpapi|aes):.+"));
        assertEquals("plain.pass", ConnectionsReaderSqliteRepository.getInstance().getConnectionByUuid(connectionUuid).getUserPassword());
    }

    @Test
    void testUndecryptableSecretsBecomeEmptyInsteadOfBreakingTheList() throws Exception {
        String connectionUuid = createConnection("loc-otro-pc");
        try (var statement = DbFactory.getInstance().getSqlitePdo().prepareStatement(
            "UPDATE app_connections SET user_password = 'aes:AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA' WHERE connection_uuid = ?"
        )) {
            statement.setString(1, connectionUuid);
            statement.executeUpdate();
        }

        assertEquals("", ConnectionsReaderSqliteRepository.getInstance().getConnectionByUuid(connectionUuid).getUserPassword());
        assertEquals(1, ConnectionsReaderSqliteRepository.getInstance().getAllConnections().size());
    }

    @Test
    void testEditingOrRenamingKeepsAnUndecryptableSecretAsItWas() throws Exception {
        String connectionUuid = createConnection("loc-otro-pc-edit");
        String undecryptable = "aes:AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
        try (var statement = DbFactory.getInstance().getSqlitePdo().prepareStatement(
            "UPDATE app_connections SET user_password = ? WHERE connection_uuid = ?"
        )) {
            statement.setString(1, undecryptable);
            statement.setString(2, connectionUuid);
            statement.executeUpdate();
        }

        UpdateConnectionService.getInstance().invoke(UpdateConnectionDto.fromPrimitives(Primitives.merge(
            ConnectionPrimitivesMother.getValidLocal("loc-otro-pc-edit"),
            Primitives.of("connectionUuid", connectionUuid, "colorHex", "#00897b", "isPasswordChanged", false,
                "sshEnabled", true, "sshHost", "bastion", "sshUser", "deploy", "isSshSecretsChanged", false, "isHttpUrlChanged", false)
        )));
        String afterEdit = String.valueOf(getStoredSecrets(connectionUuid).get("user_password"));
        RenameConnectionService.getInstance().invoke(RenameConnectionDto.fromPrimitives(Primitives.of(
            "connectionUuid", connectionUuid, "connectionName", "loc-otro-pc-renombrada"
        )));
        Map<String, Object> afterRename = getStoredSecrets(connectionUuid);

        assertEquals(undecryptable, afterEdit);
        assertEquals(undecryptable, afterRename.get("user_password"));
        assertTrue(String.valueOf(afterRename.get("ssh_password")).matches("^(dpapi|aes):.+"));
        assertEquals("Ssh.1234", ConnectionsReaderSqliteRepository.getInstance().getConnectionByUuid(connectionUuid).getSshPassword());
    }

    private String createConnection(String connectionName) {
        Map<String, Object> connection = CreateConnectionService.getInstance().invoke(CreateConnectionDto.fromPrimitives(Primitives.merge(
            ConnectionPrimitivesMother.getValidLocal(connectionName),
            Primitives.of("sshEnabled", true, "sshHost", "bastion", "sshUser", "deploy", "sshPassword", "Ssh.1234",
                "httpUrl", "https://yog.example.com/tok-secret")
        ))).getConnection();
        return String.valueOf(connection.get(ConnectionsKeyEnum.CONNECTION_UUID));
    }

    private Map<String, Object> getStoredSecrets(String connectionUuid) throws Exception {
        try (var statement = DbFactory.getInstance().getSqlitePdo().prepareStatement(
            "SELECT user_password, ssh_password, http_url FROM app_connections WHERE connection_uuid = ?"
        )) {
            statement.setString(1, connectionUuid);
            try (var resultSet = statement.executeQuery()) {
                resultSet.next();
                return Primitives.of("user_password", resultSet.getString(1), "ssh_password", resultSet.getString(2), "http_url", resultSet.getString(3));
            }
        }
    }
}
