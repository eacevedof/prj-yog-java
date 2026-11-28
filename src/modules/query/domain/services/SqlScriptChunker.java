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

    private SqlScriptChunker(String dbEngine) {
        this.sqlStatementSplitter = SqlStatementSplitter.getInstance(dbEngine);
    }

    public static SqlScriptChunker getInstance(String dbEngine) {
        return new SqlScriptChunker(dbEngine);
    }

    /**
     * Las sentencias completas con lo leido hasta ahora (todas menos la ultima, que puede no haber acabado).
     */
    public List<SqlStatementEntity> addChunk(String chunk) {
        pendingText.append(chunk);
        List<SqlStatementEntity> statements = sqlStatementSplitter.getStatements(pendingText.toString(), delimiter);
        if (statements.size() < 2) return List.of();
        List<SqlStatementEntity> completeStatements = List.copyOf(statements.subList(0, statements.size() - 1));
        SqlStatementEntity lastComplete = completeStatements.getLast();
        delimiter = lastComplete.getDelimiter();
        pendingText.delete(0, lastComplete.getEndIndex());
        return completeStatements;
    }

    /**
     * Al acabar el fichero: lo que queda, sea como sea su final.
     */
    public List<SqlStatementEntity> finish() {
        List<SqlStatementEntity> statements = sqlStatementSplitter.getStatements(pendingText.toString(), delimiter);
        pendingText.setLength(0);
        return statements;
    }
}
