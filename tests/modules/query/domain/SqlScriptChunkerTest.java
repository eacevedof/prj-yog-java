package modules.query.domain;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import testcase.AbstractTestCase;

import modules.query.domain.entities.SqlStatementEntity;
import modules.query.domain.services.SqlScriptChunker;

/**
 * Un .sql que llega por trozos (Ejecutar fichero): el resultado es el mismo que trocearlo entero, aunque el corte caiga
 * dentro de un literal, de un cuerpo $$ o entre un DELIMITER y su sentencia.
 */
final class SqlScriptChunkerTest extends AbstractTestCase {

    @Test
    void testMysqlDelimiterSurvivesChunkBoundaries() {
        String script = """
            CREATE TABLE t (id INT, txt VARCHAR(20));
            INSERT INTO t VALUES (1, 'a;b'), (2, 'it''s');
            DELIMITER ;;
            CREATE PROCEDURE p() BEGIN SELECT 1; SELECT 2; END;;
            DELIMITER ;
            /* comentario ; con punto y coma */
            SELECT 3;
            """;

        for (int chunkSize : List.of(1, 3, 7, 16, 1000)) {
            assertEquals(List.of(
                "CREATE TABLE t (id INT, txt VARCHAR(20))",
                "INSERT INTO t VALUES (1, 'a;b'), (2, 'it''s')",
                "CREATE PROCEDURE p() BEGIN SELECT 1; SELECT 2; END",
                "/* comentario ; con punto y coma */\nSELECT 3"
            ), getSqls("mysql", script, chunkSize));
        }
    }

    @Test
    void testPostgresDollarBodiesAndSqlServerGo() {
        String pgScript = "CREATE FUNCTION f() RETURNS int AS $$ BEGIN RETURN 1; END; $$ LANGUAGE plpgsql;\nSELECT f();\n";
        String msScript = "CREATE PROCEDURE p AS BEGIN SELECT 1; SELECT 2; END\nGO\nEXEC p\nGO\n";

        for (int chunkSize : List.of(2, 5, 1000)) {
            assertEquals(List.of("CREATE FUNCTION f() RETURNS int AS $$ BEGIN RETURN 1; END; $$ LANGUAGE plpgsql", "SELECT f()"), getSqls("pgsql", pgScript, chunkSize));
            assertEquals(List.of("CREATE PROCEDURE p AS BEGIN SELECT 1; SELECT 2; END", "EXEC p"), getSqls("mssql", msScript, chunkSize));
        }
    }

    private List<String> getSqls(String dbEngine, String script, int chunkSize) {
        SqlScriptChunker sqlScriptChunker = SqlScriptChunker.getInstance(dbEngine);
        List<String> sqls = new ArrayList<>();
        for (int start = 0; start < script.length(); start += chunkSize) {
            sqlScriptChunker.addChunk(script.substring(start, Math.min(script.length(), start + chunkSize)));
            for (SqlStatementEntity statement : sqlScriptChunker.getCompleteStatements()) sqls.add(statement.getSql());
        }
        sqlScriptChunker.finish();
        for (SqlStatementEntity statement : sqlScriptChunker.getCompleteStatements()) sqls.add(statement.getSql());
        return sqls;
    }
}
