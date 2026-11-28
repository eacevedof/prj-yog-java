package modules.workspace.application.savequerytabs;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static modules.shared.application.Primitives.getTrimmedString;

public final class SaveQueryTabsDto {

    private final String connectionUuid;
    private final String activeDatabase;
    private final List<Map<String, Object>> queryTabs;

    @SuppressWarnings("unchecked")
    private SaveQueryTabsDto(Map<String, Object> primitives) {
        this.connectionUuid = getTrimmedString(primitives.get("connectionUuid"));
        this.activeDatabase = getTrimmedString(primitives.get("activeDatabase"));
        List<Map<String, Object>> tabs = new ArrayList<>();
        if (primitives.get("queryTabs") instanceof List<?> list) {
            for (Object tab : list) {
                if (tab instanceof Map<?, ?> map) tabs.add((Map<String, Object>) map);
            }
        }
        this.queryTabs = List.copyOf(tabs);
    }

    public static SaveQueryTabsDto fromPrimitives(Map<String, Object> primitives) {
        return new SaveQueryTabsDto(primitives);
    }

    public String getConnectionUuid() {
        return connectionUuid;
    }

    public String getActiveDatabase() {
        return activeDatabase;
    }

    /**
     * En su orden, con las claves de WorkspaceKeyEnum (tab_title, sql_text, is_selected).
     */
    public List<Map<String, Object>> getQueryTabs() {
        return queryTabs;
    }
}
