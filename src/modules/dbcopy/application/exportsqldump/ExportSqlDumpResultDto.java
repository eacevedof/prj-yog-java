package modules.dbcopy.application.exportsqldump;

import java.util.List;
import java.util.Map;

import static modules.shared.application.Primitives.getBool;
import static modules.shared.application.Primitives.getTrimmedString;

/**
 * El resumen del volcado: el fichero escrito, un resultado por objeto (DbCopyKeyEnum: tipo, nombre, ok, filas,
 * mensaje), los totales y si se paro antes de acabar.
 */
public final class ExportSqlDumpResultDto {

    private final String filePath;
    private final List<Map<String, Object>> objects;
    private final int okCount;
    private final int errorCount;
    private final long dumpedRows;
    private final boolean isCancelled;
    private final long elapsedMs;

    @SuppressWarnings("unchecked")
    private ExportSqlDumpResultDto(Map<String, Object> primitives) {
        this.filePath = getTrimmedString(primitives.get("filePath"));
        this.objects = primitives.get("objects") instanceof List<?> rawObjects ? (List<Map<String, Object>>) rawObjects : List.of();
        this.okCount = ((Number) primitives.getOrDefault("okCount", 0)).intValue();
        this.errorCount = ((Number) primitives.getOrDefault("errorCount", 0)).intValue();
        this.dumpedRows = ((Number) primitives.getOrDefault("dumpedRows", 0L)).longValue();
        this.isCancelled = getBool(primitives.get("isCancelled"));
        this.elapsedMs = ((Number) primitives.getOrDefault("elapsedMs", 0L)).longValue();
    }

    public static ExportSqlDumpResultDto fromPrimitives(Map<String, Object> primitives) {
        return new ExportSqlDumpResultDto(primitives);
    }

    public String getFilePath() {
        return filePath;
    }

    public List<Map<String, Object>> getObjects() {
        return objects;
    }

    public int getOkCount() {
        return okCount;
    }

    public int getErrorCount() {
        return errorCount;
    }

    public long getDumpedRows() {
        return dumpedRows;
    }

    public boolean isCancelled() {
        return isCancelled;
    }

    public long getElapsedMs() {
        return elapsedMs;
    }
}
