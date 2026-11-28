package modules.explorer.application.alterdatabase;

import java.util.Map;

import static modules.shared.application.Primitives.getTrimmedString;

public final class AlterDatabaseResultDto {

    private final String alterSql;

    private AlterDatabaseResultDto(Map<String, Object> primitives) {
        this.alterSql = getTrimmedString(primitives.get("alterSql"));
    }

    public static AlterDatabaseResultDto fromPrimitives(Map<String, Object> primitives) {
        return new AlterDatabaseResultDto(primitives);
    }

    public String getAlterSql() {
        return alterSql;
    }
}
