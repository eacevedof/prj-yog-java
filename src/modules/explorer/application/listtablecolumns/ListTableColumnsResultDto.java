package modules.explorer.application.listtablecolumns;

import java.util.List;
import java.util.Map;

public final class ListTableColumnsResultDto {

    private final List<Map<String, Object>> columns;

    @SuppressWarnings("unchecked")
    private ListTableColumnsResultDto(Map<String, Object> primitives) {
        this.columns = List.copyOf((List<Map<String, Object>>) primitives.get("columns"));
    }

    public static ListTableColumnsResultDto fromPrimitives(Map<String, Object> primitives) {
        return new ListTableColumnsResultDto(primitives);
    }

    /**
     * Cada columna con las claves de ExplorerKeyEnum (column_name, column_type, is_nullable, column_key).
     */
    public List<Map<String, Object>> getColumns() {
        return columns;
    }
}
