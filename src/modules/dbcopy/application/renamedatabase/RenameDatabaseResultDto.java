package modules.dbcopy.application.renamedatabase;

import java.util.Map;

import static modules.shared.application.Primitives.getBool;
import static modules.shared.application.Primitives.getTrimmedString;

public final class RenameDatabaseResultDto {

    private final String newDatabaseName;
    private final String filePath;
    private final int objectCount;
    private final long rowCount;
    private final boolean isOriginalDropped;
    private final long elapsedMs;

    private RenameDatabaseResultDto(Map<String, Object> primitives) {
        this.newDatabaseName = getTrimmedString(primitives.get("newDatabaseName"));
        this.filePath = getTrimmedString(primitives.get("filePath"));
        this.objectCount = ((Number) primitives.getOrDefault("objectCount", 0)).intValue();
        this.rowCount = ((Number) primitives.getOrDefault("rowCount", 0L)).longValue();
        this.isOriginalDropped = getBool(primitives.get("isOriginalDropped"));
        this.elapsedMs = ((Number) primitives.getOrDefault("elapsedMs", 0L)).longValue();
    }

    public static RenameDatabaseResultDto fromPrimitives(Map<String, Object> primitives) {
        return new RenameDatabaseResultDto(primitives);
    }

    public String getNewDatabaseName() {
        return newDatabaseName;
    }

    /**
     * El volcado de la original: se queda como copia de seguridad.
     */
    public String getFilePath() {
        return filePath;
    }

    public int getObjectCount() {
        return objectCount;
    }

    public long getRowCount() {
        return rowCount;
    }

    public boolean isOriginalDropped() {
        return isOriginalDropped;
    }

    public long getElapsedMs() {
        return elapsedMs;
    }
}
