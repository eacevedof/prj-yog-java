package modules.mongo.application.savedocument;

import java.util.Map;

import static modules.shared.application.Primitives.getBool;
import static modules.shared.application.Primitives.getTrimmedString;

/**
 * Guardar un documento del navegador: isNew lo inserta (con _id nuevo si no lo trae); si no, reemplaza el que tiene su
 * _id. Sirve tambien para borrar (isDelete), por _id.
 */
public final class SaveMongoDocumentDto {

    private final String connectionUuid;
    private final String sessionKey;
    private final String databaseName;
    private final String collectionName;
    private final String documentJson;
    private final boolean isNew;
    private final boolean isDelete;
    private final boolean isConfirmed;

    private SaveMongoDocumentDto(Map<String, Object> primitives) {
        this.connectionUuid = getTrimmedString(primitives.get("connectionUuid"));
        this.sessionKey = getTrimmedString(primitives.get("sessionKey"));
        this.databaseName = getTrimmedString(primitives.get("databaseName"));
        this.collectionName = getTrimmedString(primitives.get("collectionName"));
        this.documentJson = getTrimmedString(primitives.get("documentJson"));
        this.isNew = getBool(primitives.get("isNew"));
        this.isDelete = getBool(primitives.get("isDelete"));
        this.isConfirmed = getBool(primitives.get("isConfirmed"));
    }

    public static SaveMongoDocumentDto fromPrimitives(Map<String, Object> primitives) {
        return new SaveMongoDocumentDto(primitives);
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

    public String getCollectionName() {
        return collectionName;
    }

    public String getDocumentJson() {
        return documentJson;
    }

    public boolean isNew() {
        return isNew;
    }

    public boolean isDelete() {
        return isDelete;
    }

    public boolean isConfirmed() {
        return isConfirmed;
    }
}
