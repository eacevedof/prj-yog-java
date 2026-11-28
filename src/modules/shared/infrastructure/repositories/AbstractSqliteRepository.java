package modules.shared.infrastructure.repositories;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import modules.shared.application.Primitives;
import modules.shared.infrastructure.factories.DbFactory;

/**
 * La BD local de la app (SQLite). Las sentencias van siempre con parametros enlazados (?). Un SQLException (checked)
 * sube como IllegalStateException: un repositorio no captura, el fallo llega al catch del controller.
 */
public abstract class AbstractSqliteRepository {

    protected final Connection pdo;

    protected AbstractSqliteRepository() {
        this.pdo = DbFactory.getInstance().getSqlitePdo();
    }

    /**
     * SELECT con parametros enlazados (?). Devuelve las filas como mapas (columna => valor).
     */
    protected List<Map<String, Object>> query(String sql, Object... params) {
        synchronized (pdo) {
            return getRows(sql, params);
        }
    }

    private List<Map<String, Object>> getRows(String sql, Object... params) {
        try (PreparedStatement preparedStatement = getPreparedStatement(sql, params);
             ResultSet resultSet = preparedStatement.executeQuery()) {
            ResultSetMetaData resultSetMetaData = resultSet.getMetaData();
            List<Map<String, Object>> rows = new ArrayList<>();
            while (resultSet.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= resultSetMetaData.getColumnCount(); i++) {
                    row.put(resultSetMetaData.getColumnLabel(i), resultSet.getObject(i));
                }
                rows.add(row);
            }
            return rows;
        }
        catch (SQLException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    /**
     * INSERT/UPDATE/DELETE con parametros enlazados. Devuelve las filas afectadas.
     */
    protected int execute(String sql, Object... params) {
        synchronized (pdo) {
            try (PreparedStatement preparedStatement = getPreparedStatement(sql, params)) {
                return preparedStatement.executeUpdate();
            }
            catch (SQLException e) {
                throw new IllegalStateException(e.getMessage(), e);
            }
        }
    }

    /**
     * Varias sentencias de golpe (un fichero de migracion). Sin parametros: solo SQL de confianza. JDBC ejecuta una
     * sentencia cada vez: se trocea por ";". Las migraciones no llevan ";" dentro de literales ni triggers.
     */
    protected void executeRaw(String sql) {
        synchronized (pdo) {
            try (Statement statement = pdo.createStatement()) {
                for (String singleSql : sql.split(";")) {
                    if (!singleSql.isBlank()) {
                        statement.execute(singleSql.trim());
                    }
                }
            }
            catch (SQLException e) {
                throw new IllegalStateException(e.getMessage(), e);
            }
        }
    }

    /**
     * Todo lo que haga work en una transaccion: COMMIT si acaba, ROLLBACK y relanza si falla (como
     * AbstractJdbcRepository.executeInTransaction). La conexion SQLite es compartida: sin el bloqueo, lo que otro hilo
     * escribiera entraria en esta transaccion.
     */
    protected void executeInTransaction(Runnable work) {
        synchronized (pdo) {
            try {
                boolean wasAutoCommit = pdo.getAutoCommit();
                pdo.setAutoCommit(false);
                try {
                    work.run();
                    pdo.commit();
                }
                catch (RuntimeException e) {
                    pdo.rollback();
                    throw e;
                }
                finally {
                    pdo.setAutoCommit(wasAutoCommit);
                }
            }
            catch (SQLException e) {
                throw new IllegalStateException(e.getMessage(), e);
            }
        }
    }

    /**
     * INSERT y el id de la fila insertada (el lastInsertId() de PDO), sin que otro hilo inserte en medio: todos los
     * repositorios comparten la conexion SQLite y el historial o el espacio de trabajo se guardan en segundo plano; con
     * el INSERT y el last_insert_rowid() por separado, un alta podia devolver el id de la fila de otro hilo.
     */
    protected int insertAndGetId(String sql, Object... params) {
        synchronized (pdo) {
            execute(sql, params);
            List<Map<String, Object>> rows = getRows("SELECT last_insert_rowid() AS last_insert_id");
            return Primitives.getInt(rows.getFirst().get("last_insert_id"));
        }
    }

    private PreparedStatement getPreparedStatement(String sql, Object[] params) throws SQLException {
        PreparedStatement preparedStatement = pdo.prepareStatement(sql);
        for (int i = 0; i < params.length; i++) {
            preparedStatement.setObject(i + 1, params[i]);
        }
        return preparedStatement;
    }
}
