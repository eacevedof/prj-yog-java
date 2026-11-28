package modules.mongo.infrastructure.repositories;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;

import org.bson.Document;

import modules.mongo.domain.enums.MongoKeyEnum;

/**
 * Los cambios del navegador de MongoDB, siempre por _id: guardar (reemplazar) un documento, insertar uno nuevo y
 * borrar uno.
 */
public final class MongoWriterRepository extends AbstractMongoWriterRepository {

    private final MongoClient mongoClient;

    private MongoWriterRepository(MongoClient mongoClient) {
        this.mongoClient = mongoClient;
    }

    public static MongoWriterRepository getInstance(MongoClient mongoClient) {
        return new MongoWriterRepository(mongoClient);
    }

    /**
     * Reemplaza el documento con ese _id. Devuelve los encontrados (0 = ya no existe).
     */
    @Override
    public long replaceById(String databaseName, String collectionName, Document document) {
        UpdateResult updateResult = getCollection(databaseName, collectionName).replaceOne(Filters.eq(MongoKeyEnum.ID, document.get(MongoKeyEnum.ID)), document);
        return updateResult.getMatchedCount();
    }

    /**
     * Inserta el documento; si no trae _id se le pone un ObjectId nuevo. Devuelve el _id.
     */
    @Override
    public Object insert(String databaseName, String collectionName, Document document) {
        Document documentWithId = getWithId(document);
        getCollection(databaseName, collectionName).insertOne(documentWithId);
        return documentWithId.get(MongoKeyEnum.ID);
    }

    @Override
    public long deleteById(String databaseName, String collectionName, Object id) {
        DeleteResult deleteResult = getCollection(databaseName, collectionName).deleteOne(Filters.eq(MongoKeyEnum.ID, id));
        return deleteResult.getDeletedCount();
    }

    private MongoCollection<Document> getCollection(String databaseName, String collectionName) {
        return mongoClient.getDatabase(databaseName).getCollection(collectionName);
    }
}
