package modules.mongo.infrastructure.components;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bson.Document;
import org.bson.json.JsonMode;
import org.bson.json.JsonWriterSettings;

/**
 * Como se ensenan y se leen los documentos. Para ver y editar, JSON con la sintaxis de mongosh (ObjectId("..."),
 * ISODate("..."), NumberLong(...)): asi un documento vuelve a la BD con los mismos tipos al guardarlo. En la tabla, cada
 * campo de primer nivel en una columna (_id la primera) y su valor compacto.
 */
public final class MongoDocumentFormatter {

    private static final JsonWriterSettings PRETTY_SETTINGS = JsonWriterSettings.builder().outputMode(JsonMode.SHELL).indent(true).build();
    private static final JsonWriterSettings COMPACT_SETTINGS = JsonWriterSettings.builder().outputMode(JsonMode.SHELL).build();
    private static final String ID_FIELD = "_id";
    private static final String VALUE_FIELD = "v";

    private MongoDocumentFormatter() {
    }

    public static MongoDocumentFormatter getInstance() {
        return new MongoDocumentFormatter();
    }

    public String getPrettyJson(Document document) {
        return document.toJson(PRETTY_SETTINGS);
    }

    /**
     * Cualquier valor (documento, lista, numero, cadena...) como JSON de mongosh; una cadena, sin comillas si compact.
     */
    public String getValueText(Object value, boolean isPretty) {
        if (value == null) return "null";
        if (value instanceof Document document) return document.toJson(isPretty ? PRETTY_SETTINGS : COMPACT_SETTINGS);
        String wrapped = new Document(VALUE_FIELD, value).toJson(isPretty ? PRETTY_SETTINGS : COMPACT_SETTINGS);
        // {"v": <valor>} → <valor>
        int colon = wrapped.indexOf(':');
        String inner = wrapped.substring(colon + 1, wrapped.lastIndexOf('}')).strip();
        return isPretty ? inner.replace("\n  ", "\n") : inner;
    }

    /**
     * Un argumento de la consola o un filtro: JSON relajado (claves sin comillas, ObjectId(), ISODate()...) a su valor
     * Java (Document, List, Integer, String...).
     * @throws org.bson.json.JsonParseException si no es JSON valido
     */
    public Object getParsedValue(String json) {
        return Document.parse("{\"" + VALUE_FIELD + "\": " + json + "}").get(VALUE_FIELD);
    }

    /**
     * @throws org.bson.json.JsonParseException si no es un documento JSON valido
     */
    public Document getParsedDocument(String json) {
        return json == null || json.isBlank() ? new Document() : Document.parse(json);
    }

    /**
     * [columns, rows]: las columnas, todos los campos de primer nivel en orden de aparicion (_id primero); las celdas,
     * las cadenas tal cual y lo demas como JSON compacto; "" si el documento no tiene ese campo.
     */
    public Map<String, Object> getTable(List<Document> documents) {
        Set<String> columns = new LinkedHashSet<>();
        columns.add(ID_FIELD);
        for (Document document : documents) columns.addAll(document.keySet());
        List<List<String>> rows = new ArrayList<>();
        for (Document document : documents) {
            List<String> row = new ArrayList<>(columns.size());
            for (String column : columns) {
                if (!document.containsKey(column)) {
                    row.add("");
                    continue;
                }
                Object value = document.get(column);
                row.add(value instanceof String text ? text : getValueText(value, false));
            }
            rows.add(row);
        }
        return Map.of("columns", List.copyOf(columns), "rows", rows);
    }
}
