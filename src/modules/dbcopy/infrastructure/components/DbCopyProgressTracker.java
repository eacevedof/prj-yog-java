package modules.dbcopy.infrastructure.components;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import modules.dbcopy.domain.services.DbCopyProgressListener;

/**
 * El progreso y el "Parar" de cada copia en marcha, por id de copia (feature 29). La UI registra su listener antes de
 * lanzar la copia y lo quita al acabar; CopyDatabaseService lo obtiene con getInstance() y avisa por el id que llega en
 * su DTO, asi el listener no viaja como argumento del caso de uso (decision de Eduardo, 2026-11-25, DDD 20). Una copia
 * sin listener registrado (tests, consola) no informa de nada y nunca se para. Mismo patron que
 * RunningStatementsComponent para cancelar queries.
 */
public final class DbCopyProgressTracker {

    private static final Map<String, DbCopyProgressListener> LISTENERS_BY_COPY_ID = new ConcurrentHashMap<>();

    private DbCopyProgressTracker() {
    }

    public static DbCopyProgressTracker getInstance() {
        return new DbCopyProgressTracker();
    }

    public void register(String copyId, DbCopyProgressListener dbCopyProgressListener) {
        LISTENERS_BY_COPY_ID.put(copyId, dbCopyProgressListener);
    }

    public void unregister(String copyId) {
        LISTENERS_BY_COPY_ID.remove(copyId);
    }

    public void notifyObjectStarted(String copyId, String objectType, String objectName, int objectIndex, int objectCount) {
        getListener(copyId).onObjectStarted(objectType, objectName, objectIndex, objectCount);
    }

    public void notifyRowsCopied(String copyId, String tableName, long copiedRows, long estimatedRows) {
        getListener(copyId).onRowsCopied(tableName, copiedRows, estimatedRows);
    }

    public void notifyPhaseStarted(String copyId, String phase) {
        getListener(copyId).onPhaseStarted(phase);
    }

    public boolean isCancelled(String copyId) {
        return getListener(copyId).isCancelled();
    }

    private DbCopyProgressListener getListener(String copyId) {
        return LISTENERS_BY_COPY_ID.getOrDefault(copyId, DbCopyProgressListener.NONE);
    }
}
