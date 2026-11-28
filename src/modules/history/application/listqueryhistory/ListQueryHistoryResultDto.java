package modules.history.application.listqueryhistory;

import java.util.List;
import java.util.Map;

public final class ListQueryHistoryResultDto {

    private final List<Map<String, Object>> history;

    @SuppressWarnings("unchecked")
    private ListQueryHistoryResultDto(Map<String, Object> primitives) {
        this.history = List.copyOf((List<Map<String, Object>>) primitives.get("history"));
    }

    public static ListQueryHistoryResultDto fromPrimitives(Map<String, Object> primitives) {
        return new ListQueryHistoryResultDto(primitives);
    }

    /**
     * Lo mas reciente primero, con las claves de QueryHistoryKeyEnum.
     */
    public List<Map<String, Object>> getHistory() {
        return history;
    }
}
