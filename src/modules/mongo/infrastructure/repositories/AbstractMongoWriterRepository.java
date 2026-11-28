package modules.mongo.infrastructure.repositories;

import org.bson.Document;
import org.bson.types.ObjectId;

/**
 * Los cambios del navegador de MongoDB por _id, por el driver (MongoWriterRepository) o por el tunel HTTP
 * (MongoWriterHttpTunnelRepository). Lo elige MongoRepositoryFactory segun la conexion.
 */
public abstract class AbstractMongoWriterRepository {

    protected static final String ID_FIELD = "_id";

    /**
     * Reemplaza el documento con ese _id. Devuelve los encontrados (0 = ya no existe).
     */
    public abstract long replaceById(String databaseName, String collectionName, Document document);

    /**
     * Inserta el documento; si no trae _id se le pone un ObjectId nuevo. Devuelve el _id.
     */
    public abstract Object insert(String databaseName, String collectionName, Document document);

    public abstract long deleteById(String databaseName, String collectionName, Object id);

    /**
     * El documento a insertar: el mismo si trae _id; si no, una copia con un ObjectId nuevo delante (como mongosh), sin
     * tocar el que llega.
     */
    protected Document getWithId(Document document) {
        if (document.containsKey(ID_FIELD)) return document;
        Document documentWithId = new Document(ID_FIELD, new ObjectId());
        documentWithId.putAll(document);
        return documentWithId;
    }
}
