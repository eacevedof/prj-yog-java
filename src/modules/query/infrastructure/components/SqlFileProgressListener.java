package modules.query.infrastructure.components;

/**
 * El progreso de ejecutar un fichero .sql: caracteres leidos de los totales (aprox. bytes) y sentencias ejecutadas.
 */
public interface SqlFileProgressListener {

    void onProgress(long readChars, long totalChars, long executedStatements, long failedStatements);
}
