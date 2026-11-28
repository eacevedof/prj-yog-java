package modules.mongo.infrastructure.repositories;

import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.util.ArrayList;
import java.util.List;

import com.mongodb.MongoCommandException;
import com.mongodb.MongoException;
import com.mongodb.MongoExecutionTimeoutException;
import com.mongodb.MongoSocketException;
import com.mongodb.ServerAddress;

import org.bson.BsonDocument;
import org.bson.BsonDouble;
import org.bson.BsonInt32;
import org.bson.BsonString;
import org.bson.Document;
import org.bson.json.JsonMode;
import org.bson.json.JsonWriterSettings;

import modules.connections.infrastructure.components.httptunnel.HttpTunnelClient;
import modules.connections.infrastructure.components.httptunnel.HttpTunnelResult;

/**
 * Lo comun a los repositorios de MongoDB por el tunel HTTP (contrato sqlyog-tunnel-multidbs §5): cada orden va como un
 * documento de db.runCommand en Extended JSON (el nombre del comando, primera clave) y vuelve en la columna "document",
 * un documento por fila: los del cursor (find, aggregate, listCollections...) o la respuesta del comando.
 * <p>
 * EXCEPCION DOCUMENTADA a «los repositorios no capturan» (DDD 18): el SQLException del tunel se traduce aqui a la
 * familia del driver, para que los servicios de MongoDB traten igual las dos vias sin cambiar (error del servidor =
 * MongoCommandException con su codigo; 50 = MongoExecutionTimeoutException; red, HTTP o destino no permitido =
 * MongoSocketException).
 */
final class MongoHttpTunnelCommands {

    private static final String ADMIN_DATABASE = "admin";
    private static final int MAX_TIME_EXPIRED_CODE = 50;
    // 2003: destino fuera de YOG_ALLOWED_HOSTS o sin conexion con MongoDB
    private static final int CONNECTION_ERROR_CODE = 2003;
    private static final JsonWriterSettings EXTENDED_JSON = JsonWriterSettings.builder().outputMode(JsonMode.EXTENDED).build();

    private final HttpTunnelClient httpTunnelClient;

    private MongoHttpTunnelCommands(HttpTunnelClient httpTunnelClient) {
        this.httpTunnelClient = httpTunnelClient;
    }

    static MongoHttpTunnelCommands getInstance(HttpTunnelClient httpTunnelClient) {
        return new MongoHttpTunnelCommands(httpTunnelClient);
    }

    /**
     * Los documentos de un comando en esa BD (vacia = admin).
     */
    List<Document> getDocuments(Document command, String databaseName) {
        return getDocuments(getResult(command.toJson(EXTENDED_JSON), databaseName, false).getFirst());
    }

    /**
     * La respuesta de un comando sin cursor (una fila); un documento vacio si no da ninguna.
     */
    Document getResponse(Document command, String databaseName) {
        List<Document> documents = getDocuments(command, databaseName);
        Document response = documents.isEmpty() ? new Document() : documents.getFirst();
        failIfWriteErrors(response);
        return response;
    }

    /**
     * insert, update y delete contestan ok: 1 aunque una escritura falle (clave duplicada, validacion): el fallo va en
     * writeErrors. El driver lo convierte en excepcion; aqui igual, con el primero.
     */
    private void failIfWriteErrors(Document response) {
        List<Document> writeErrors = response.getList("writeErrors", Document.class, List.of());
        if (writeErrors.isEmpty()) return;
        Document firstError = writeErrors.getFirst();
        int code = firstError.get("code") instanceof Number number ? number.intValue() : 0;
        BsonDocument errorResponse = new BsonDocument("ok", new BsonDouble(0))
            .append("code", new BsonInt32(code))
            .append("errmsg", new BsonString(String.valueOf(firstError.get("errmsg"))));
        throw new MongoCommandException(errorResponse, new ServerAddress());
    }

    /**
     * Varios comandos en una peticion (array JSON): los documentos de cada uno, en orden.
     */
    List<List<Document>> getBatchDocuments(List<Document> commands, String databaseName) {
        if (commands.isEmpty()) return List.of();
        List<String> jsons = new ArrayList<>();
        for (Document command : commands) jsons.add(command.toJson(EXTENDED_JSON));
        List<List<Document>> batch = new ArrayList<>();
        for (HttpTunnelResult result : getResult("[" + String.join(",", jsons) + "]", databaseName, true)) batch.add(getDocuments(result));
        return batch;
    }

    private List<HttpTunnelResult> getResult(String query, String databaseName, boolean isBatch) {
        try {
            return httpTunnelClient.execute(query, databaseName == null || databaseName.isEmpty() ? ADMIN_DATABASE : databaseName, isBatch);
        }
        catch (SQLException e) {
            throw getMongoException(e);
        }
    }

    private List<Document> getDocuments(HttpTunnelResult result) {
        List<Document> documents = new ArrayList<>();
        for (List<byte[]> row : result.rows()) {
            String json = row.isEmpty() ? null : HttpTunnelResult.getText(row.getFirst());
            if (json != null && !json.isBlank()) documents.add(Document.parse(json));
        }
        return documents;
    }

    private MongoException getMongoException(SQLException e) {
        String message = e.getMessage() == null ? "tunnel error" : e.getMessage();
        if (e instanceof SQLTimeoutException || e.getErrorCode() == CONNECTION_ERROR_CODE || message.startsWith("tunnel:")) {
            return new MongoSocketException(message, new ServerAddress());
        }
        if (e.getErrorCode() == MAX_TIME_EXPIRED_CODE) return new MongoExecutionTimeoutException(e.getErrorCode(), message);
        BsonDocument response = new BsonDocument("ok", new BsonDouble(0))
            .append("code", new BsonInt32(e.getErrorCode()))
            .append("errmsg", new BsonString(message));
        return new MongoCommandException(response, new ServerAddress());
    }
}
