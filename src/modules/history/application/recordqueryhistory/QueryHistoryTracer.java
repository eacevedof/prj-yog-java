package modules.history.application.recordqueryhistory;

import java.sql.SQLException;
import java.util.concurrent.TimeUnit;

import modules.shared.application.Primitives;
import modules.shared.infrastructure.components.sqltrace.SqlTraceComponent;
import modules.shared.infrastructure.components.sqltrace.SqlTraceComponent.SqlTrace;

/**
 * Feature 8: envuelve un servicio que lee o escribe la BD destino por sus repositorios (arbol, Info, editores,
 * volcado...) y apunta en el historial, en una sola fila, todo el SQL que lanzo, tambien si fallo. Si el servicio no
 * llego a lanzar nada, no apunta nada.
 */
public final class QueryHistoryTracer {

    private final String connectionUuid;
    private final String databaseName;

    private QueryHistoryTracer(String connectionUuid, String databaseName) {
        this.connectionUuid = connectionUuid;
        this.databaseName = databaseName;
    }

    public static QueryHistoryTracer getInstance(String connectionUuid, String databaseName) {
        return new QueryHistoryTracer(connectionUuid, databaseName);
    }

    /**
     * @throws SQLException la del trabajo, tal cual
     */
    public <T> T getTraced(TracedWork<T> tracedWork) throws SQLException {
        SqlTraceComponent sqlTraceComponent = SqlTraceComponent.getInstance();
        if (!sqlTraceComponent.start()) return tracedWork.run();

        long startNanos = System.nanoTime();
        boolean hasError = true;
        try {
            T result = tracedWork.run();
            hasError = false;
            return result;
        }
        finally {
            SqlTrace sqlTrace = sqlTraceComponent.stop();
            if (!sqlTrace.getSqls().isEmpty()) {
                RecordQueryHistoryService.getInstance().invoke(RecordQueryHistoryDto.fromPrimitives(Primitives.of(
                    "connectionUuid", connectionUuid,
                    "databaseName", databaseName,
                    "sqlQuery", String.join(";\n", sqlTrace.getSqls()),
                    "elapsedMs", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos),
                    "rowsCount", sqlTrace.getRowsCount(),
                    "hasError", hasError
                )));
            }
        }
    }

    @FunctionalInterface
    public interface TracedWork<T> {
        T run() throws SQLException;
    }
}
