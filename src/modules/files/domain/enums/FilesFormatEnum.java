package modules.files.domain.enums;

import java.util.List;
import java.util.Map;

/**
 * Como esta escrito cada fichero de la carpeta: se lee y se vuelve a escribir igual.
 */
public final class FilesFormatEnum {

    public static final String CSV = "csv";
    public static final String JSON_ARRAY = "json-array";
    public static final String JSON_LINES = "json-lines";

    public static final String EXTENSION_CSV = "csv";
    public static final String EXTENSION_TSV = "tsv";
    public static final String EXTENSION_JSON = "json";
    public static final String EXTENSION_JSONL = "jsonl";
    public static final String EXTENSION_NDJSON = "ndjson";

    /**
     * Por la extension; un .json que trae un objeto por linea se lee como JSON Lines (se decide al leerlo).
     */
    public static final Map<String, String> FORMATS_BY_EXTENSION = Map.of(
        EXTENSION_CSV, CSV,
        EXTENSION_TSV, CSV,
        EXTENSION_JSON, JSON_ARRAY,
        EXTENSION_JSONL, JSON_LINES,
        EXTENSION_NDJSON, JSON_LINES
    );

    public static final List<String> JSON_FORMATS = List.of(JSON_ARRAY, JSON_LINES);

    private FilesFormatEnum() {
    }
}
