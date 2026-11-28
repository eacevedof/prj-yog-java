package modules.history.application.exportqueryhistory;

import java.util.Map;

import static modules.shared.application.Primitives.getTrimmedString;

public final class ExportQueryHistoryDto {

    private final String connectionUuid;
    private final String searchText;
    private final String dbEngine;
    private final String filePath;

    private ExportQueryHistoryDto(Map<String, Object> primitives) {
        this.connectionUuid = getTrimmedString(primitives.get("connectionUuid"));
        this.searchText = getTrimmedString(primitives.get("searchText"));
        this.dbEngine = getTrimmedString(primitives.get("dbEngine"));
        this.filePath = getTrimmedString(primitives.get("filePath"));
    }

    public static ExportQueryHistoryDto fromPrimitives(Map<String, Object> primitives) {
        return new ExportQueryHistoryDto(primitives);
    }

    /**
     * "" = el historial de todas las conexiones.
     */
    public String getConnectionUuid() {
        return connectionUuid;
    }

    public String getSearchText() {
        return searchText;
    }

    /**
     * El motor de la conexion donde se va a lanzar el script (como se cambia de BD).
     */
    public String getDbEngine() {
        return dbEngine;
    }

    public String getFilePath() {
        return filePath;
    }
}
