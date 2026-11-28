package modules.connections.application.encryptstoredsecrets;

import java.util.Map;

import static modules.shared.application.Primitives.getInt;

public final class EncryptStoredSecretsResultDto {

    private final int encryptedConnections;

    private EncryptStoredSecretsResultDto(Map<String, Object> primitives) {
        this.encryptedConnections = getInt(primitives.get("encryptedConnections"));
    }

    public static EncryptStoredSecretsResultDto fromPrimitives(Map<String, Object> primitives) {
        return new EncryptStoredSecretsResultDto(primitives);
    }

    public int getEncryptedConnections() {
        return encryptedConnections;
    }
}
