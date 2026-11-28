package modules.history.application.recordqueryhistory;

import java.util.Map;

import static modules.shared.application.Primitives.getBool;
import static modules.shared.application.Primitives.getInt;
import static modules.shared.application.Primitives.getTrimmedString;

public final class RecordQueryHistoryDto {

    private final String connectionUuid;
    private final String databaseName;
    private final String sqlQuery;
    private final long elapsedMs;
    private final int rowsCount;
    private final boolean hasError;

    private RecordQueryHistoryDto(Map<String, Object> primitives) {
        this.connectionUuid = getTrimmedString(primitives.get("connectionUuid"));
        this.databaseName = getTrimmedString(primitives.get("databaseName"));
        this.sqlQuery = getTrimmedString(primitives.get("sqlQuery"));
        this.elapsedMs = primitives.get("elapsedMs") instanceof Number number ? number.longValue() : 0L;
        this.rowsCount = getInt(primitives.get("rowsCount"));
        this.hasError = getBool(primitives.get("hasError"));
    }

    public static RecordQueryHistoryDto fromPrimitives(Map<String, Object> primitives) {
        return new RecordQueryHistoryDto(primitives);
    }

    public String getConnectionUuid() {
        return connectionUuid;
    }

    /**
     * "" = sin BD activa (se guarda NULL).
     */
    public String getDatabaseName() {
        return databaseName;
    }

    public String getSqlQuery() {
        return sqlQuery;
    }

    public long getElapsedMs() {
        return elapsedMs;
    }

    /**
     * Filas devueltas mas afectadas.
     */
    public int getRowsCount() {
        return rowsCount;
    }

    public boolean hasError() {
        return hasError;
    }
}
