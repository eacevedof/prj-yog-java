package modules.history.application.clearqueryhistory;

import java.util.Map;

import static modules.shared.application.Primitives.getInt;

public final class ClearQueryHistoryResultDto {

    private final int deletedCount;

    private ClearQueryHistoryResultDto(Map<String, Object> primitives) {
        this.deletedCount = getInt(primitives.get("deletedCount"));
    }

    public static ClearQueryHistoryResultDto fromPrimitives(Map<String, Object> primitives) {
        return new ClearQueryHistoryResultDto(primitives);
    }

    public int getDeletedCount() {
        return deletedCount;
    }
}
