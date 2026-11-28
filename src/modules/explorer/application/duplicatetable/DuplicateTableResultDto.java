package modules.explorer.application.duplicatetable;

import java.util.Map;

import static modules.shared.application.Primitives.getTrimmedString;

public final class DuplicateTableResultDto {

    private final String copyTableName;

    private DuplicateTableResultDto(Map<String, Object> primitives) {
        this.copyTableName = getTrimmedString(primitives.get("copyTableName"));
    }

    public static DuplicateTableResultDto fromPrimitives(Map<String, Object> primitives) {
        return new DuplicateTableResultDto(primitives);
    }

    public String getCopyTableName() {
        return copyTableName;
    }
}
