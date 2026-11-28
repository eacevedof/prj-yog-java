package modules.explorer.application.dropdatabase;

import java.util.Map;

import static modules.shared.application.Primitives.getTrimmedString;

public final class DropDatabaseResultDto {

    private final String databaseName;
    private final String dropSql;

    private DropDatabaseResultDto(Map<String, Object> primitives) {
        this.databaseName = getTrimmedString(primitives.get("databaseName"));
        this.dropSql = getTrimmedString(primitives.get("dropSql"));
    }

    public static DropDatabaseResultDto fromPrimitives(Map<String, Object> primitives) {
        return new DropDatabaseResultDto(primitives);
    }

    public String getDatabaseName() {
        return databaseName;
    }

    public String getDropSql() {
        return dropSql;
    }
}
