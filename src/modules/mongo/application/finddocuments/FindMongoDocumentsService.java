package modules.mongo.application.finddocuments;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.mongodb.MongoException;
import com.mongodb.MongoExecutionTimeoutException;
import com.mongodb.MongoSocketException;
import com.mongodb.MongoTimeoutException;

import org.bson.Document;
import org.bson.json.JsonParseException;

import modules.shared.application.Primitives;

import static core.config.Global.__;

import modules.mongo.domain.enums.MongoKeyEnum;
import modules.mongo.domain.enums.MongoMessageEnum;
import modules.mongo.infrastructure.components.MongoDocumentFormatter;
import modules.mongo.infrastructure.factories.MongoRepositoryFactory;
import modules.mongo.infrastructure.repositories.AbstractMongoReaderRepository;
import modules.mongo.domain.exceptions.MongoModuleException;

/**
 * Una pagina de documentos de una coleccion para el navegador: filtro y orden en JSON (como en mongosh), salto y
 * limite. Devuelve la tabla (un campo por columna) y cada documento en JSON, y cuantos cumplen el filtro. Hace red.
 */
public final class FindMongoDocumentsService {

    private FindMongoDocumentsService() {
    }

    public static FindMongoDocumentsService getInstance() {
        return new FindMongoDocumentsService();
    }

    /**
     * @throws MongoModuleException
     * @throws modules.connections.domain.exceptions.ConnectionsException
     */
    public FindMongoDocumentsResultDto invoke(FindMongoDocumentsDto findMongoDocumentsDto) {
        failIfWrongInput(findMongoDocumentsDto);
        MongoDocumentFormatter mongoDocumentFormatter = MongoDocumentFormatter.getInstance();
        Document filter = getParsed(mongoDocumentFormatter, findMongoDocumentsDto.getFilterJson(), "filter");
        Document sort = getParsed(mongoDocumentFormatter, findMongoDocumentsDto.getSortJson(), "sort");
        String databaseName = findMongoDocumentsDto.getDatabaseName();
        String collectionName = findMongoDocumentsDto.getCollectionName();
        try {
            AbstractMongoReaderRepository mongoReaderRepository = MongoRepositoryFactory.getInstance().getReaderRepository(
                findMongoDocumentsDto.getConnectionUuid(), findMongoDocumentsDto.getSessionKey()
            );
            long startNanos = System.nanoTime();
            List<Document> documents = mongoReaderRepository.findDocuments(
                databaseName, collectionName, filter, sort, findMongoDocumentsDto.getSkip(), findMongoDocumentsDto.getLimit()
            );
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
            long totalCount;
            try {
                totalCount = mongoReaderRepository.countDocuments(databaseName, collectionName, filter);
            }
            catch (MongoExecutionTimeoutException e) {
                totalCount = -1;
            }
            Map<String, Object> documentsTable = mongoDocumentFormatter.getTable(documents);
            return FindMongoDocumentsResultDto.fromPrimitives(Primitives.of(
                MongoKeyEnum.COLUMNS, documentsTable.get(MongoKeyEnum.COLUMNS),
                MongoKeyEnum.ROWS, documentsTable.get(MongoKeyEnum.ROWS),
                "documentsJson", documents.stream().map(mongoDocumentFormatter::getPrettyJson).toList(),
                "totalCount", totalCount,
                "elapsedMs", elapsedMs
            ));
        }
        // red o tiempo agotado: el servidor no esta (503), no es un fallo de la peticion (400)
        catch (MongoSocketException | MongoTimeoutException e) {
            throw MongoModuleException.serviceUnavailableCustom(__(MongoMessageEnum.COULD_NOT_READ_X_X, databaseName + "." + collectionName, e.getMessage()));
        }
        catch (MongoException e) {
            throw MongoModuleException.badRequestCustom(__(MongoMessageEnum.COULD_NOT_READ_X_X, databaseName + "." + collectionName, e.getMessage()));
        }
    }

    /**
     * @throws MongoModuleException
     */
    private Document getParsed(MongoDocumentFormatter mongoDocumentFormatter, String json, String fieldName) {
        try {
            return mongoDocumentFormatter.getParsedDocument(json);
        }
        catch (JsonParseException e) {
            throw MongoModuleException.badRequestCustom(__(MongoMessageEnum.INVALID_JSON_X_X, fieldName, e.getMessage()));
        }
    }

    /**
     * @throws MongoModuleException
     */
    private void failIfWrongInput(FindMongoDocumentsDto findMongoDocumentsDto) {
        if (findMongoDocumentsDto.getConnectionUuid().isEmpty() || findMongoDocumentsDto.getSessionKey().isEmpty()) {
            throw MongoModuleException.badRequestCustom(__(MongoMessageEnum.FIELD_IS_REQUIRED_X, "connectionUuid, sessionKey"));
        }
        if (findMongoDocumentsDto.getDatabaseName().isEmpty() || findMongoDocumentsDto.getCollectionName().isEmpty()) {
            throw MongoModuleException.badRequestCustom(__(MongoMessageEnum.FIELD_IS_REQUIRED_X, "databaseName, collectionName"));
        }
    }
}
