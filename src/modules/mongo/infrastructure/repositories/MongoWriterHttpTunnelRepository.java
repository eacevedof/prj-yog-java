package modules.mongo.infrastructure.repositories;

import java.util.List;

import org.bson.Document;

import modules.connections.infrastructure.components.httptunnel.HttpTunnelClient;

import modules.mongo.domain.enums.MongoKeyEnum;

/**
 * Los cambios del navegador de MongoDB por el tunel HTTP, siempre por _id: update, insert y delete de runCommand.
 */
public final class MongoWriterHttpTunnelRepository extends AbstractMongoWriterRepository {

    private final MongoHttpTunnelCommands mongoHttpTunnelCommands;

    private MongoWriterHttpTunnelRepository(HttpTunnelClient httpTunnelClient) {
        this.mongoHttpTunnelCommands = MongoHttpTunnelCommands.getInstance(httpTunnelClient);
    }

    public static MongoWriterHttpTunnelRepository getInstance(HttpTunnelClient httpTunnelClient) {
        return new MongoWriterHttpTunnelRepository(httpTunnelClient);
    }

    /**
     * La n de la respuesta de update es la de encontrados (matchedCount en el driver).
     */
    @Override
    public long replaceById(String databaseName, String collectionName, Document document) {
        Document update = new Document("update", collectionName).append("updates", List.of(
            new Document("q", new Document(MongoKeyEnum.ID, document.get(MongoKeyEnum.ID))).append("u", document)
        ));
        return getLong(mongoHttpTunnelCommands.getResponse(update, databaseName).get("n"));
    }

    @Override
    public Object insert(String databaseName, String collectionName, Document document) {
        Document documentWithId = getWithId(document);
        mongoHttpTunnelCommands.getResponse(new Document("insert", collectionName).append("documents", List.of(documentWithId)), databaseName);
        return documentWithId.get(MongoKeyEnum.ID);
    }

    @Override
    public long deleteById(String databaseName, String collectionName, Object id) {
        Document delete = new Document("delete", collectionName).append("deletes", List.of(
            new Document("q", new Document(MongoKeyEnum.ID, id)).append("limit", 1)
        ));
        return getLong(mongoHttpTunnelCommands.getResponse(delete, databaseName).get("n"));
    }

    private long getLong(Object value) {
        return value instanceof Number number ? number.longValue() : 0;
    }
}
