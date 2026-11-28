package modules.explorer.application.getcreatetableoptions;

import java.util.List;
import java.util.Map;

import static modules.shared.application.Primitives.getTrimmedString;

/**
 * Lo que ofrecen los desplegables de Avanzado al crear una tabla: motores y cotejamientos del servidor.
 */
public final class GetCreateTableOptionsResultDto {

    private final String dbEngine;
    private final List<String> engines;
    private final List<String> collations;

    @SuppressWarnings("unchecked")
    private GetCreateTableOptionsResultDto(Map<String, Object> primitives) {
        this.dbEngine = getTrimmedString(primitives.get("dbEngine"));
        this.engines = primitives.get("engines") instanceof List<?> rawEngines ? List.copyOf((List<String>) rawEngines) : List.of();
        this.collations = primitives.get("collations") instanceof List<?> rawCollations ? List.copyOf((List<String>) rawCollations) : List.of();
    }

    public static GetCreateTableOptionsResultDto fromPrimitives(Map<String, Object> primitives) {
        return new GetCreateTableOptionsResultDto(primitives);
    }

    public String getDbEngine() {
        return dbEngine;
    }

    public List<String> getEngines() {
        return engines;
    }

    public List<String> getCollations() {
        return collations;
    }
}
