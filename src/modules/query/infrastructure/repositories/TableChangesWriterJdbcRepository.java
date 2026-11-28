package modules.query.infrastructure.repositories;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import modules.shared.domain.services.SqlDialect;
import modules.shared.domain.services.SqlLiteralQuoter;
import modules.shared.infrastructure.repositories.AbstractJdbcRepository;

import modules.connections.infrastructure.components.httptunnel.HttpTunnelResult;
import modules.connections.infrastructure.components.httptunnel.HttpTunnelSession;

import modules.query.domain.entities.PreparedSqlEntity;

/**
 * Aplica los cambios de la rejilla editable (feature 6) en una transaccion: o todos o ninguno. Directo por JDBC en
 * MySQL, PostgreSQL y SQLite; por el tunel HTTP (feature 13), en un solo lote con una comprobacion por fila.
 */
public final class TableChangesWriterJdbcRepository extends AbstractJdbcRepository {

    // MySQL: dos filas si la fila buscada no esta (@jy_found <> 1) y MySQL lanza 1242 "Subquery returns more than 1 row"
    private static final String MYSQL_TUNNEL_ROW_GUARD = "DO (SELECT 1 FROM DUAL WHERE @jy_found <> 1 UNION ALL SELECT 2 FROM DUAL WHERE @jy_found <> 1)";
    private static final int ER_SUBQUERY_NO_1_ROW = 1242;
    // SQLite: abs() del minimo entero lanza "integer overflow" si la sentencia anterior no toco exactamente una fila
    private static final String SQLITE_TUNNEL_ROW_GUARD = "SELECT CASE WHEN changes() = 1 THEN 1 ELSE abs(-9223372036854775807 - 1) END";
    private static final Pattern KEYED_STATEMENT_PATTERN = Pattern.compile("^(?:UPDATE|DELETE FROM) (`(?:[^`]|``)+`(?:\\.`(?:[^`]|``)+`)?)");
    private static final String ROW_GONE_MESSAGE = "Expected 1 row: a row was deleted or its key changed meanwhile";

    private final SqlDialect sqlDialect;

    private TableChangesWriterJdbcRepository(Connection pdo, SqlDialect sqlDialect) {
        super(pdo);
        this.sqlDialect = sqlDialect;
    }

    public static TableChangesWriterJdbcRepository getInstance(Connection pdo, SqlDialect sqlDialect) {
        return new TableChangesWriterJdbcRepository(pdo, sqlDialect);
    }

    /**
     * Todas las sentencias en una transaccion. Si una falla, o una que va por clave primaria no toca exactamente una
     * fila (otro la borro o cambio su clave mientras tanto), se deshace todo y sube SQLException. Devuelve las filas
     * tocadas en total.
     */
    public int applyInTransaction(List<PreparedSqlEntity> statements) throws SQLException {
        if (pdo.isWrapperFor(HttpTunnelSession.class)) return applyByHttpTunnel(pdo.unwrap(HttpTunnelSession.class), statements);
        return executeInTransaction(() -> {
            int affectedRows = 0;
            for (PreparedSqlEntity statement : statements) {
                int statementRows = executeUpdate(statement);
                if (statement.expectsOneRow() && statementRows != 1) {
                    throw new SQLException("Expected 1 row, affected " + statementRows + ": " + statement.getSql());
                }
                affectedRows += statementRows;
            }
            return affectedRows;
        });
    }

