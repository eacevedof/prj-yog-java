package modules.history.application.recordqueryhistory;

import modules.shared.application.Primitives;
import modules.shared.infrastructure.components.date.DateTimer;

import modules.connections.domain.entities.ConnectionEntity;
import modules.connections.infrastructure.repositories.ConnectionsReaderSqliteRepository;

import modules.history.domain.enums.QueryHistoryKeyEnum;
import modules.history.infrastructure.repositories.QueryHistoryWriterSqliteRepository;

/**
 * Feature 8: apunta en el historial lo que se lanzo contra una BD destino, lecturas y escrituras (SQL del editor,
 * cambios de la rejilla, ALTER, DROP, copia y renombrado de BD, ficheros SQL), tambien si fallo. Se queda con las
 * ultimas MAX_HISTORY_ROWS. Si la BD local falla al apuntarlo, la operacion no se pierde por eso: el historial es
 * secundario y esta fila se pierde (excepcion declarada a "los servicios no capturan", como el aviso por correo).
 */
public final class RecordQueryHistoryService {

    private RecordQueryHistoryService() {
    }

    public static RecordQueryHistoryService getInstance() {
        return new RecordQueryHistoryService();
    }

    public void invoke(RecordQueryHistoryDto recordQueryHistoryDto) {
        try {
            ConnectionEntity connectionEntity = ConnectionsReaderSqliteRepository.getInstance().getConnectionByUuid(recordQueryHistoryDto.getConnectionUuid());
            QueryHistoryWriterSqliteRepository queryHistoryWriterSqliteRepository = QueryHistoryWriterSqliteRepository.getInstance();
            queryHistoryWriterSqliteRepository.insertHistory(Primitives.of(
                QueryHistoryKeyEnum.CONNECTION_UUID, recordQueryHistoryDto.getConnectionUuid(),
                QueryHistoryKeyEnum.CONNECTION_NAME, connectionEntity == null ? "" : connectionEntity.getConnectionName(),
                QueryHistoryKeyEnum.DATABASE_NAME, recordQueryHistoryDto.getDatabaseName(),
                QueryHistoryKeyEnum.SQL_QUERY, recordQueryHistoryDto.getSqlQuery(),
                QueryHistoryKeyEnum.EXECUTED_AT, DateTimer.getInstance().getNowDt(),
                QueryHistoryKeyEnum.ELAPSED_MS, recordQueryHistoryDto.getElapsedMs(),
                QueryHistoryKeyEnum.ROWS_COUNT, recordQueryHistoryDto.getRowsCount(),
                QueryHistoryKeyEnum.HAS_ERROR, recordQueryHistoryDto.hasError()
            ));
            queryHistoryWriterSqliteRepository.deleteOlderThanLast(QueryHistoryKeyEnum.MAX_HISTORY_ROWS);
        }
        catch (RuntimeException e) {
            // el historial no bloquea la operacion: se pierde esta fila. Cualquier fallo, no solo el de SQLite: se llama
            // desde finally y una excepcion aqui taparia el error original de la operacion
        }
    }
}
