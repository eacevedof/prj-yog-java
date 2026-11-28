package modules.connections.application.encryptstoredsecrets;

import modules.shared.application.Primitives;

import modules.connections.domain.entities.ConnectionEntity;
import modules.connections.infrastructure.repositories.ConnectionsReaderSqliteRepository;
import modules.connections.infrastructure.repositories.ConnectionsWriterSqliteRepository;

/**
 * Feature 47, al arrancar: las passwords guardadas en claro antes de la 0.13.0 se cifran (una vez; despues ya no
 * queda ninguna). El lector las devuelve tal cual porque no llevan prefijo, y el escritor las guarda cifradas.
 */
public final class EncryptStoredSecretsService {

    private EncryptStoredSecretsService() {
    }

    public static EncryptStoredSecretsService getInstance() {
        return new EncryptStoredSecretsService();
    }

    public EncryptStoredSecretsResultDto invoke(EncryptStoredSecretsDto encryptStoredSecretsDto) {
        ConnectionsReaderSqliteRepository connectionsReaderSqliteRepository = ConnectionsReaderSqliteRepository.getInstance();
        ConnectionsWriterSqliteRepository connectionsWriterSqliteRepository = ConnectionsWriterSqliteRepository.getInstance();
        int encryptedConnections = 0;
        for (int connectionId : connectionsReaderSqliteRepository.getConnectionIdsWithPlainSecrets()) {
            ConnectionEntity connectionEntity = connectionsReaderSqliteRepository.getConnectionById(connectionId);
            if (connectionEntity == null) continue;
            encryptedConnections += connectionsWriterSqliteRepository.updateSecretsById(connectionEntity);
        }
        return EncryptStoredSecretsResultDto.fromPrimitives(Primitives.of("encryptedConnections", encryptedConnections));
    }
}
