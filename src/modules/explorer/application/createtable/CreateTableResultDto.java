package modules.explorer.application.createtable;

import java.util.List;
import java.util.Map;

import static modules.shared.application.Primitives.getTrimmedString;

/**
 * La sentencia ejecutada y el nombre de la tabla creada.
 */
public final class CreateTableResultDto {

    private final List<String> statements;
    private final String tableName;

    @SuppressWarnings("unchecked")
    private CreateTableResultDto(Map<String, Object> primitives) {
        this.statements = primitives.get("statements") instanceof List<?> rawStatements ? List.copyOf((List<String>) rawStatements) : List.of();
        this.tableName = getTrimmedString(primitives.get("tableName"));
    }

    public static CreateTableResultDto fromPrimitives(Map<String, Object> primitives) {
        return new CreateTableResultDto(primitives);
    }

    public List<String> getStatements() {
        return statements;
    }

    public String getTableName() {
        return tableName;
    }
}
