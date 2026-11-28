package modules.shared.infrastructure.components.uuid;

import java.util.UUID;

public final class UuidGenerator {

    private UuidGenerator() {
    }

    public static UuidGenerator getInstance() {
        return new UuidGenerator();
    }

    /**
     * <prefijo><uuid v4>, p.ej. "conn-6e142a62-5af7-4d43-b786-cec6ac17be62": el prefijo dice de que entidad es.
     */
    public String getUniqueIdWithPrefix(String prefix) {
        return prefix + UUID.randomUUID();
    }
}
