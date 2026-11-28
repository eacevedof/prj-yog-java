package modules.mongo.application.savedocument;

import com.mongodb.MongoException;
import com.mongodb.MongoSocketException;
import com.mongodb.MongoTimeoutException;

import org.bson.Document;
import org.bson.json.JsonParseException;

import modules.shared.application.Primitives;

import static core.config.Global.__;

import modules.connections.domain.entities.ConnectionEntity;
import modules.connections.infrastructure.repositories.ConnectionsReaderSqliteRepository;

import modules.mongo.domain.enums.MongoKeyEnum;
import modules.mongo.domain.enums.MongoMessageEnum;
import modules.mongo.infrastructure.components.MongoDocumentFormatter;
import modules.mongo.infrastructure.factories.MongoRepositoryFactory;
import modules.mongo.infrastructure.repositories.AbstractMongoWriterRepository;
import modules.mongo.domain.exceptions.MongoModuleException;

/**
 * Inserta, reemplaza (por _id) o borra (por _id) un documento desde el navegador de MongoDB. En una conexion de
 * produccion pide confirmacion (428), como la rejilla editable de SQL.
 */
public final class SaveMongoDocumentService {

    private SaveMongoDocumentService() {
    }

    public static SaveMongoDocumentService getInstance() {
        return new SaveMongoDocumentService();
    }

    /**
     * @throws MongoModuleException 404 si el documento a reemplazar o borrar ya no esta
     * @throws modules.connections.domain.exceptions.ConnectionsException
     */
    public SaveMongoDocumentResultDto invoke(SaveMongoDocumentDto saveMongoDocumentDto) {
        failIfWrongInput(saveMongoDocumentDto);
        MongoDocumentFormatter mongoDocumentFormatter = MongoDocumentFormatter.getInstance();
        Document document;
        try {
            document = mongoDocumentFormatter.getParsedDocument(saveMongoDocumentDto.getDocumentJson());
        }
        catch (JsonParseException e) {
            throw MongoModuleException.badRequestCustom(__(MongoMessageEnum.INVALID_JSON_X_X, "document", e.getMessage()));
        }
        if (!saveMongoDocumentDto.isNew() && !document.containsKey(MongoKeyEnum.ID)) {
            throw MongoModuleException.badRequestCustom(__(MongoMessageEnum.DOCUMENT_WITHOUT_ID));
        }
        failIfProductionNotConfirmed(saveMongoDocumentDto);

        String databaseName = saveMongoDocumentDto.getDatabaseName();
        String collectionName = saveMongoDocumentDto.getCollectionName();
        try {
            AbstractMongoWriterRepository mongoWriterRepository = MongoRepositoryFactory.getInstance().getWriterRepository(
                saveMongoDocumentDto.getConnectionUuid(), saveMongoDocumentDto.getSessionKey()
            );
            Object id;
            if (saveMongoDocumentDto.isNew()) {
                id = mongoWriterRepository.insert(databaseName, collectionName, document);
            }
            else {
                id = document.get(MongoKeyEnum.ID);
                long affected = saveMongoDocumentDto.isDelete()
                    ? mongoWriterRepository.deleteById(databaseName, collectionName, id)
                    : mongoWriterRepository.replaceById(databaseName, collectionName, document);
                if (affected == 0) {
                    throw MongoModuleException.notFoundCustom(__(MongoMessageEnum.DOCUMENT_X_NOT_FOUND, mongoDocumentFormatter.getValueText(id, false)));
                }
            }
            return SaveMongoDocumentResultDto.fromPrimitives(Primitives.of("idJson", mongoDocumentFormatter.getValueText(id, false)));
        }
        // red o tiempo agotado: el servidor no esta (503), no es un fallo de la peticion (400)
        catch (MongoSocketException | MongoTimeoutException e) {
            throw MongoModuleException.serviceUnavailableCustom(__(MongoMessageEnum.COULD_NOT_SAVE_X_X, databaseName + "." + collectionName, e.getMessage()));
        }
        catch (MongoException e) {
            throw MongoModuleException.badRequestCustom(__(MongoMessageEnum.COULD_NOT_SAVE_X_X, databaseName + "." + collectionName, e.getMessage()));
        }
    }

    /**
     * @throws MongoModuleException
     */
    private void failIfProductionNotConfirmed(SaveMongoDocumentDto saveMongoDocumentDto) {
        if (saveMongoDocumentDto.isConfirmed()) return;
        ConnectionEntity connectionEntity = ConnectionsReaderSqliteRepository.getInstance().getConnectionByUuid(saveMongoDocumentDto.getConnectionUuid());
        if (connectionEntity == null || !connectionEntity.isProduction()) return;
        String action = saveMongoDocumentDto.isDelete() ? "delete" : saveMongoDocumentDto.isNew() ? "insert" : "replace";
        throw MongoModuleException.preconditionRequiredCustom(__(
            MongoMessageEnum.PRODUCTION_X_CONFIRM_X, connectionEntity.getConnectionName(),
            action + " in " + saveMongoDocumentDto.getDatabaseName() + "." + saveMongoDocumentDto.getCollectionName()
        ));
    }

    /**
     * @throws MongoModuleException
     */
    private void failIfWrongInput(SaveMongoDocumentDto saveMongoDocumentDto) {
        if (saveMongoDocumentDto.getConnectionUuid().isEmpty() || saveMongoDocumentDto.getSessionKey().isEmpty()) {
            throw MongoModuleException.badRequestCustom(__(MongoMessageEnum.FIELD_IS_REQUIRED_X, "connectionUuid, sessionKey"));
        }
        if (saveMongoDocumentDto.getDatabaseName().isEmpty() || saveMongoDocumentDto.getCollectionName().isEmpty()) {
            throw MongoModuleException.badRequestCustom(__(MongoMessageEnum.FIELD_IS_REQUIRED_X, "databaseName, collectionName"));
        }
        if (saveMongoDocumentDto.isNew() && saveMongoDocumentDto.isDelete()) {
            throw MongoModuleException.badRequestCustom(__(MongoMessageEnum.NEW_AND_DELETE_TOGETHER));
        }
    }
}
