package modules.files.application.syncfilesfolder;

import java.util.Map;

import static modules.shared.application.Primitives.getBool;
import static modules.shared.application.Primitives.getTrimmedString;

public final class SyncFilesFolderDto {

    private final String connectionUuid;
    private final String sessionKey;
    private final boolean isReloadForced;

    private SyncFilesFolderDto(Map<String, Object> primitives) {
        this.connectionUuid = getTrimmedString(primitives.get("connectionUuid"));
        this.sessionKey = getTrimmedString(primitives.get("sessionKey"));
        this.isReloadForced = getBool(primitives.get("isReloadForced"));
    }

    public static SyncFilesFolderDto fromPrimitives(Map<String, Object> primitives) {
        return new SyncFilesFolderDto(primitives);
    }

    public String getConnectionUuid() {
        return connectionUuid;
    }

    public String getSessionKey() {
        return sessionKey;
    }

    /**
     * true: "Recargar desde ficheros", se cargan todos aunque no hayan cambiado y se pierde lo no guardado.
     */
    public boolean isReloadForced() {
        return isReloadForced;
    }
}
