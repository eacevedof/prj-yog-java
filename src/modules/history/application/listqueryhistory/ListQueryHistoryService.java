package modules.history.application.listqueryhistory;

import modules.shared.application.Primitives;

import modules.history.domain.enums.QueryHistoryConfigEnum;
import modules.history.infrastructure.repositories.QueryHistoryReaderSqliteRepository;

/**
 * El historial de queries (feature 8): de una conexion o de todas, buscando en el SQL.
 */
public final class ListQueryHistoryService {

    // sin limite pedido, todo lo que guarda el historial: asi ordenar por id ascendente llega a lo mas antiguo
    private static final int DEFAULT_LIMIT = QueryHistoryConfigEnum.MAX_HISTORY_ROWS;

    private ListQueryHistoryService() {
    }

    public static ListQueryHistoryService getInstance() {
        return new ListQueryHistoryService();
    }

    public ListQueryHistoryResultDto invoke(ListQueryHistoryDto listQueryHistoryDto) {
        int limit = listQueryHistoryDto.getLimit() > 0 ? listQueryHistoryDto.getLimit() : DEFAULT_LIMIT;
        return ListQueryHistoryResultDto.fromPrimitives(Primitives.of(
            "history", QueryHistoryReaderSqliteRepository.getInstance().getHistory(
                listQueryHistoryDto.getConnectionUuid(), listQueryHistoryDto.getSearchText(), limit
            )
        ));
    }
}
