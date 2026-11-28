package modules.mongo.application.listcollections;

import com.mongodb.MongoException;

import modules.shared.application.Primitives;

import static core.config.Global.__;

import modules.mongo.domain.enums.MongoMessageEnum;
import modules.mongo.infrastructure.factories.MongoRepositoryFactory;
import modules.mongo.domain.exceptions.MongoModuleException;

/**
 * Las colecciones de una BD de MongoDB con su numero estimado de documentos. Hace red.
 */
public final class ListMongoCollectionsService {

    private ListMongoCollectionsService() {
    }

    public static ListMongoCollectionsService getInstance() {
        return new ListMongoCollectionsService();
    }

    /**
     * @throws MongoModuleException
     * @throws modules.connections.domain.exceptions.ConnectionsException
     */
    public ListMongoCollectionsResultDto invoke(ListMongoCollectionsDto listMongoCollectionsDto) {
        if (listMongoCollectionsDto.getConnectionUuid().isEmpty() || listMongoCollectionsDto.getSessionKey().isEmpty()
            || listMongoCollectionsDto.getDatabaseName().isEmpty()) {
            throw MongoModuleException.badRequestCustom(__(MongoMessageEnum.FIELD_IS_REQUIRED_X, "connectionUuid, sessionKey, databaseName"));
        }
        try {
            return ListMongoCollectionsResultDto.fromPrimitives(Primitives.of(
                "collections",
                MongoRepositoryFactory.getInstance().getReaderRepository(
                    listMongoCollectionsDto.getConnectionUuid(), listMongoCollectionsDto.getSessionKey()
                ).getCollections(listMongoCollectionsDto.getDatabaseName())
            ));
        }
        catch (MongoException e) {
            throw MongoModuleException.serviceUnavailableCustom(__(MongoMessageEnum.COULD_NOT_READ_X_X, listMongoCollectionsDto.getDatabaseName(), e.getMessage()));
        }
    }
}
