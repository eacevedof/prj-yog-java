package modules.explorer.application.createdatabase;

import java.util.Map;

import static modules.shared.application.Primitives.getTrimmedString;

public final class CreateDatabaseResultDto {

    private final String databaseName;
    private final String createSql;

    private CreateDatabaseResultDto(Map<String, Object> primitives) {
        this.databaseName = getTrimmedString(primitives.get("databaseName"));
        this.createSql = getTrimmedString(primitives.get("createSql"));
    }

    public static CreateDatabaseResultDto fromPrimitives(Map<String, Object> primitives) {
        return new CreateDatabaseResultDto(primitives);
    }

    public String getDatabaseName() {
        return databaseName;
    }

    public String getCreateSql() {
        return createSql;
    }
}
