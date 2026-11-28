package modules.workspace.application.getquerytabs;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static modules.shared.application.Primitives.getTrimmedString;

public final class GetQueryTabsResultDto {

    private final String activeDatabase;
    private final List<Map<String, Object>> queryTabs;

    /**
     * Solo los elementos que son mapas (como SaveQueryTabsDto): un elemento de otro tipo no llega a la UI como un
     * ClassCastException tardio.
     */
    @SuppressWarnings("unchecked")
    private GetQueryTabsResultDto(Map<String, Object> primitives) {
        this.activeDatabase = getTrimmedString(primitives.get("activeDatabase"));
        List<Map<String, Object>> tabs = new ArrayList<>();
        if (primitives.get("queryTabs") instanceof List<?> rawTabs) {
            for (Object tab : rawTabs) {
                if (tab instanceof Map<?, ?> map) tabs.add((Map<String, Object>) map);
            }
        }
        this.queryTabs = List.copyOf(tabs);
    }

    public static GetQueryTabsResultDto fromPrimitives(Map<String, Object> primitives) {
        return new GetQueryTabsResultDto(primitives);
    }

    /**
     * "" si no se guardo: se queda la de la conexion.
     */
    public String getActiveDatabase() {
        return activeDatabase;
    }

    /**
     * En su orden, con las claves de WorkspaceKeyEnum; vacio si la conexion no tiene pestañas guardadas.
     */
    public List<Map<String, Object>> getQueryTabs() {
        return queryTabs;
    }
}
