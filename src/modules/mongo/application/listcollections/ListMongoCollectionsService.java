package modules.mongo.application.listcollections;

import com.mongodb.MongoException;
import com.mongodb.MongoSocketException;
import com.mongodb.MongoTimeoutException;

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
        failIfWrongInput(listMongoCollectionsDto);

        try {
            return ListMongoCollectionsResultDto.fromPrimitives(Primitives.of(
                "collections",
                MongoRepositoryFactory.getInstance().getReaderRepository(
                    listMongoCollectionsDto.getConnectionUuid(), listMongoCollectionsDto.getSessionKey()
                ).getCollections(listMongoCollectionsDto.getDatabaseName())
            ));
        }
        // red o tiempo agotado: el servidor no esta (503); un error del servidor (sin permisos, Unauthorized 13) es de la
        // peticion (400), como en el resto del modulo
        catch (MongoSocketException | MongoTimeoutException e) {
            throw MongoModuleException.serviceUnavailableCustom(__(MongoMessageEnum.COULD_NOT_READ_X_X, listMongoCollectionsDto.getDatabaseName(), e.getMessage()));
        }
        catch (MongoException e) {
            throw MongoModuleException.badRequestCustom(__(MongoMessageEnum.COULD_NOT_READ_X_X, listMongoCollectionsDto.getDatabaseName(), e.getMessage()));
        }
    }

    /**
     * @throws MongoModuleException
     */
    private void failIfWrongInput(ListMongoCollectionsDto listMongoCollectionsDto) {
        if (listMongoCollectionsDto.getConnectionUuid().isEmpty() || listMongoCollectionsDto.getSessionKey().isEmpty()
            || listMongoCollectionsDto.getDatabaseName().isEmpty()) {
            throw MongoModuleException.badRequestCustom(__(MongoMessageEnum.FIELD_IS_REQUIRED_X, "connectionUuid, sessionKey, databaseName"));
        }
    }
}
