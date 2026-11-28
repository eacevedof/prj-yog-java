package modules.dbcopy.infrastructure.repositories;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import modules.shared.infrastructure.repositories.AbstractJdbcRepository;

import modules.connections.infrastructure.components.httptunnel.HttpTunnelSession;

import modules.dbcopy.domain.enums.DbCopyConfigEnum;
import modules.dbcopy.domain.enums.DbCopyObjectTypeEnum;
import modules.dbcopy.domain.services.BulkInsertBuffer;
import modules.dbcopy.domain.services.DbCopySqlBuilder;

/**
 * Las escrituras de la copia (feature 29) en el destino, sobre una sesion propia; lo que se lee del destino va por
 * DbCopyReaderMysqlRepository. El SQL lo compone este repositorio (con DbCopySqlBuilder): el servicio solo pasa
 * nombres, el CREATE leido del origen y las filas. Por JDBC los SET de la sesion (sin claves foraneas, sql_mode, UTC)
 * se fijan una vez; por el tunel HTTP cada peticion es una sesion nueva, asi que van delante de cada lote.
 */
public final class DbCopyWriterMysqlRepository extends AbstractJdbcRepository {

    private final DbCopySqlBuilder dbCopySqlBuilder = DbCopySqlBuilder.getInstance();
    private final HttpTunnelSession httpTunnelSession;

    private DbCopyWriterMysqlRepository(Connection pdo) throws SQLException {
        super(pdo);
        this.httpTunnelSession = pdo.isWrapperFor(HttpTunnelSession.class) ? pdo.unwrap(HttpTunnelSession.class) : null;
    }

    public static DbCopyWriterMysqlRepository getInstance(Connection pdo) throws SQLException {
        return new DbCopyWriterMysqlRepository(pdo);
    }

    /**
     * "CREATE DATABASE IF NOT EXISTS" con el juego de caracteres del origen.
     */
    public void createDatabase(String databaseName, String characterSet, String collation) throws SQLException {
        executeAlone(dbCopySqlBuilder.getCreateDatabaseSql(databaseName, characterSet, collation));
    }

    /**
     * Renombrar BD: la BD entera, con todo lo que tiene.
     */
    public void dropDatabase(String databaseName) throws SQLException {
        executeAlone(dbCopySqlBuilder.getDropDatabaseSql(databaseName));
    }

    /**
     * La BD destino como activa (los CREATE no van cualificados) y, por JDBC, los SET de la sesion de copia.
     */
    public void prepareSession(String databaseName) throws SQLException {
        pdo.setCatalog(databaseName);
        if (httpTunnelSession != null) return;
        try (Statement statement = pdo.createStatement()) {
            for (String sessionSql : DbCopyConfigEnum.TARGET_SESSION_SQLS) {
                statement.execute(sessionSql);
            }
        }
    }

    /**
     * "Borrar en destino si existe": DROP TABLE IF EXISTS y el CREATE del origen, en la sesion de copia (sin claves
     * foraneas, que si no impedirian borrar o crear en cualquier orden).
     */
    public void dropAndCreateTable(String tableName, String createTableSql) throws SQLException {
        executeInSession(List.of(dbCopySqlBuilder.getDropSql(DbCopyObjectTypeEnum.TABLE, tableName), createTableSql));
    }

    /**
     * Sin "Borrar en destino": la tabla que ya existe se queda (CREATE TABLE IF NOT EXISTS) y los datos se añaden.
     */
    public void createTableIfMissing(String createTableSql) throws SQLException {
        executeInSession(List.of(dbCopySqlBuilder.getCreateTableIfNotExists(createTableSql)));
    }

    /**
     * DROP VIEW / PROCEDURE / FUNCTION / TRIGGER / EVENT IF EXISTS.
     */
    public void dropObject(String objectType, String objectName) throws SQLException {
        executeAlone(dbCopySqlBuilder.getDropSql(objectType, objectName));
    }

    /**
     * El CREATE del origen de una vista, rutina, trigger o evento, tal cual (o sin DEFINER): puede llevar ";" en su
     * cuerpo BEGIN ... END, asi que va solo.
     */
    public void createObject(String createSql) throws SQLException {
        executeAlone(createSql);
    }

    /**
     * Una pagina de filas en INSERT de varias filas, sin pasar de maxStatementChars por sentencia.
     */
    public void insertRowsInBulk(String tableName, List<String> columnNames, List<List<Object>> rows, int maxStatementChars) throws SQLException {
        insertRows(BulkInsertBuffer.getBulkInstance(dbCopySqlBuilder.getInsertPrefix(tableName, columnNames), maxStatementChars), rows);
    }

    /**
     * Una pagina de filas, un INSERT por fila (sin "INSERT en bloque").
     */
    public void insertRowsOneByOne(String tableName, List<String> columnNames, List<List<Object>> rows) throws SQLException {
        insertRows(BulkInsertBuffer.getRowByRowInstance(dbCopySqlBuilder.getInsertPrefix(tableName, columnNames)), rows);
    }

    private void insertRows(BulkInsertBuffer bulkInsertBuffer, List<List<Object>> rows) throws SQLException {
        for (List<Object> row : rows) {
            bulkInsertBuffer.add(dbCopySqlBuilder.getRowTuple(row));
        }
        executeInSession(bulkInsertBuffer.getStatements());
    }

    /**
     * Sentencias que necesitan los SET de la sesion de copia (DROP/CREATE TABLE con claves foraneas, INSERT). Por el
     * tunel van en un lote, que el PHP trocea por ";": no valen para cuerpos BEGIN ... END (ver executeAlone).
     */
    private void executeInSession(List<String> statements) throws SQLException {
        if (statements.isEmpty()) return;
        if (httpTunnelSession != null) {
            List<String> script = new ArrayList<>(DbCopyConfigEnum.TARGET_SESSION_SQLS);
            script.addAll(statements);
            httpTunnelSession.executeBatch(script);
            return;
        }
        // en una transaccion: sin "INSERT en bloque" son miles de sentencias y un COMMIT por fila es lo que mas tarda
        executeInTransaction(() -> {
            try (Statement statement = pdo.createStatement()) {
                for (String sql : statements) {
                    statement.execute(sql);
                }
            }
            return statements.size();
        });
    }

    /**
     * Una sentencia sola, sin los SET (vistas, rutinas, triggers, eventos): por el tunel va en una peticion propia y
     * el servidor la recibe entera, con sus ";" internos.
     */
    private void executeAlone(String sql) throws SQLException {
        try (Statement statement = pdo.createStatement()) {
            statement.execute(sql);
        }
    }
}
