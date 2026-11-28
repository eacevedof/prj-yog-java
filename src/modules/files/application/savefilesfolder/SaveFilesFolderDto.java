package modules.files.application.savefilesfolder;

import java.util.Map;

import static modules.shared.application.Primitives.getTrimmedString;

public final class SaveFilesFolderDto {

    private final String connectionUuid;
    private final String sessionKey;

    private SaveFilesFolderDto(Map<String, Object> primitives) {
        this.connectionUuid = getTrimmedString(primitives.get("connectionUuid"));
        this.sessionKey = getTrimmedString(primitives.get("sessionKey"));
    }

    public static SaveFilesFolderDto fromPrimitives(Map<String, Object> primitives) {
        return new SaveFilesFolderDto(primitives);
    }

    public String getConnectionUuid() {
        return connectionUuid;
    }

    public String getSessionKey() {
        return sessionKey;
    }
}
