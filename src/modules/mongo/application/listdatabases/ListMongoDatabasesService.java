package modules.mongo.application.listdatabases;

import com.mongodb.MongoException;
import com.mongodb.MongoSocketException;
import com.mongodb.MongoTimeoutException;

import modules.shared.application.Primitives;

import static core.config.Global.__;

import modules.connections.domain.entities.ConnectionEntity;
import modules.connections.infrastructure.repositories.ConnectionsReaderSqliteRepository;

import modules.mongo.domain.enums.MongoMessageEnum;
import modules.mongo.infrastructure.factories.MongoRepositoryFactory;
import modules.mongo.domain.exceptions.MongoModuleException;

/**
 * Las BDs del arbol de MongoDB (sin permiso para listarlas, la de la conexion). Hace red.
 */
public final class ListMongoDatabasesService {

    private ListMongoDatabasesService() {
    }

    public static ListMongoDatabasesService getInstance() {
        return new ListMongoDatabasesService();
    }

    /**
     * @throws MongoModuleException
     * @throws modules.connections.domain.exceptions.ConnectionsException
     */
    public ListMongoDatabasesResultDto invoke(ListMongoDatabasesDto listMongoDatabasesDto) {
        failIfWrongInput(listMongoDatabasesDto);

        ConnectionEntity connectionEntity = ConnectionsReaderSqliteRepository.getInstance().getConnectionByUuid(listMongoDatabasesDto.getConnectionUuid());
        String defaultDatabase = connectionEntity == null ? "" : connectionEntity.getDatabaseName();
        try {
            return ListMongoDatabasesResultDto.fromPrimitives(Primitives.of(
                "databaseNames",
                MongoRepositoryFactory.getInstance().getReaderRepository(
                    listMongoDatabasesDto.getConnectionUuid(), listMongoDatabasesDto.getSessionKey()
                ).getDatabaseNames(defaultDatabase)
            ));
        }
        // red o tiempo agotado: el servidor no esta (503); un error del servidor (sin permisos, Unauthorized 13) es de la
        // peticion (400), como en el resto del modulo
        catch (MongoSocketException | MongoTimeoutException e) {
            throw MongoModuleException.serviceUnavailableCustom(__(MongoMessageEnum.COULD_NOT_READ_X_X, "databases", e.getMessage()));
        }
        catch (MongoException e) {
            throw MongoModuleException.badRequestCustom(__(MongoMessageEnum.COULD_NOT_READ_X_X, "databases", e.getMessage()));
        }
    }

    /**
     * @throws MongoModuleException
     */
    private void failIfWrongInput(ListMongoDatabasesDto listMongoDatabasesDto) {
        if (listMongoDatabasesDto.getConnectionUuid().isEmpty() || listMongoDatabasesDto.getSessionKey().isEmpty()) {
            throw MongoModuleException.badRequestCustom(__(MongoMessageEnum.FIELD_IS_REQUIRED_X, "connectionUuid, sessionKey"));
        }
    }
}
