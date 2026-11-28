package modules.files.infrastructure.components;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.bson.BSONException;
import org.bson.BsonArray;
import org.bson.BsonBoolean;
import org.bson.BsonDocument;
import org.bson.BsonDouble;
import org.bson.BsonInt64;
import org.bson.BsonNull;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.bson.json.JsonMode;
import org.bson.json.JsonParseException;
import org.bson.json.JsonWriterSettings;

import modules.files.domain.enums.FilesColumnKindEnum;
import modules.files.domain.enums.FilesConfigEnum;
import modules.files.domain.enums.FilesFormatEnum;
import modules.files.domain.entities.FileTableEntity;
import modules.files.domain.services.ColumnNamesBuilder;
import modules.files.domain.services.TextLayoutDetector;

/**
 * JSON &lt;-&gt; tabla, envolviendo el lector y escritor JSON de BSON (ya viene con el driver de MongoDB). Un fichero es
 * una lista de objetos: un array (.json) o un objeto por linea (JSON Lines). Las columnas son todas las claves, en el
 * orden en que aparecen; los objetos y listas anidados van como texto JSON en su celda y vuelven a ser JSON al guardar.
 * Un error de sintaxis sale como JsonLineParseException, con la linea del objeto que falla.
 */
public final class JsonTableConverter {

    private static final String ARRAY_START = "[";
    private static final String INDENT = "    ";
    private static final Pattern LONG_PATTERN = Pattern.compile("-?(0|[1-9]\\d{0,17})");
    private static final Pattern DOUBLE_PATTERN = Pattern.compile("-?\\d+(\\.\\d+)?([eE][-+]?\\d+)?");

    private JsonTableConverter() {
    }

    public static JsonTableConverter getInstance() {
        return new JsonTableConverter();
    }

    /**
     * null si el JSON no es una lista de objetos.
     * @throws JsonLineParseException si no es JSON valido (con la linea)
     */
    public FileTableEntity getTable(String text, String format) {
        TextLayoutDetector textLayoutDetector = TextLayoutDetector.getInstance();
        String untrimmedBody = textLayoutDetector.getBody(text);
        String body = untrimmedBody.trim();
        // las lineas en blanco del principio cuentan: el numero de linea de un error es el del fichero
        int firstLineNumber = getLineNumber(untrimmedBody, untrimmedBody.indexOf(body), 1);
        boolean isArray = body.startsWith(ARRAY_START) || (body.isEmpty() && FilesFormatEnum.JSON_ARRAY.equals(format));
        List<BsonDocument> documents = isArray ? getArrayDocuments(body, firstLineNumber) : getLineDocuments(body, firstLineNumber);
        if (documents == null) return null;

        Map<String, List<BsonValue>> valuesByKey = new LinkedHashMap<>();
        for (BsonDocument document : documents) {
            for (String key : document.keySet()) valuesByKey.computeIfAbsent(key, newKey -> new ArrayList<>());
        }
        for (BsonDocument document : documents) {
            for (Map.Entry<String, List<BsonValue>> keyValues : valuesByKey.entrySet()) {
                keyValues.getValue().add(document.get(keyValues.getKey()));
            }
        }
        List<String> keys = new ArrayList<>(valuesByKey.keySet());
        List<String> columnKinds = new ArrayList<>();
        for (List<BsonValue> values : valuesByKey.values()) columnKinds.add(getKind(values));

        List<String> columnNames = ColumnNamesBuilder.getInstance().getColumnNames(keys);
        List<String> explicitNullColumns = new ArrayList<>();
        List<List<Object>> rows = new ArrayList<>();
        for (BsonDocument document : documents) {
            List<Object> row = new ArrayList<>();
            for (int columnIndex = 0; columnIndex < keys.size(); columnIndex++) {
                BsonValue value = document.get(keys.get(columnIndex));
                boolean isExplicitNull = value != null && value.isNull();
                if (isExplicitNull && !explicitNullColumns.contains(columnNames.get(columnIndex))) explicitNullColumns.add(columnNames.get(columnIndex));
                row.add(getSqlValue(value, columnKinds.get(columnIndex)));
            }
            rows.add(row);
        }
        return new FileTableEntity(
            isArray ? FilesFormatEnum.JSON_ARRAY : FilesFormatEnum.JSON_LINES, "", textLayoutDetector.getLineSeparator(text), textLayoutDetector.hasBom(text),
            columnNames, columnKinds, explicitNullColumns, rows
        );
    }

