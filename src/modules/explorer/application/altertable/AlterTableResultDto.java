package modules.explorer.application.altertable;

import java.util.List;
import java.util.Map;

import static modules.shared.application.Primitives.getTrimmedString;

/**
 * Las sentencias ejecutadas y el nombre con el que queda la tabla.
 */
public final class AlterTableResultDto {

    private final List<String> statements;
    private final String tableName;

    @SuppressWarnings("unchecked")
    private AlterTableResultDto(Map<String, Object> primitives) {
        this.statements = primitives.get("statements") instanceof List<?> rawStatements ? (List<String>) rawStatements : List.of();
        this.tableName = getTrimmedString(primitives.get("tableName"));
    }

    public static AlterTableResultDto fromPrimitives(Map<String, Object> primitives) {
        return new AlterTableResultDto(primitives);
    }

    public List<String> getStatements() {
        return statements;
    }

    public String getTableName() {
        return tableName;
    }
}
