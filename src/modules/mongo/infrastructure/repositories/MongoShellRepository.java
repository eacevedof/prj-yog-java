package modules.mongo.infrastructure.repositories;

import java.util.ArrayList;
import java.util.List;

import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.InsertManyResult;
import com.mongodb.client.result.InsertOneResult;
import com.mongodb.client.result.UpdateResult;

import org.bson.Document;

import modules.mongo.domain.entities.MongoShellCommandEntity;

/**
 * Ejecuta una orden de la consola de MongoDB (ya partida por MongoShellParser) y devuelve su resultado como texto, con
 * el formato de mongosh. Los argumentos JSON los interpreta aqui el driver.
 */
public final class MongoShellRepository extends AbstractMongoShellRepository {

    private final MongoClient mongoClient;

    private MongoShellRepository(MongoClient mongoClient) {
        this.mongoClient = mongoClient;
    }

    public static MongoShellRepository getInstance(MongoClient mongoClient) {
        return new MongoShellRepository(mongoClient);
    }

    @Override
    public String execute(MongoShellCommandEntity command, String databaseName) {
        MongoDatabase database = mongoClient.getDatabase(databaseName);
        return switch (command.type()) {
            case MongoShellCommandEntity.SHOW_DATABASES -> getShowDatabasesText();
            case MongoShellCommandEntity.SHOW_COLLECTIONS -> String.join("\n", database.listCollectionNames().into(new ArrayList<>()));
            case MongoShellCommandEntity.DROP_DATABASE -> {
                database.drop();
                yield mongoDocumentFormatter.getPrettyJson(new Document("ok", 1).append("dropped", databaseName));
            }
            case MongoShellCommandEntity.RUN_COMMAND -> {
                MongoDatabase target = "adminCommand".equals(command.name()) ? mongoClient.getDatabase(ADMIN_DATABASE) : database;
                yield mongoDocumentFormatter.getPrettyJson(target.runCommand(getDocument(command.arguments(), 0)));
            }
            case MongoShellCommandEntity.COLLECTION_METHOD -> executeCollectionMethod(database.getCollection(command.name()), command);
            default -> throw new IllegalArgumentException(command.type());
        };
    }

    private String executeCollectionMethod(MongoCollection<Document> collection, MongoShellCommandEntity command) {
        List<String> arguments = command.arguments();
        return switch (command.method()) {
            case "find" -> getDocumentsText(getFind(collection, command));
            case "findOne" -> {
                Document document = collection.find(getDocument(arguments, 0)).projection(getDocument(arguments, 1)).first();
                yield document == null ? "null" : mongoDocumentFormatter.getPrettyJson(document);
            }
            case "countDocuments" -> String.valueOf(collection.countDocuments(getDocument(arguments, 0)));
            case "estimatedDocumentCount" -> String.valueOf(collection.estimatedDocumentCount());
            case "distinct" -> {
                List<Object> values = new ArrayList<>();
                collection.distinct(getString(arguments, 0, command.method()), getDocument(arguments, 1), Object.class).into(values);
                yield mongoDocumentFormatter.getValueText(values, true);
            }
            case "aggregate" -> getDocumentsText(collection.aggregate(getDocumentList(arguments, 0, command.method())).into(new ArrayList<>()));
            case "insertOne" -> {
                InsertOneResult insertOneResult = collection.insertOne(getDocument(arguments, 0));
                yield getResultText(new Document("acknowledged", true).append("insertedId", insertOneResult.getInsertedId()));
            }
            case "insertMany" -> {
                InsertManyResult insertManyResult = collection.insertMany(getDocumentList(arguments, 0, command.method()));
                yield getResultText(new Document("acknowledged", true).append("insertedIds", new ArrayList<>(insertManyResult.getInsertedIds().values())));
            }
            case "updateOne" -> getUpdateText(collection.updateOne(getDocument(arguments, 0), getDocument(arguments, 1)));
            case "updateMany" -> getUpdateText(collection.updateMany(getDocument(arguments, 0), getDocument(arguments, 1)));
            case "replaceOne" -> getUpdateText(collection.replaceOne(getDocument(arguments, 0), getDocument(arguments, 1)));
            case "deleteOne" -> getDeleteText(collection.deleteOne(getDocument(arguments, 0)));
            case "deleteMany" -> getDeleteText(collection.deleteMany(getDocument(arguments, 0)));
            case "getIndexes" -> getDocumentsText(collection.listIndexes().into(new ArrayList<>()));
            case "createIndex" -> {
                Document options = getDocument(arguments, 1);
                IndexOptions indexOptions = new IndexOptions().unique(options.getBoolean("unique", false));
                if (options.getString("name") != null) indexOptions.name(options.getString("name"));
                yield "\"" + collection.createIndex(getDocument(arguments, 0), indexOptions) + "\"";
            }
            case "dropIndex" -> {
                Object index = mongoDocumentFormatter.getParsedValue(getArgument(arguments, 0, command.method()));
                if (index instanceof Document keys) collection.dropIndex(keys);
                else collection.dropIndex(String.valueOf(index));
                yield getResultText(new Document("ok", 1));
            }
            case "drop" -> {
                collection.drop();
                yield "true";
            }
            default -> throw new IllegalArgumentException("method:" + command.method());
        };
    }

    /**
     * find(filtro, proyeccion) con .sort() .skip() .limit() .projection(); sin limit, los primeros DEFAULT_FIND_LIMIT.
     */
    private List<Document> getFind(MongoCollection<Document> collection, MongoShellCommandEntity command) {
        FindIterable<Document> findIterable = collection.find(getDocument(command.arguments(), 0)).projection(getDocument(command.arguments(), 1));
        int limit = DEFAULT_FIND_LIMIT;
        for (List<String> modifier : command.modifiers()) {
            String argument = modifier.get(1);
            switch (modifier.getFirst()) {
                case "sort" -> findIterable.sort(mongoDocumentFormatter.getParsedDocument(argument));
                case "skip" -> findIterable.skip(Integer.parseInt(argument.trim()));
                case "limit" -> limit = Math.min(Integer.parseInt(argument.trim()), MAX_RESULTS);
                case "projection" -> findIterable.projection(mongoDocumentFormatter.getParsedDocument(argument));
                default -> throw new IllegalArgumentException("modifier:" + modifier.getFirst());
            }
        }
        return findIterable.limit(limit).into(new ArrayList<>());
    }

    private String getShowDatabasesText() {
        List<String> lines = new ArrayList<>();
        for (Document database : mongoClient.listDatabases()) {
            long sizeOnDisk = database.get("sizeOnDisk") instanceof Number number ? number.longValue() : 0;
            lines.add(String.format("%-20s %10.2f KiB", database.getString("name"), sizeOnDisk / 1024.0));
        }
        return String.join("\n", lines);
    }

    private String getUpdateText(UpdateResult updateResult) {
        Document result = new Document("acknowledged", updateResult.wasAcknowledged())
            .append("matchedCount", updateResult.getMatchedCount())
            .append("modifiedCount", updateResult.getModifiedCount());
        if (updateResult.getUpsertedId() != null) result.append("upsertedId", updateResult.getUpsertedId());
        return getResultText(result);
    }

    private String getDeleteText(DeleteResult deleteResult) {
        return getResultText(new Document("acknowledged", deleteResult.wasAcknowledged()).append("deletedCount", deleteResult.getDeletedCount()));
    }
}
