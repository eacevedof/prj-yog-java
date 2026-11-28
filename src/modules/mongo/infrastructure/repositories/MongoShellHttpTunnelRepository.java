package modules.mongo.infrastructure.repositories;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bson.Document;
import org.bson.types.ObjectId;

import modules.connections.infrastructure.components.httptunnel.HttpTunnelClient;

import modules.mongo.domain.entities.MongoShellCommandEntity;

/**
 * La consola de MongoDB por el tunel HTTP: cada orden de mongosh (ya partida por MongoShellParser) se pasa a su
 * documento de db.runCommand (contrato sqlyog-tunnel-multidbs §5: find, aggregate, insert, update, delete, count...)
 * y el resultado sale con el mismo texto que por el driver (MongoShellRepository).
 */
public final class MongoShellHttpTunnelRepository extends AbstractMongoShellRepository {

    private final MongoHttpTunnelCommands mongoHttpTunnelCommands;

    private MongoShellHttpTunnelRepository(HttpTunnelClient httpTunnelClient) {
        this.mongoHttpTunnelCommands = MongoHttpTunnelCommands.getInstance(httpTunnelClient);
    }

    public static MongoShellHttpTunnelRepository getInstance(HttpTunnelClient httpTunnelClient) {
        return new MongoShellHttpTunnelRepository(httpTunnelClient);
    }

    @Override
    public String execute(MongoShellCommandEntity command, String databaseName) {
        return switch (command.type()) {
            case MongoShellCommandEntity.SHOW_DATABASES -> getShowDatabasesText();
            case MongoShellCommandEntity.SHOW_COLLECTIONS -> {
                List<String> names = new ArrayList<>();
                for (Document collection : mongoHttpTunnelCommands.getDocuments(new Document("listCollections", 1).append("nameOnly", true), databaseName)) {
                    names.add(collection.getString("name"));
                }
                yield String.join("\n", names);
            }
            case MongoShellCommandEntity.DROP_DATABASE -> {
                mongoHttpTunnelCommands.getResponse(new Document("dropDatabase", 1), databaseName);
                yield getResultText(new Document("ok", 1).append("dropped", databaseName));
            }
            case MongoShellCommandEntity.RUN_COMMAND -> {
                String targetDatabase = "adminCommand".equals(command.name()) ? ADMIN_DATABASE : databaseName;
                yield getResultText(mongoHttpTunnelCommands.getResponse(getDocument(command.arguments(), 0), targetDatabase));
            }
            case MongoShellCommandEntity.COLLECTION_METHOD -> executeCollectionMethod(command, databaseName);
            default -> throw new IllegalArgumentException(command.type());
        };
    }

    private String executeCollectionMethod(MongoShellCommandEntity command, String databaseName) {
        List<String> arguments = command.arguments();
        String collection = command.name();
        return switch (command.method()) {
            case "find" -> getDocumentsText(getDocuments(getFind(command), databaseName));
            case "findOne" -> {
                Document find = new Document("find", collection).append("filter", getDocument(arguments, 0)).append("limit", 1);
                Document projection = getDocument(arguments, 1);
                if (!projection.isEmpty()) find.append("projection", projection);
                List<Document> documents = getDocuments(find, databaseName);
                yield documents.isEmpty() ? "null" : mongoDocumentFormatter.getPrettyJson(documents.getFirst());
            }
            case "countDocuments" -> String.valueOf(getN(getResponse(new Document("count", collection).append("query", getDocument(arguments, 0)), databaseName)));
            case "estimatedDocumentCount" -> String.valueOf(getN(getResponse(new Document("count", collection), databaseName)));
            case "distinct" -> {
                Document distinct = new Document("distinct", collection)
                    .append("key", getString(arguments, 0, command.method()))
                    .append("query", getDocument(arguments, 1));
                yield mongoDocumentFormatter.getValueText(getResponse(distinct, databaseName).getList("values", Object.class, List.of()), true);
            }
            case "aggregate" -> getDocumentsText(getDocuments(
                new Document("aggregate", collection).append("pipeline", getDocumentList(arguments, 0, command.method())).append("cursor", new Document()),
                databaseName
            ));
            case "insertOne" -> {
                Document document = getWithId(getDocument(arguments, 0));
                getResponse(new Document("insert", collection).append("documents", List.of(document)), databaseName);
                yield getResultText(new Document("acknowledged", true).append("insertedId", document.get("_id")));
            }
            case "insertMany" -> {
                List<Document> documents = new ArrayList<>();
                for (Document document : getDocumentList(arguments, 0, command.method())) documents.add(getWithId(document));
                getResponse(new Document("insert", collection).append("documents", documents), databaseName);
                yield getResultText(new Document("acknowledged", true).append("insertedIds", documents.stream().map(document -> document.get("_id")).toList()));
            }
            case "updateOne" -> getUpdateText(collection, getDocument(arguments, 0), getOperatorsUpdate(getDocument(arguments, 1)), false, databaseName);
            case "updateMany" -> getUpdateText(collection, getDocument(arguments, 0), getOperatorsUpdate(getDocument(arguments, 1)), true, databaseName);
            case "replaceOne" -> getUpdateText(collection, getDocument(arguments, 0), getDocument(arguments, 1), false, databaseName);
            case "deleteOne" -> getDeleteText(collection, getDocument(arguments, 0), 1, databaseName);
            case "deleteMany" -> getDeleteText(collection, getDocument(arguments, 0), 0, databaseName);
            case "getIndexes" -> getDocumentsText(getDocuments(new Document("listIndexes", collection), databaseName));
            case "createIndex" -> {
                Document keys = getDocument(arguments, 0);
                Document options = getDocument(arguments, 1);
                String name = options.getString("name") != null ? options.getString("name") : getDefaultIndexName(keys);
                Document index = new Document("key", keys).append("name", name);
                if (options.getBoolean("unique", false)) index.append("unique", true);
                getResponse(new Document("createIndexes", collection).append("indexes", List.of(index)), databaseName);
                yield "\"" + name + "\"";
            }
            case "dropIndex" -> {
                Object index = mongoDocumentFormatter.getParsedValue(getArgument(arguments, 0, command.method()));
                getResponse(new Document("dropIndexes", collection).append("index", index instanceof Document keys ? keys : String.valueOf(index)), databaseName);
                yield getResultText(new Document("ok", 1));
            }
            case "drop" -> {
                getResponse(new Document("drop", collection), databaseName);
                yield "true";
            }
            default -> throw new IllegalArgumentException("method:" + command.method());
        };
    }