    /**
     * La tabla de vuelta a JSON en su formato: el array con un objeto por bloque indentado; JSON Lines, uno por linea.
     */
    public String getText(FileTableEntity fileTableEntity) {
        String lineSeparator = fileTableEntity.lineSeparator();
        boolean isArray = FilesFormatEnum.JSON_ARRAY.equals(fileTableEntity.format());
        JsonWriterSettings jsonWriterSettings = JsonWriterSettings.builder()
            .outputMode(JsonMode.RELAXED)
            .indent(isArray)
            .indentCharacters(INDENT)
            .newLineCharacters(lineSeparator)
            .build();
        List<String> documentTexts = new ArrayList<>();
        for (List<Object> row : fileTableEntity.rows()) {
            BsonDocument document = new BsonDocument();
            for (int columnIndex = 0; columnIndex < fileTableEntity.columnNames().size(); columnIndex++) {
                String columnName = fileTableEntity.columnNames().get(columnIndex);
                Object value = columnIndex < row.size() ? row.get(columnIndex) : null;
                if (value == null && !fileTableEntity.explicitNullColumns().contains(columnName)) continue;
                document.put(columnName, getBsonValue(value, fileTableEntity.columnKinds().get(columnIndex)));
            }
            documentTexts.add(document.toJson(jsonWriterSettings));
        }
        StringBuilder text = new StringBuilder();
        if (fileTableEntity.hasBom()) text.append(FilesConfigEnum.UTF8_BOM);
        if (!isArray) {
            for (String documentText : documentTexts) text.append(documentText).append(lineSeparator);
            return text.toString();
        }
        if (documentTexts.isEmpty()) return text.append("[]").append(lineSeparator).toString();
        text.append("[").append(lineSeparator);
        for (int i = 0; i < documentTexts.size(); i++) {
            text.append(INDENT).append(documentTexts.get(i).replace(lineSeparator, lineSeparator + INDENT));
            text.append(i < documentTexts.size() - 1 ? "," : "").append(lineSeparator);
        }
        return text.append("]").append(lineSeparator).toString();
    }

    private List<BsonDocument> getArrayDocuments(String body, int firstLineNumber) {
        if (body.isEmpty()) return List.of();
        BsonArray bsonArray;
        try {
            bsonArray = BsonArray.parse(body);
        }
        catch (JsonParseException | BSONException e) {
            throw getLocatedException(body, firstLineNumber, e);
        }
        List<BsonDocument> documents = new ArrayList<>();
        for (BsonValue bsonValue : bsonArray) {
            if (!bsonValue.isDocument()) return null;
            documents.add(bsonValue.asDocument());
        }
        return documents;
    }

    private List<BsonDocument> getLineDocuments(String body, int firstLineNumber) {
        List<BsonDocument> documents = new ArrayList<>();
        String[] lines = body.split("\\R", -1);
        for (int lineIndex = 0; lineIndex < lines.length; lineIndex++) {
            String trimmedLine = lines[lineIndex].trim();
            if (trimmedLine.isEmpty()) continue;
            if (!trimmedLine.startsWith("{")) return null;
            try {
                documents.add(BsonDocument.parse(trimmedLine));
            }
            catch (JsonParseException | BSONException e) {
                throw new JsonLineParseException(firstLineNumber + lineIndex, e.getMessage(), e);
            }
        }
        return documents;
    }

    /**
     * El lector de BSON no dice donde falla: se recorren los elementos de primer nivel del array (respetando cadenas y
     * anidados) y se lee cada uno por separado; el primero que no se lee da la linea. Si todos se leen (array sin
     * cerrar, basura al final), la ultima linea.
     */
    private JsonLineParseException getLocatedException(String body, int firstLineNumber, RuntimeException parseException) {
        int depth = 0;
        int elementStart = -1;
        boolean isInString = false;
        boolean isEscaped = false;
        for (int charIndex = 1; charIndex < body.length(); charIndex++) {
            char character = body.charAt(charIndex);
            if (isInString) {
                if (isEscaped) isEscaped = false;
                else if (character == '\\') isEscaped = true;
                else if (character == '"') isInString = false;
                continue;
            }
            if (elementStart == -1 && !Character.isWhitespace(character) && character != ',' && character != ']') elementStart = charIndex;
            if (character == '"') isInString = true;
            else if (character == '{' || character == '[') depth++;
            else if (character == '}' || character == ']') depth--;
            boolean isElementEnd = elementStart != -1 && ((depth == 0 && character == ',') || depth < 0);
            if (!isElementEnd) continue;
            String elementText = body.substring(elementStart, charIndex);
            try {
                BsonArray.parse("[" + elementText + "]");
            }
            catch (JsonParseException | BSONException elementException) {
                return new JsonLineParseException(getLineNumber(body, elementStart, firstLineNumber), elementException.getMessage(), elementException);
            }
            if (depth < 0) break;
            elementStart = -1;
        }
        int failedAt = elementStart == -1 ? body.length() - 1 : elementStart;
        return new JsonLineParseException(getLineNumber(body, failedAt, firstLineNumber), parseException.getMessage(), parseException);
    }

