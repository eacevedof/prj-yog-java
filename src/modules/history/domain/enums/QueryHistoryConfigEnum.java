package modules.history.domain.enums;

import java.util.List;

/**
 * Como se apuntan en el historial las operaciones que no son una sentencia que se pueda relanzar (ejecutar un fichero,
 * copiar o renombrar una BD) y los textos del script SQL exportado (comentarios en ingles: son del fichero, no de la
 * interfaz).
 */
public final class QueryHistoryConfigEnum {

    // la orden del cliente mysql que hace lo mismo que "Ejecutar fichero SQL"
    public static final String SQL_FILE_PREFIX = "SOURCE ";
    public static final String COPY_DATABASE_X_X_X = "-- copy database %s.`%s` -> `%s`";
    public static final String RENAME_DATABASE_X_X = "-- rename database `%s` -> `%s`";
    public static final String RENAME_DATABASE_DROPPED_X_X = "-- rename database `%s` -> `%s` (original dropped)";
    // lo que empieza asi se exporta comentado: no se puede volver a lanzar tal cual
    public static final List<String> NOT_REPLAYABLE_PREFIXES = List.of(SQL_FILE_PREFIX, "-- copy database ", "-- rename database ");

    public static final String SCRIPT_HEADER_X_X = """
        /* multi-dbs query history, exported %s: %d execution(s), oldest first.
           Run it in order (Import / Execute SQL file) to replay the statements and reach the same state.
           Failed executions and operations that are not a statement (SQL files, copy or rename database)
           are commented out. */
        """;
    public static final String ENTRY_X_X_X_X_X_X = "#%s · %s · %s · %s · %s ms · %s row(s)";
    public static final String NOT_REPLAYED_ERROR = " · ERROR, not replayed";
    public static final String NOT_REPLAYED_OPERATION = " · not a statement, not replayed";
    public static final String DATABASE_CHANGE_X = "/* database: %s */";

    private QueryHistoryConfigEnum() {
    }
}
