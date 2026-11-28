package modules.mongo.infrastructure.repositories;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.mongodb.MongoCommandException;

import org.bson.Document;

import modules.shared.application.Primitives;

import modules.connections.infrastructure.components.httptunnel.HttpTunnelClient;

/**
 * El navegador de MongoDB por el tunel HTTP: lo mismo que MongoReaderRepository, con documentos de runCommand. Los
 * recuentos de todas las colecciones van en un lote (una peticion).
 */
public final class MongoReaderHttpTunnelRepository extends AbstractMongoReaderRepository {

    private static final long COUNT_MAX_TIME_MS = 5000;

    private final MongoHttpTunnelCommands mongoHttpTunnelCommands;

    private MongoReaderHttpTunnelRepository(HttpTunnelClient httpTunnelClient) {
        this.mongoHttpTunnelCommands = MongoHttpTunnelCommands.getInstance(httpTunnelClient);
    }

    public static MongoReaderHttpTunnelRepository getInstance(HttpTunnelClient httpTunnelClient) {
        return new MongoReaderHttpTunnelRepository(httpTunnelClient);
    }

    @Override
    public List<String> getDatabaseNames(String defaultDatabase) {
        try {
            Document response = mongoHttpTunnelCommands.getResponse(new Document("listDatabases", 1).append("nameOnly", true), "");
            List<String> databaseNames = new ArrayList<>();
            for (Document database : response.getList("databases", Document.class, List.of())) databaseNames.add(database.getString("name"));
            return databaseNames;
        }
        catch (MongoCommandException e) {
            if (defaultDatabase.isEmpty()) throw e;
            return List.of(defaultDatabase);
        }
    }

    /**
     * listCollections y, en un lote, el count (por metadatos, sin filtro) de cada coleccion; una vista sale con -1.
     */
    @Override
    public List<Map<String, Object>> getCollections(String databaseName) {
        List<String> collectionNames = new ArrayList<>();
        List<String> viewNames = new ArrayList<>();
        for (Document collection : mongoHttpTunnelCommands.getDocuments(new Document("listCollections", 1), databaseName)) {
            String name = collection.getString("name");
            if ("view".equals(collection.getString("type"))) viewNames.add(name);
            else collectionNames.add(name);
        }
        collectionNames.sort(String::compareTo);
        List<Document> countCommands = new ArrayList<>();
        for (String collectionName : collectionNames) countCommands.add(new Document("count", collectionName));
        List<List<Document>> counts = mongoHttpTunnelCommands.getBatchDocuments(countCommands, databaseName);
        List<Map<String, Object>> collections = new ArrayList<>();
        for (int i = 0; i < collectionNames.size(); i++) {
            List<Document> countResponse = i < counts.size() ? counts.get(i) : List.of();
            long count = countResponse.isEmpty() ? -1 : getLong(countResponse.getFirst().get("n"));
            collections.add(Primitives.of("name", collectionNames.get(i), "count", count));
        }
        for (String viewName : viewNames) collections.add(Primitives.of("name", viewName, "count", -1L));
        collections.sort((left, right) -> String.valueOf(left.get("name")).compareTo(String.valueOf(right.get("name"))));
        return collections;
    }

    @Override
    public List<Document> findDocuments(String databaseName, String collectionName, Document filter, Document sort, int skip, int limit) {
        Document find = new Document("find", collectionName).append("filter", filter);
        if (!sort.isEmpty()) find.append("sort", sort);
        if (skip > 0) find.append("skip", skip);
        if (limit > 0) find.append("limit", limit);
        return mongoHttpTunnelCommands.getDocuments(find, databaseName);
    }

    @Override
    public long countDocuments(String databaseName, String collectionName, Document filter) {
        Document count = new Document("count", collectionName).append("query", filter).append("maxTimeMS", COUNT_MAX_TIME_MS);
        return getLong(mongoHttpTunnelCommands.getResponse(count, databaseName).get("n"));
    }

    private long getLong(Object value) {
        return value instanceof Number number ? number.longValue() : 0;
    }
}
