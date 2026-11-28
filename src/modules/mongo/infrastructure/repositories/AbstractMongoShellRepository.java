package modules.mongo.infrastructure.repositories;

import java.util.ArrayList;
import java.util.List;

import org.bson.Document;

import static core.config.Global.__;

import modules.connections.domain.enums.DbEngineEnum;

import modules.mongo.domain.entities.MongoShellCommandEntity;
import modules.mongo.infrastructure.components.MongoDocumentFormatter;

/**
 * La consola de MongoDB: una orden ya partida por MongoShellParser y su resultado como texto, con el formato de mongosh.
 * Por el driver (MongoShellRepository) o por el tunel HTTP (MongoShellHttpTunnelRepository), que la pasa a runCommand.
 * Aqui lo comun: leer los argumentos JSON y dar formato al resultado.
 */
public abstract class AbstractMongoShellRepository {

    public static final int DEFAULT_FIND_LIMIT = 50;
    protected static final int MAX_RESULTS = 1000;
    protected static final String ADMIN_DATABASE = DbEngineEnum.MONGODB_ADMIN_DATABASE;

    protected final MongoDocumentFormatter mongoDocumentFormatter = MongoDocumentFormatter.getInstance();

    /**
     * El texto del resultado. Un USE no va al servidor: lo resuelve el servicio.
     * @throws com.mongodb.MongoException con un error del servidor
     * @throws org.bson.json.JsonParseException con un argumento que no es JSON
     * @throws IllegalArgumentException con un metodo no soportado o argumentos que no encajan (mensaje = el metodo)
     */
    public abstract String execute(MongoShellCommandEntity command, String databaseName);

    protected String getDocumentsText(List<Document> documents) {
        if (documents.isEmpty()) return __("(sin documentos)");
        List<String> texts = new ArrayList<>();
        for (Document document : documents.subList(0, Math.min(documents.size(), MAX_RESULTS))) texts.add(mongoDocumentFormatter.getPrettyJson(document));
        return String.join("\n", texts) + "\n" + __("({0} documento(s))", documents.size());
    }

    protected String getResultText(Document result) {
        return mongoDocumentFormatter.getPrettyJson(result);
    }

    /**
     * El argumento index como documento; si no va, uno vacio (find() = todos).
     */
    protected Document getDocument(List<String> arguments, int index) {
        if (index >= arguments.size() || arguments.get(index).isBlank()) return new Document();
        Object value = mongoDocumentFormatter.getParsedValue(arguments.get(index));
        if (value instanceof Document document) return document;
        throw new IllegalArgumentException("document:" + arguments.get(index));
    }

    @SuppressWarnings("unchecked")
    protected List<Document> getDocumentList(List<String> arguments, int index, String method) {
        Object value = mongoDocumentFormatter.getParsedValue(getArgument(arguments, index, method));
        if (value instanceof List<?> list && list.stream().allMatch(Document.class::isInstance)) return (List<Document>) list;
        throw new IllegalArgumentException("method:" + method);
    }

    protected String getString(List<String> arguments, int index, String method) {
        Object value = mongoDocumentFormatter.getParsedValue(getArgument(arguments, index, method));
        if (value instanceof String text) return text;
        throw new IllegalArgumentException("method:" + method);
    }

    protected String getArgument(List<String> arguments, int index, String method) {
        if (index >= arguments.size() || arguments.get(index).isBlank()) throw new IllegalArgumentException("method:" + method);
        return arguments.get(index);
    }
}
