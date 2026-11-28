package modules.dbcopy.domain.services;

import java.util.ArrayList;
import java.util.List;

/**
 * Junta filas en INSERT: con "INSERT en bloque" (getBulkInstance), varias filas por sentencia sin pasar de
 * maxStatementChars (el max_allowed_packet del servidor o el tamaño de una peticion del tunel); sin el
 * (getRowByRowInstance), una sentencia por fila.
 */
public final class BulkInsertBuffer {

    private final String insertPrefix;
    private final boolean isBulk;
    private final int maxStatementChars;
    private final StringBuilder currentStatement = new StringBuilder();
    private final List<String> closedStatements = new ArrayList<>();

    private BulkInsertBuffer(String insertPrefix, boolean isBulk, int maxStatementChars) {
        this.insertPrefix = insertPrefix;
        this.isBulk = isBulk;
        this.maxStatementChars = maxStatementChars;
    }

    public static BulkInsertBuffer getBulkInstance(String insertPrefix, int maxStatementChars) {
        return new BulkInsertBuffer(insertPrefix, true, maxStatementChars);
    }

    public static BulkInsertBuffer getRowByRowInstance(String insertPrefix) {
        return new BulkInsertBuffer(insertPrefix, false, 0);
    }

    /**
     * Añade una fila ("(...)"). Una fila mas grande que el tope va sola en su sentencia.
     */
    public void add(String rowTuple) {
        if (!isBulk) {
            addClosed(insertPrefix + rowTuple);
            return;
        }
        if (!currentStatement.isEmpty() && currentStatement.length() + 2 + rowTuple.length() > maxStatementChars) {
            closeCurrentStatement();
        }
        if (currentStatement.isEmpty()) currentStatement.append(insertPrefix);
        else currentStatement.append(", ");
        currentStatement.append(rowTuple);
    }

    /**
     * Todas las sentencias, tambien la que esta a medias; no cambia el buffer.
     */
    public List<String> getStatements() {
        List<String> statements = new ArrayList<>(closedStatements);
        if (!currentStatement.isEmpty()) statements.add(currentStatement.toString());
        return List.copyOf(statements);
    }

    private void closeCurrentStatement() {
        addClosed(currentStatement.toString());
        currentStatement.setLength(0);
    }

    private void addClosed(String statement) {
        closedStatements.add(statement);
    }
}
