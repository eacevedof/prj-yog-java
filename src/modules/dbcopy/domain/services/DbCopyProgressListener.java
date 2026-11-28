package modules.dbcopy.domain.services;

/**
 * Lo que la copia cuenta mientras corre (la UI pinta el progreso) y como se le pide que pare.
 */
public interface DbCopyProgressListener {

    DbCopyProgressListener NONE = new DbCopyProgressListener() {
    };

    /**
     * Empieza un objeto: indice (desde 1) sobre el total.
     */
    default void onObjectStarted(String objectType, String objectName, int objectIndex, int objectCount) {
    }

    /**
     * Filas copiadas de la tabla en curso; estimatedRows es la estimacion del servidor (0 si no la da).
     */
    default void onRowsCopied(String tableName, long copiedRows, long estimatedRows) {
    }

    /**
     * Empieza una fase de una operacion de varios pasos (Renombrar BD: volcar, crear, cargar, comprobar, borrar).
     */
    default void onPhaseStarted(String phase) {
    }

    /**
     * Se mira entre objetos y entre paginas de filas: true = parar en cuanto se pueda.
     */
    default boolean isCancelled() {
        return false;
    }
}
