package modules.explorer.application.gettabledefinition;

import java.util.List;
import java.util.Map;

import modules.explorer.domain.entities.TableDefinitionEntity;

/**
 * La tabla tal como esta y lo que ofrecen los desplegables de Avanzado (motores y cotejamientos del servidor).
 */
public final class GetTableDefinitionResultDto {

    private final TableDefinitionEntity tableDefinition;
    private final List<String> engines;
    private final List<String> collations;

    @SuppressWarnings("unchecked")
    private GetTableDefinitionResultDto(Map<String, Object> primitives) {
        this.tableDefinition = (TableDefinitionEntity) primitives.get("tableDefinition");
        this.engines = primitives.get("engines") instanceof List<?> rawEngines ? (List<String>) rawEngines : List.of();
        this.collations = primitives.get("collations") instanceof List<?> rawCollations ? (List<String>) rawCollations : List.of();
    }

    public static GetTableDefinitionResultDto fromPrimitives(Map<String, Object> primitives) {
        return new GetTableDefinitionResultDto(primitives);
    }

    public TableDefinitionEntity getTableDefinition() {
        return tableDefinition;
    }

    public List<String> getEngines() {
        return engines;
    }

    public List<String> getCollations() {
        return collations;
    }
}
