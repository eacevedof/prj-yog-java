package modules.query.domain.services;

/**
 * El progreso de ejecutar un fichero .sql: caracteres leidos de los totales (aprox. bytes) y sentencias ejecutadas.
 */
public interface SqlFileProgressListener {

    void onProgress(long readChars, long totalChars, long executedStatements, long failedStatements);
}
