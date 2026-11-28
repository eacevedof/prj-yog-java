package modules.explorer.application.droptable;

import java.util.Map;

import static modules.shared.application.Primitives.getTrimmedString;

public final class DropTableResultDto {

    private final String dropSql;

    private DropTableResultDto(Map<String, Object> primitives) {
        this.dropSql = getTrimmedString(primitives.get("dropSql"));
    }

    public static DropTableResultDto fromPrimitives(Map<String, Object> primitives) {
        return new DropTableResultDto(primitives);
    }

    public String getDropSql() {
        return dropSql;
    }
}
