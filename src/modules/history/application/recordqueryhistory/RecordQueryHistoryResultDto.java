package modules.history.application.recordqueryhistory;

import java.util.Map;

/**
 * Sin datos de salida: la ejecucion queda en el historial (o se pierde si falla la BD local).
 */
public final class RecordQueryHistoryResultDto {

    private RecordQueryHistoryResultDto(Map<String, Object> primitives) {
    }

    public static RecordQueryHistoryResultDto fromPrimitives(Map<String, Object> primitives) {
        return new RecordQueryHistoryResultDto(primitives);
    }
}
