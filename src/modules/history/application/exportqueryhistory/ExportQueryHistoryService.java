package modules.history.application.exportqueryhistory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import modules.shared.application.Primitives;
import modules.shared.infrastructure.components.date.DateTimer;

import static core.config.Global.__;

import modules.history.domain.enums.QueryHistoryConfigEnum;
import modules.history.domain.enums.QueryHistoryMessageEnum;
import modules.history.domain.services.QueryHistorySqlScriptBuilder;
import modules.history.infrastructure.repositories.QueryHistoryReaderSqliteRepository;
import modules.history.infrastructure.repositories.QueryHistoryWriterFileRepository;
import modules.history.domain.exceptions.QueryHistoryException;

/**
 * Exporta a un .sql lo que filtra la pestaña Historial (una conexion o todas, y el texto buscado) en orden de ejecucion,
 * para volver a lanzarlo con Importar y llegar a la misma foto (QueryHistorySqlScriptBuilder).
 */
public final class ExportQueryHistoryService {

    private ExportQueryHistoryService() {
    }

    public static ExportQueryHistoryService getInstance() {
        return new ExportQueryHistoryService();
    }

    /**
     * @throws QueryHistoryException
     * @throws IOException si no se puede escribir el fichero: sube tal cual hasta el controller
     */
    public ExportQueryHistoryResultDto invoke(ExportQueryHistoryDto exportQueryHistoryDto) throws IOException {
        failIfWrongInput(exportQueryHistoryDto);
        // el lector da lo mas reciente primero; el script va en orden de ejecucion
        List<Map<String, Object>> historyRows = QueryHistoryReaderSqliteRepository.getInstance().getHistory(
            exportQueryHistoryDto.getConnectionUuid(), exportQueryHistoryDto.getSearchText(), QueryHistoryConfigEnum.MAX_HISTORY_ROWS
        ).reversed();
        String script = QueryHistorySqlScriptBuilder.getInstance(exportQueryHistoryDto.getDbEngine())
            .getScript(historyRows, DateTimer.getInstance().getNowDt());
        QueryHistoryWriterFileRepository.getInstance().writeScript(Path.of(exportQueryHistoryDto.getFilePath()), script);
        return ExportQueryHistoryResultDto.fromPrimitives(Primitives.of(
            "exportedCount", historyRows.size(),
            "filePath", exportQueryHistoryDto.getFilePath()
        ));
    }

    /**
     * @throws QueryHistoryException
     */
    private void failIfWrongInput(ExportQueryHistoryDto exportQueryHistoryDto) {
        if (exportQueryHistoryDto.getFilePath().isEmpty()) {
            throw QueryHistoryException.badRequestCustom(__(QueryHistoryMessageEnum.FIELD_IS_REQUIRED_X, "filePath"));
        }
    }
}