    /**
     * Feature 13: el tunel HTTP no guarda sesion entre peticiones, asi que la transaccion entera va en un lote. Si una
     * fila no esta (otro la borro o cambio su clave), su comprobacion lanza un error, el tunel para ahi sin llegar al
     * COMMIT y su conexion, al cerrarse, deshace lo hecho.
     */
    private int applyByHttpTunnel(HttpTunnelSession httpTunnelSession, List<PreparedSqlEntity> statements) throws SQLException {
        if (sqlDialect.isMysql()) return applyByMysqlTunnel(httpTunnelSession, statements);
        SqlLiteralQuoter sqlLiteralQuoter = SqlLiteralQuoter.getInstance(sqlDialect.getDbEngine());
        List<String> script = new ArrayList<>(List.of("START TRANSACTION"));
        for (PreparedSqlEntity statement : statements) {
            String inlinedSql = sqlLiteralQuoter.getInlined(statement.getSql(), statement.getParams());
            if (!statement.expectsOneRow()) {
                script.add(inlinedSql);
                continue;
            }
            if (sqlDialect.isPgsql()) {
                // 1 / 0 (division_by_zero) si el UPDATE/DELETE no encontro la fila
                script.add("WITH jy_changed AS (" + inlinedSql + " RETURNING 1) SELECT 1 / (SELECT COUNT(*) FROM jy_changed)");
                continue;
            }
            script.add(inlinedSql);
            script.add(SQLITE_TUNNEL_ROW_GUARD);
        }
        script.add("COMMIT");
        try {
            httpTunnelSession.executeBatch(script);
            return statements.size();
        }
        catch (SQLException e) {
            String message = String.valueOf(e.getMessage()).toLowerCase(Locale.ROOT);
            if (message.contains("division by zero") || message.contains("integer overflow")) throw new SQLException(ROW_GONE_MESSAGE, e);
            throw e;
        }
    }

    /**
     * Antes de cada UPDATE/DELETE por clave se cuenta la fila; si no esta, el guard falla con el error 1242. (ROW_COUNT()
     * no vale: por PDO cuenta las filas cambiadas, no las encontradas.)
     */
    private int applyByMysqlTunnel(HttpTunnelSession httpTunnelSession, List<PreparedSqlEntity> statements) throws SQLException {
        SqlLiteralQuoter sqlLiteralQuoter = SqlLiteralQuoter.getInstance();
        List<String> script = new ArrayList<>(List.of("START TRANSACTION", "SET @jy_rows = 0"));
        for (PreparedSqlEntity statement : statements) {
            if (statement.expectsOneRow()) {
                script.add("SET @jy_found = (SELECT COUNT(*) FROM " + getKeyLookup(statement) + ")");
                script.add(MYSQL_TUNNEL_ROW_GUARD);
                script.add(sqlLiteralQuoter.getInlined(statement.getSql(), statement.getParams()));
                script.add("SET @jy_rows = @jy_rows + 1");
                continue;
            }
            script.add(sqlLiteralQuoter.getInlined(statement.getSql(), statement.getParams()));
            script.add("SET @jy_rows = @jy_rows + ROW_COUNT()");
        }
        script.add("COMMIT");
        script.add("SELECT @jy_rows");
        try {
            HttpTunnelResult result = httpTunnelSession.executeBatch(script);
            if (result.rows().isEmpty()) return 0;
            return Integer.parseInt(HttpTunnelResult.getText(result.rows().getFirst().getFirst()));
        }
        catch (SQLException e) {
            if (e.getErrorCode() == ER_SUBQUERY_NO_1_ROW) throw new SQLException(ROW_GONE_MESSAGE, e);
            throw e;
        }
    }

    /**
     * "`bd`.`tabla` WHERE `id` = 7" de un UPDATE/DELETE de MySQL de TableChangesSqlBuilder (... WHERE <clave> LIMIT 1):
     * los parametros del WHERE son los ultimos.
     */
    private String getKeyLookup(PreparedSqlEntity statement) throws SQLException {
        String sql = statement.getSql();
        Matcher tableMatcher = KEYED_STATEMENT_PATTERN.matcher(sql);
        int whereStart = sql.lastIndexOf(" WHERE ");
        int limitStart = sql.lastIndexOf(" LIMIT 1");
        if (!tableMatcher.find() || whereStart == -1 || limitStart < whereStart) {
            throw new SQLException("not a keyed UPDATE/DELETE: " + sql);
        }
        String whereTemplate = sql.substring(whereStart, limitStart);
        int whereParamCount = (int) whereTemplate.chars().filter(character -> character == '?').count();
        List<Object> params = statement.getParams();
        List<Object> whereParams = params.subList(params.size() - whereParamCount, params.size());
        return tableMatcher.group(1) + SqlLiteralQuoter.getInstance().getInlined(whereTemplate, whereParams);
    }

    private int executeUpdate(PreparedSqlEntity statement) throws SQLException {
        try (PreparedStatement preparedStatement = pdo.prepareStatement(statement.getSql())) {
            List<Object> params = statement.getParams();
            for (int i = 0; i < params.size(); i++) {
                preparedStatement.setObject(i + 1, params.get(i));
            }
            return preparedStatement.executeUpdate();
        }
    }
}
