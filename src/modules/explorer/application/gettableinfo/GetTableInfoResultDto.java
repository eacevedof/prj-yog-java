package modules.explorer.application.gettableinfo;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static modules.shared.application.Primitives.getBool;
import static modules.shared.application.Primitives.getTrimmedString;

public final class GetTableInfoResultDto {

    private final String databaseName;
    private final String tableName;
    private final boolean isView;
    private final String createStatement;
    private final List<Map<String, Object>> indexes;
    private final Map<String, Object> status;

    @SuppressWarnings("unchecked")
    private GetTableInfoResultDto(Map<String, Object> primitives) {
        this.databaseName = getTrimmedString(primitives.get("databaseName"));
        this.tableName = getTrimmedString(primitives.get("tableName"));
        this.isView = getBool(primitives.get("isView"));
        this.createStatement = getTrimmedString(primitives.get("createStatement"));
        this.indexes = List.copyOf((List<Map<String, Object>>) primitives.get("indexes"));
        this.status = Map.copyOf(withoutNulls((Map<String, Object>) primitives.get("status")));
    }

    public static GetTableInfoResultDto fromPrimitives(Map<String, Object> primitives) {
        return new GetTableInfoResultDto(primitives);
    }

    private Map<String, Object> withoutNulls(Map<String, Object> map) {
        Map<String, Object> copy = new LinkedHashMap<>();
        map.forEach((key, value) -> {
            if (value != null) copy.put(key, value);
        });
        return copy;
    }

    public String getDatabaseName() {
        return databaseName;
    }

    public String getTableName() {
        return tableName;
    }

    public boolean isView() {
        return isView;
    }

    public String getCreateStatement() {
        return createStatement;
    }

    /**
     * Filas de information_schema.STATISTICS: index_name, non_unique, seq_in_index, column_name, index_type.
     */
    public List<Map<String, Object>> getIndexes() {
        return indexes;
    }

    /**
     * Fila de information_schema.TABLES (engine, table_rows, data_length...), sin las columnas a NULL.
     */
    public Map<String, Object> getStatus() {
        return status;
    }
}
