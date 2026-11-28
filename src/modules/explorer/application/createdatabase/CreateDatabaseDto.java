package modules.explorer.application.createdatabase;

import java.util.Map;

import static modules.shared.application.Primitives.getBool;
import static modules.shared.application.Primitives.getTrimmedString;

/**
 * encryption: "" (no se escribe), "Y" o "N". Las opciones que el motor no usa llegan vacias.
 */
public final class CreateDatabaseDto {

    private final String connectionUuid;
    private final String sessionKey;
    private final String databaseName;
    private final String characterSet;
    private final String collation;
    private final String encryption;
    private final String comment;
    private final String owner;
    private final boolean isIfNotExists;
    private final boolean isConfirmed;

    private CreateDatabaseDto(Map<String, Object> primitives) {
        this.connectionUuid = getTrimmedString(primitives.get("connectionUuid"));
        this.sessionKey = getTrimmedString(primitives.get("sessionKey"));
        this.databaseName = getTrimmedString(primitives.get("databaseName"));
        this.characterSet = getTrimmedString(primitives.get("characterSet"));
        this.collation = getTrimmedString(primitives.get("collation"));
        this.encryption = getTrimmedString(primitives.get("encryption"));
        this.comment = getTrimmedString(primitives.get("comment"));
        this.owner = getTrimmedString(primitives.get("owner"));
        this.isIfNotExists = getBool(primitives.get("isIfNotExists"));
        this.isConfirmed = getBool(primitives.get("isConfirmed"));
    }

    public static CreateDatabaseDto fromPrimitives(Map<String, Object> primitives) {
        return new CreateDatabaseDto(primitives);
    }

    public String getConnectionUuid() {
        return connectionUuid;
    }

    public String getSessionKey() {
        return sessionKey;
    }

    public String getDatabaseName() {
        return databaseName;
    }

    public String getCharacterSet() {
        return characterSet;
    }

    public String getCollation() {
        return collation;
    }

    public String getEncryption() {
        return encryption;
    }

    public String getComment() {
        return comment;
    }

    public String getOwner() {
        return owner;
    }

    public boolean isIfNotExists() {
        return isIfNotExists;
    }

    public boolean isConfirmed() {
        return isConfirmed;
    }
}
