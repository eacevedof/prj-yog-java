package modules.history.application.clearqueryhistory;

import modules.shared.application.Primitives;

import modules.history.infrastructure.repositories.QueryHistoryWriterSqliteRepository;

/**
 * Limpia el historial: el de una conexion o, sin conexion, el de todas.
 */
public final class ClearQueryHistoryService {

    private ClearQueryHistoryService() {
    }

    public static ClearQueryHistoryService getInstance() {
        return new ClearQueryHistoryService();
    }

    public ClearQueryHistoryResultDto invoke(ClearQueryHistoryDto clearQueryHistoryDto) {
        QueryHistoryWriterSqliteRepository queryHistoryWriterSqliteRepository = QueryHistoryWriterSqliteRepository.getInstance();
        int deletedCount = clearQueryHistoryDto.getConnectionUuid().isEmpty()
            ? queryHistoryWriterSqliteRepository.deleteAllHistory()
            : queryHistoryWriterSqliteRepository.deleteConnectionHistory(clearQueryHistoryDto.getConnectionUuid());
        return ClearQueryHistoryResultDto.fromPrimitives(Primitives.of("deletedCount", deletedCount));
    }
}
