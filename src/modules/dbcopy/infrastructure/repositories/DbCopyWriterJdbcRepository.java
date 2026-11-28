package modules.dbcopy.infrastructure.repositories;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import modules.shared.domain.services.SqlDialect;
import modules.shared.infrastructure.repositories.AbstractJdbcRepository;

import modules.connections.infrastructure.components.httptunnel.HttpTunnelSession;

import modules.dbcopy.domain.enums.DbCopyConfigEnum;
import modules.dbcopy.domain.services.DbCopySqlBuilder;

/**
 * Copia entre motores: las escrituras en el destino (cualquier motor SQL) sobre una sesion propia. El SQL lo compone
 * PortableCopySqlBuilder en el servicio: aqui solo se ejecuta. En MySQL la sesion lleva los SET de la copia (sin modo
 * estricto, sin que un 0 se convierta en el siguiente AUTO_INCREMENT, UTC); por el tunel van delante de cada lote.
 */
public final class DbCopyWriterJdbcRepository extends AbstractJdbcRepository {

    private final HttpTunnelSession httpTunnelSession;
    private final boolean isMysql;

    private DbCopyWriterJdbcRepository(Connection pdo, String dbEngine) throws SQLException {
        super(pdo, dbEngine);
        this.httpTunnelSession = pdo.isWrapperFor(HttpTunnelSession.class) ? pdo.unwrap(HttpTunnelSession.class) : null;
        this.isMysql = SqlDialect.getInstance(dbEngine).isMysql();
    }

    public static DbCopyWriterJdbcRepository getInstance(Connection pdo, String dbEngine) throws SQLException {
        return new DbCopyWriterJdbcRepository(pdo, dbEngine);
    }

    /**
     * Por el tunel HTTP (los literales de SQLite cambian y no hay sesion entre peticiones).
     */
    public boolean isHttpTunnel() {
        return httpTunnelSession != null;
    }

    public void prepareSession() throws SQLException {
        if (!isMysql || httpTunnelSession != null) return;
        try (Statement statement = pdo.createStatement()) {
            for (String sessionSql : DbCopySqlBuilder.TARGET_SESSION_SQLS) {
                statement.execute(sessionSql);
            }
        }
    }

    /**
     * Cuanto cabe en un INSERT: en MySQL por JDBC lo que deje su max_allowed_packet; por el tunel, una peticion HTTP
     * razonable; en el resto, el tope de JDBC.
     */
    public int getMaxStatementChars() throws SQLException {
        if (httpTunnelSession != null) return DbCopyConfigEnum.TUNNEL_MAX_STATEMENT_CHARS;
        if (!isMysql) return DbCopyConfigEnum.JDBC_MAX_STATEMENT_CHARS;
        return DbCopyReaderMysqlRepository.getInstance(pdo).getMaxStatementChars();
    }

    /**
     * Una sentencia sola, fuera de transaccion (CREATE DATABASE de SQL Server no puede ir dentro de una).
     */
    public void executeAlone(String sql) throws SQLException {
        try (Statement statement = pdo.createStatement()) {
            statement.execute(sql);
        }
    }

    /**
     * Los INSERT de una pagina, con las sentencias de sesion que necesiten delante (SET IDENTITY_INSERT de SQL Server):
     * por JDBC en una transaccion; por el tunel en un lote, con los SET de MySQL delante.
     */
    public void executeInserts(List<String> sessionSqls, List<String> statements) throws SQLException {
        if (statements.isEmpty()) return;
        if (httpTunnelSession != null) {
            List<String> script = new ArrayList<>(isMysql ? DbCopySqlBuilder.TARGET_SESSION_SQLS : List.of());
            script.addAll(sessionSqls);
            script.addAll(statements);
            httpTunnelSession.executeBatch(script);
            return;
        }
        executeInTransaction(() -> {
            try (Statement statement = pdo.createStatement()) {
                for (String sql : sessionSqls) {
                    statement.execute(sql);
                }
                for (String sql : statements) {
                    statement.execute(sql);
                }
            }
            return statements.size();
        });
    }
}
