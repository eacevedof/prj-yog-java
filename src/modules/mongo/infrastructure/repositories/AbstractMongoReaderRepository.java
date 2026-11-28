package modules.mongo.infrastructure.repositories;

import java.util.List;
import java.util.Map;

import org.bson.Document;

/**
 * Lo que lee el navegador de MongoDB, por el driver (directo o SSH: MongoReaderRepository) o por el tunel HTTP
 * (MongoReaderHttpTunnelRepository). Lo elige MongoRepositoryFactory segun la conexion; los servicios no lo saben.
 * Un error sube como MongoException en los dos.
 */
public abstract class AbstractMongoReaderRepository {

    /**
     * Las BDs que el usuario puede listar; sin permiso para listDatabases, la de la conexion.
     */
    public abstract List<String> getDatabaseNames(String defaultDatabase);

    /**
     * [name, count] de cada coleccion (y vista), por nombre; count estimado (-1 en una vista).
     */
    public abstract List<Map<String, Object>> getCollections(String databaseName);

    public abstract List<Document> findDocuments(String databaseName, String collectionName, Document filter, Document sort, int skip, int limit);

    public abstract long countDocuments(String databaseName, String collectionName, Document filter);
}
