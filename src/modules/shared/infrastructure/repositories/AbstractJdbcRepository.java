package modules.shared.infrastructure.repositories;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import modules.shared.domain.services.SqlDialect;
import modules.shared.domain.services.SqlLiteralQuoter;
import modules.shared.infrastructure.components.sqltrace.SqlTraceComponent;

/**
 * La BD destino (MySQL, PostgreSQL o SQLite de una conexion guardada) sobre una sesion ya abierta. A diferencia de la BD local, aqui un
 * fallo es normal (permisos, BD borrada, red): sube como SQLException, sin traducir, hasta el controller. Solo
 * executeInTransaction la captura, para el ROLLBACK, y la relanza.
 */
public abstract class AbstractJdbcRepository {

    protected final Connection pdo;
    private final SqlLiteralQuoter traceLiteralQuoter;

    /**
     * MySQL: la traza del historial pone los ? como literales de MySQL.
     */
    protected AbstractJdbcRepository(Connection pdo) {
        this(pdo, SqlDialect.MYSQL);
    }

    /**
     * Con el motor de la sesion: la traza del historial pone los ? como literales de ese motor (en PostgreSQL o SQL
     * Server, 'it''s' y no 'it\'s'), asi el SQL que se ve es el que se puede volver a lanzar.
     */
    protected AbstractJdbcRepository(Connection pdo, String dbEngine) {
        this.pdo = pdo;
        this.traceLiteralQuoter = SqlLiteralQuoter.getInstance(dbEngine);
    }

    /**
     * El metodo transaccional de los repositorios: lo que haga work va en una transaccion JDBC; si termina, COMMIT, y
     * si lanza, ROLLBACK y la excepcion sigue subiendo tal cual. Es el unico sitio de un repositorio que captura
     * (decision de Eduardo, 2026-11-25, DDD 18): asi ningun repositorio repite su try/catch/finally. Por el tunel HTTP
     * no sirve (no hay transacciones entre peticiones): alli lo atomico va en un lote START TRANSACTION ... COMMIT.
     */
    protected <T> T executeInTransaction(TransactionWork<T> work) throws SQLException {
        boolean wasAutoCommit = pdo.getAutoCommit();
        pdo.setAutoCommit(false);
        try {
            T result = work.run();
            pdo.commit();
            return result;
        }
        catch (SQLException | RuntimeException e) {
            pdo.rollback();
            throw e;
        }
        finally {
            pdo.setAutoCommit(wasAutoCommit);
        }
    }

    /**
     * SELECT con parametros enlazados (?). Las filas como mapas (columna => valor).
     * Si hay traza abierta (feature 8), el SQL con los parametros ya puestos y las filas leidas van a ella.
     */
    protected List<Map<String, Object>> query(String sql, Object... params) throws SQLException {
        SqlTraceComponent sqlTraceComponent = SqlTraceComponent.getInstance();
        if (sqlTraceComponent.isTracing()) {
            sqlTraceComponent.addSql(params.length == 0 ? sql : traceLiteralQuoter.getInlined(sql, Arrays.asList(params)));
        }
        try (PreparedStatement preparedStatement = pdo.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                preparedStatement.setObject(i + 1, params[i]);
            }
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                ResultSetMetaData resultSetMetaData = resultSet.getMetaData();
                List<Map<String, Object>> rows = new ArrayList<>();
                while (resultSet.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= resultSetMetaData.getColumnCount(); i++) {
                        row.put(resultSetMetaData.getColumnLabel(i), resultSet.getObject(i));
                    }
                    rows.add(row);
                }
                sqlTraceComponent.addRowsCount(rows.size());
                return rows;
            }
        }
    }

    /**
     * Lo que se ejecuta dentro de executeInTransaction.
     */
    @FunctionalInterface
    protected interface TransactionWork<T> {
        T run() throws SQLException;
    }
}
