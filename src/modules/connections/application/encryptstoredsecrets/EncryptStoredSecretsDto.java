package modules.connections.application.encryptstoredsecrets;

import java.util.Map;

/**
 * Sin datos de entrada: se recorren todas las conexiones guardadas.
 */
public final class EncryptStoredSecretsDto {

    private EncryptStoredSecretsDto(Map<String, Object> primitives) {
    }

    public static EncryptStoredSecretsDto fromPrimitives(Map<String, Object> primitives) {
        return new EncryptStoredSecretsDto(primitives);
    }
}
