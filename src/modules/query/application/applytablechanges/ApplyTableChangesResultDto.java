package modules.query.application.applytablechanges;

import java.util.Map;

import static modules.shared.application.Primitives.getInt;

public final class ApplyTableChangesResultDto {

    private final int affectedRows;

    private ApplyTableChangesResultDto(Map<String, Object> primitives) {
        this.affectedRows = getInt(primitives.get("affectedRows"));
    }

    public static ApplyTableChangesResultDto fromPrimitives(Map<String, Object> primitives) {
        return new ApplyTableChangesResultDto(primitives);
    }

    public int getAffectedRows() {
        return affectedRows;
    }
}