    private int getLineNumber(String text, int charIndex, int firstLineNumber) {
        int lineNumber = firstLineNumber;
        for (int index = 0; index < charIndex && index < text.length(); index++) {
            if (text.charAt(index) == '\n') lineNumber++;
        }
        return lineNumber;
    }

    /**
     * Entera si todos los valores lo son, real si son numeros, booleana, JSON si son objetos o listas; si se mezclan
     * tipos, texto. Los null y las claves que faltan no cuentan.
     */
    private String getKind(List<BsonValue> values) {
        String kind = null;
        for (BsonValue value : values) {
            if (value == null || value.isNull()) continue;
            String valueKind = getValueKind(value);
            if (kind == null) {
                kind = valueKind;
                continue;
            }
            if (kind.equals(valueKind)) continue;
            boolean isNumberMix = List.of(FilesColumnKindEnum.INTEGER, FilesColumnKindEnum.REAL).containsAll(List.of(kind, valueKind));
            kind = isNumberMix ? FilesColumnKindEnum.REAL : FilesColumnKindEnum.STRING;
        }
        return kind == null ? FilesColumnKindEnum.STRING : kind;
    }

    private String getValueKind(BsonValue value) {
        if (value.isInt32() || value.isInt64()) return FilesColumnKindEnum.INTEGER;
        if (value.isDouble() || value.isDecimal128()) return FilesColumnKindEnum.REAL;
        if (value.isBoolean()) return FilesColumnKindEnum.BOOLEAN;
        if (value.isDocument() || value.isArray()) return FilesColumnKindEnum.JSON;
        return FilesColumnKindEnum.STRING;
    }

    private Object getSqlValue(BsonValue value, String kind) {
        if (value == null || value.isNull()) return null;
        if (value.isInt32()) return (long) value.asInt32().getValue();
        if (value.isInt64()) return value.asInt64().getValue();
        if (value.isDouble()) return value.asDouble().getValue();
        if (value.isDecimal128()) return value.asDecimal128().getValue().bigDecimalValue().doubleValue();
        if (value.isBoolean()) return FilesColumnKindEnum.BOOLEAN.equals(kind) ? (Object) (value.asBoolean().getValue() ? 1L : 0L) : String.valueOf(value.asBoolean().getValue());
        if (value.isString()) return value.asString().getValue();
        if (value.isDocument()) return value.asDocument().toJson(JsonWriterSettings.builder().outputMode(JsonMode.RELAXED).build());
        if (value.isArray()) return getArrayJson(value.asArray());
        return value.toString();
    }

    private String getArrayJson(BsonArray bsonArray) {
        String wrapped = new BsonDocument("a", bsonArray).toJson(JsonWriterSettings.builder().outputMode(JsonMode.RELAXED).build());
        return wrapped.substring(wrapped.indexOf('['), wrapped.lastIndexOf(']') + 1);
    }

    /**
     * Lo que haya en la celda, al tipo de su columna; si no encaja (texto en una columna entera), como texto.
     */
    private BsonValue getBsonValue(Object value, String kind) {
        if (value == null) return BsonNull.VALUE;
        String text = String.valueOf(value);
        return switch (kind) {
            case FilesColumnKindEnum.INTEGER -> value instanceof Number number && !(value instanceof Double) ? new BsonInt64(number.longValue()) : getNumberOrString(text);
            case FilesColumnKindEnum.REAL -> value instanceof Double doubleValue ? new BsonDouble(doubleValue) : getNumberOrString(text);
            case FilesColumnKindEnum.BOOLEAN -> getBooleanOrString(value, text);
            case FilesColumnKindEnum.JSON -> getJsonOrString(text);
            default -> value instanceof Number ? getNumberOrString(text) : new BsonString(text);
        };
    }

    private BsonValue getNumberOrString(String text) {
        if (LONG_PATTERN.matcher(text).matches()) return new BsonInt64(Long.parseLong(text));
        if (DOUBLE_PATTERN.matcher(text).matches()) return new BsonDouble(Double.parseDouble(text));
        return new BsonString(text);
    }

    private BsonValue getBooleanOrString(Object value, String text) {
        if (value instanceof Number number) return BsonBoolean.valueOf(number.longValue() != 0);
        if ("true".equalsIgnoreCase(text) || "false".equalsIgnoreCase(text)) return BsonBoolean.valueOf(Boolean.parseBoolean(text));
        return new BsonString(text);
    }

    private BsonValue getJsonOrString(String text) {
        String trimmedText = text.trim();
        try {
            if (trimmedText.startsWith("{")) return BsonDocument.parse(trimmedText);
            if (trimmedText.startsWith("[")) return BsonArray.parse(trimmedText);
        }
        catch (JsonParseException e) {
            // lo que el usuario escribio no es JSON: se guarda como texto, sin perderlo
        }
        return new BsonString(text);
    }
}
