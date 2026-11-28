package modules.mongo.application.finddocuments;

import java.util.List;
import java.util.Map;

public final class FindMongoDocumentsResultDto {

    private final List<String> columns;
    private final List<List<String>> rows;
    private final List<String> documentsJson;
    private final long totalCount;
    private final long elapsedMs;

    @SuppressWarnings("unchecked")
    private FindMongoDocumentsResultDto(Map<String, Object> primitives) {
        this.columns = List.copyOf((List<String>) primitives.get("columns"));
        this.rows = List.copyOf((List<List<String>>) primitives.get("rows"));
        this.documentsJson = List.copyOf((List<String>) primitives.get("documentsJson"));
        this.totalCount = primitives.get("totalCount") instanceof Number number ? number.longValue() : -1;
        this.elapsedMs = primitives.get("elapsedMs") instanceof Number number ? number.longValue() : 0;
    }

    public static FindMongoDocumentsResultDto fromPrimitives(Map<String, Object> primitives) {
        return new FindMongoDocumentsResultDto(primitives);
    }

    /**
     * Los campos de primer nivel de los documentos leidos, _id primero.
     */
    public List<String> getColumns() {
        return columns;
    }

    public List<List<String>> getRows() {
        return rows;
    }

    /**
     * Cada documento entero como JSON de mongosh (para verlo y editarlo), en el orden de rows.
     */
    public List<String> getDocumentsJson() {
        return documentsJson;
    }

    /**
     * Los que cumplen el filtro; -1 si contarlos tardaba demasiado.
     */
    public long getTotalCount() {
        return totalCount;
    }

    public long getElapsedMs() {
        return elapsedMs;
    }
}