    /**
     * find(filtro, proyeccion) con .sort() .skip() .limit() .projection(); sin limit, los primeros DEFAULT_FIND_LIMIT.
     */
    private Document getFind(MongoShellCommandEntity command) {
        Document find = new Document("find", command.name()).append("filter", getDocument(command.arguments(), 0));
        Document projection = getDocument(command.arguments(), 1);
        if (!projection.isEmpty()) find.append("projection", projection);
        int limit = DEFAULT_FIND_LIMIT;
        for (List<String> modifier : command.modifiers()) {
            String argument = modifier.get(1);
            switch (modifier.getFirst()) {
                case "sort" -> find.append("sort", mongoDocumentFormatter.getParsedDocument(argument));
                case "skip" -> find.append("skip", Integer.parseInt(argument.trim()));
                case "limit" -> limit = Math.min(Integer.parseInt(argument.trim()), MAX_RESULTS);
                case "projection" -> find.append("projection", mongoDocumentFormatter.getParsedDocument(argument));
                default -> throw new IllegalArgumentException("modifier:" + modifier.getFirst());
            }
        }
        return find.append("limit", limit);
    }

    /**
     * updateOne/updateMany/replaceOne: la respuesta de update trae n (encontrados), nModified y upserted.
     */
    private String getUpdateText(String collection, Document filter, Document update, boolean isMulti, String databaseName) {
        Document statement = new Document("q", filter).append("u", update);
        if (isMulti) statement.append("multi", true);
        Document response = getResponse(new Document("update", collection).append("updates", List.of(statement)), databaseName);
        Document result = new Document("acknowledged", true)
            .append("matchedCount", getN(response))
            .append("modifiedCount", response.get("nModified") instanceof Number number ? number.longValue() : 0L);
        List<Document> upserted = response.getList("upserted", Document.class, List.of());
        if (!upserted.isEmpty()) result.append("upsertedId", upserted.getFirst().get("_id"));
        return getResultText(result);
    }

    /**
     * updateOne/updateMany solo con operadores ($set, $inc...), como el driver (MongoShellRepository): sin esto, el
     * servidor tomaria un documento sin $ como reemplazo. Mismos mensajes que el UpdateFieldNameValidator del driver.
     */
    private Document getOperatorsUpdate(Document update) {
        if (update.isEmpty()) throw new IllegalArgumentException("Invalid BSON document for an update. The document may not be empty.");
        for (String fieldName : update.keySet()) {
            if (!fieldName.startsWith("$")) {
                throw new IllegalArgumentException(String.format("All update operators must start with '$', but '%s' does not", fieldName));
            }
        }
        return update;
    }

    /**
     * limit 1 = deleteOne; 0 = todos los que cumplen el filtro.
     */
    private String getDeleteText(String collection, Document filter, int limit, String databaseName) {
        Document response = getResponse(new Document("delete", collection).append("deletes", List.of(new Document("q", filter).append("limit", limit))), databaseName);
        return getResultText(new Document("acknowledged", true).append("deletedCount", getN(response)));
    }

    private String getShowDatabasesText() {
        List<String> lines = new ArrayList<>();
        for (Document database : getResponse(new Document("listDatabases", 1), ADMIN_DATABASE).getList("databases", Document.class, List.of())) {
            long sizeOnDisk = database.get("sizeOnDisk") instanceof Number number ? number.longValue() : 0;
            lines.add(String.format("%-20s %10.2f KiB", database.getString("name"), sizeOnDisk / 1024.0));
        }
        return String.join("\n", lines);
    }

    /**
     * El nombre que pone el driver a un indice sin nombre: campo_1_otro_-1.
     */
    private String getDefaultIndexName(Document keys) {
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, Object> entry : keys.entrySet()) parts.add(entry.getKey() + "_" + entry.getValue());
        return String.join("_", parts);
    }

    /**
     * Como el driver: un documento sin _id recibe un ObjectId nuevo antes de insertarse.
     */
    private Document getWithId(Document document) {
        if (!document.containsKey("_id")) {
            Document withId = new Document("_id", new ObjectId());
            withId.putAll(document);
            return withId;
        }
        return document;
    }

    private List<Document> getDocuments(Document command, String databaseName) {
        return mongoHttpTunnelCommands.getDocuments(command, databaseName);
    }

    private Document getResponse(Document command, String databaseName) {
        return mongoHttpTunnelCommands.getResponse(command, databaseName);
    }

    private long getN(Document response) {
        return response.get("n") instanceof Number number ? number.longValue() : 0;
    }
}
