package modules.query.domain.services;

import java.util.List;

import modules.query.domain.entities.SqlStatementEntity;

/**
 * Trocea en sentencias un script que llega por partes (un .sql de cientos de MB no cabe entero en memoria). Con cada
 * parte devuelve las sentencias que ya estan completas y se guarda el resto: la ultima sentencia puede seguir en la
 * parte siguiente (un literal, un comentario o un cuerpo $$ partido). El delimitador de un DELIMITER sigue valiendo en
 * las partes que vienen. Una instancia por script.
 */
public final class SqlScriptChunker {

    private final SqlStatementSplitter sqlStatementSplitter;
    private final StringBuilder pendingText = new StringBuilder();
    private String delimiter = ";";
    private List<SqlStatementEntity> completeStatements = List.of();

    private SqlScriptChunker(String dbEngine) {
        this.sqlStatementSplitter = SqlStatementSplitter.getInstance(dbEngine);
    }

    public static SqlScriptChunker getInstance(String dbEngine) {
        return new SqlScriptChunker(dbEngine);
    }

    /**
     * Junta la parte con lo pendiente y deja en getCompleteStatements las sentencias ya completas (todas menos la
     * ultima, que puede no haber acabado).
     */
    public void addChunk(String chunk) {
        pendingText.append(chunk);
        List<SqlStatementEntity> statements = sqlStatementSplitter.getStatements(pendingText.toString(), delimiter);
        if (statements.size() < 2) {
            completeStatements = List.of();
            return;
        }
        completeStatements = List.copyOf(statements.subList(0, statements.size() - 1));
        SqlStatementEntity lastComplete = completeStatements.getLast();
        delimiter = lastComplete.getDelimiter();
        pendingText.delete(0, lastComplete.getEndIndex());
    }

    /**
     * Al acabar el fichero: deja en getCompleteStatements lo que queda, sea como sea su final.
     */
    public void finish() {
        completeStatements = sqlStatementSplitter.getStatements(pendingText.toString(), delimiter);
        pendingText.setLength(0);
    }

    /**
     * Las sentencias que completo el ultimo addChunk o finish.
     */
    public List<SqlStatementEntity> getCompleteStatements() {
        return completeStatements;
    }
}
