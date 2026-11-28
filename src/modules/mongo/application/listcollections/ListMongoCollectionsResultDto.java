package modules.mongo.application.listcollections;

import java.util.List;
import java.util.Map;

public final class ListMongoCollectionsResultDto {

    private final List<Map<String, Object>> collections;

    @SuppressWarnings("unchecked")
    private ListMongoCollectionsResultDto(Map<String, Object> primitives) {
        this.collections = List.copyOf((List<Map<String, Object>>) primitives.get("collections"));
    }

    public static ListMongoCollectionsResultDto fromPrimitives(Map<String, Object> primitives) {
        return new ListMongoCollectionsResultDto(primitives);
    }

    /**
     * name y count (estimado; -1 en una vista).
     */
    public List<Map<String, Object>> getCollections() {
        return collections;
    }
}
