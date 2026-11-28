package modules.history.application.listqueryhistory;

import java.util.Map;

import static modules.shared.application.Primitives.getInt;
import static modules.shared.application.Primitives.getTrimmedString;

public final class ListQueryHistoryDto {

    private final String connectionUuid;
    private final String searchText;
    private final int limit;

    private ListQueryHistoryDto(Map<String, Object> primitives) {
        this.connectionUuid = getTrimmedString(primitives.get("connectionUuid"));
        this.searchText = getTrimmedString(primitives.get("searchText"));
        this.limit = getInt(primitives.get("limit"));
    }

    public static ListQueryHistoryDto fromPrimitives(Map<String, Object> primitives) {
        return new ListQueryHistoryDto(primitives);
    }

    /**
     * "" = todas las conexiones.
     */
    public String getConnectionUuid() {
        return connectionUuid;
    }

    public String getSearchText() {
        return searchText;
    }

    public int getLimit() {
        return limit;
    }
}
