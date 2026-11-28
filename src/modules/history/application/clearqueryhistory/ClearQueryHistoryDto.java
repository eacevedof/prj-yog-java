package modules.history.application.clearqueryhistory;

import java.util.Map;

import static modules.shared.application.Primitives.getTrimmedString;

public final class ClearQueryHistoryDto {

    private final String connectionUuid;

    private ClearQueryHistoryDto(Map<String, Object> primitives) {
        this.connectionUuid = getTrimmedString(primitives.get("connectionUuid"));
    }

    public static ClearQueryHistoryDto fromPrimitives(Map<String, Object> primitives) {
        return new ClearQueryHistoryDto(primitives);
    }

    /**
     * "" = el historial de todas las conexiones.
     */
    public String getConnectionUuid() {
        return connectionUuid;
    }
}
