package modules.query.infrastructure.components;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import modules.query.domain.services.SqlFileProgressListener;

/**
 * Une el servicio que ejecuta un fichero .sql (en su hilo) con el dialogo que lo pinta, por el id de la ejecucion.
 */
public final class SqlFileProgressTracker {

    private static final SqlFileProgressTracker INSTANCE = new SqlFileProgressTracker();

    private final Map<String, SqlFileProgressListener> listenersByRunId = new ConcurrentHashMap<>();

    private SqlFileProgressTracker() {
    }

    public static SqlFileProgressTracker getInstance() {
        return INSTANCE;
    }

    public void register(String runId, SqlFileProgressListener sqlFileProgressListener) {
        listenersByRunId.put(runId, sqlFileProgressListener);
    }

    public void unregister(String runId) {
        listenersByRunId.remove(runId);
    }

    public void notifyProgress(String runId, long readChars, long totalChars, long executedStatements, long failedStatements) {
        SqlFileProgressListener sqlFileProgressListener = listenersByRunId.get(runId);
        if (sqlFileProgressListener != null) sqlFileProgressListener.onProgress(readChars, totalChars, executedStatements, failedStatements);
    }
}
