package modules.mongo.infrastructure.repositories;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.mongodb.MongoCommandException;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.CountOptions;

import org.bson.Document;

import modules.shared.application.Primitives;

import modules.mongo.domain.enums.MongoConfigEnum;
import modules.mongo.domain.enums.MongoKeyEnum;

/**
 * Lo que lee el navegador de MongoDB: BDs, colecciones con su numero (estimado) de documentos y una pagina de
 * documentos con filtro, orden, salto y limite.
 */
public final class MongoReaderRepository extends AbstractMongoReaderRepository {

    private final MongoClient mongoClient;

    private MongoReaderRepository(MongoClient mongoClient) {
        this.mongoClient = mongoClient;
    }

    public static MongoReaderRepository getInstance(MongoClient mongoClient) {
        return new MongoReaderRepository(mongoClient);
    }

    /**
     * Las BDs que el usuario puede listar. Sin permiso para listDatabases (un usuario de una sola BD), la de la
     * conexion.
     */
    @Override
    public List<String> getDatabaseNames(String defaultDatabase) {
        try {
            List<String> databaseNames = new ArrayList<>();
            mongoClient.listDatabaseNames().into(databaseNames);
            return databaseNames;
        }
        catch (MongoCommandException e) {
            if (defaultDatabase.isEmpty()) throw e;
            return List.of(defaultDatabase);
        }
    }

    /**
     * [name, count] de cada coleccion (y vista), por nombre; count es el estimado (metadatos, instantaneo).
     */
    @Override
    public List<Map<String, Object>> getCollections(String databaseName) {
        MongoDatabase database = mongoClient.getDatabase(databaseName);
        List<String> collectionNames = new ArrayList<>();
        database.listCollectionNames().into(collectionNames);
        collectionNames.sort(String::compareTo);
        List<Map<String, Object>> collections = new ArrayList<>();
        for (String collectionName : collectionNames) {
            long count;
            try {
                count = database.getCollection(collectionName).estimatedDocumentCount();
            }
            catch (MongoCommandException e) {
                // una vista no tiene recuento por metadatos
                count = -1;
            }
            collections.add(Primitives.of(MongoKeyEnum.NAME, collectionName, MongoKeyEnum.COUNT, count));
        }
        return collections;
    }

    @Override
    public List<Document> findDocuments(String databaseName, String collectionName, Document filter, Document sort, int skip, int limit) {
        FindIterable<Document> findIterable = getCollection(databaseName, collectionName).find(filter).skip(skip).limit(limit);
        if (!sort.isEmpty()) findIterable.sort(sort);
        List<Document> documents = new ArrayList<>();
        findIterable.into(documents);
        return documents;
    }

    /**
     * Los que cumplen el filtro, con un tope de tiempo: en una coleccion enorme sin indice puede tardar.
     */
    @Override
    public long countDocuments(String databaseName, String collectionName, Document filter) {
        return getCollection(databaseName, collectionName).countDocuments(filter, new CountOptions().maxTime(MongoConfigEnum.COUNT_MAX_TIME_MS, TimeUnit.MILLISECONDS));
    }

    private MongoCollection<Document> getCollection(String databaseName, String collectionName) {
        return mongoClient.getDatabase(databaseName).getCollection(collectionName);
    }
}
