package modules.query.infrastructure.repositories;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import modules.shared.domain.services.SqlDialect;
import modules.shared.application.Primitives;

import modules.query.domain.enums.QueryKeyEnum;
import modules.query.infrastructure.components.RunningStatementsComponent;

/**
 * Ejecuta el SQL del usuario tal cual, en MySQL, PostgreSQL o SQLite (no admite parametros: es lo que se escribe en el editor) sobre la sesion de una
 * pestaña. Los valores se leen como texto para la rejilla; NULL llega como null.
 */
public final class QueryExecutorJdbcRepository {

    private static final int MAX_HEX_BYTES = 32;

    private final Connection pdo;
    private final SqlDialect sqlDialect;

    private QueryExecutorJdbcRepository(Connection pdo, SqlDialect sqlDialect) {
        this.pdo = pdo;
        this.sqlDialect = sqlDialect;
    }

    public static QueryExecutorJdbcRepository getInstance(Connection pdo, SqlDialect sqlDialect) {
        return new QueryExecutorJdbcRepository(pdo, sqlDialect);
    }

    /**
     * La BD activa (en PostgreSQL, el esquema del search_path), preguntada al servidor: con SELECT DATABASE() y no
     * getCatalog(), porque Connector/J cachea el catalogo y no se entera de un USE lanzado como SQL.
     */
    public String getCurrentDatabase() throws SQLException {
        try (Statement statement = pdo.createStatement();
             ResultSet resultSet = statement.executeQuery(sqlDialect.getCurrentDatabaseSql())) {
            String databaseName = resultSet.next() ? resultSet.getString(1) : null;
            return databaseName == null ? "" : databaseName;
        }
    }

    /**
     * El USE de la pestaña: la BD activa que se eligio en el arbol.
     */
    public void useDatabase(String databaseName) throws SQLException {
        if (sqlDialect.isSqlite()) return;
        if (sqlDialect.isPgsql()) {
            pdo.setSchema(databaseName);
            return;
        }
        pdo.setCatalog(databaseName);
    }

    /**
     * Una sentencia puede devolver varios resultados (un CALL): uno por cada result set o recuento de filas.
     * maxRows > 0 limita las filas que se leen de cada result set.
     */
    public List<Map<String, Object>> executeStatement(String sql, int maxRows, String sessionKey) throws SQLException {
        RunningStatementsComponent runningStatementsComponent = RunningStatementsComponent.getInstance();
        List<Map<String, Object>> results = new ArrayList<>();
        long startNanos = System.nanoTime();

        try (Statement statement = pdo.createStatement()) {
            if (maxRows > 0) {
                statement.setMaxRows(maxRows);
            }
            runningStatementsComponent.register(sessionKey, statement);
            // Cancelar pulsado justo antes de registrar la sentencia: ya no se lanza
            if (runningStatementsComponent.isCancelled(sessionKey)) return results;
            boolean hasResultSet = statement.execute(sql);
            while (true) {
                if (hasResultSet) {
                    try (ResultSet resultSet = statement.getResultSet()) {
                        results.add(getResultSetResult(sql, resultSet, maxRows, startNanos));
                    }
                }
                else {
                    int updateCount = statement.getUpdateCount();
                    if (updateCount == -1) break;
                    results.add(getUpdateResult(sql, updateCount, startNanos));
                }
                hasResultSet = statement.getMoreResults();
            }
        }
        finally {
            runningStatementsComponent.unregister(sessionKey);
        }
        return results;
    }

    private Map<String, Object> getResultSetResult(String sql, ResultSet resultSet, int maxRows, long startNanos) throws SQLException {
        ResultSetMetaData resultSetMetaData = resultSet.getMetaData();
        int columnCount = resultSetMetaData.getColumnCount();
        List<String> columnNames = new ArrayList<>();
        for (int i = 1; i <= columnCount; i++) {
            columnNames.add(resultSetMetaData.getColumnLabel(i));
        }

        List<List<String>> rows = new ArrayList<>();
        while (resultSet.next()) {
            List<String> row = new ArrayList<>(columnCount);
            for (int i = 1; i <= columnCount; i++) {
                row.add(getCellText(resultSet, i, resultSetMetaData.getColumnType(i)));
            }
            rows.add(row);
        }

        return Primitives.of(
            QueryKeyEnum.SQL, sql,
            QueryKeyEnum.HAS_RESULT_SET, true,
            QueryKeyEnum.COLUMN_NAMES, columnNames,
            QueryKeyEnum.ROWS, rows,
            QueryKeyEnum.AFFECTED_ROWS, 0,
            QueryKeyEnum.IS_TRUNCATED, maxRows > 0 && rows.size() >= maxRows,
            QueryKeyEnum.ELAPSED_MS, getElapsedMs(startNanos)
        );
    }

    private Map<String, Object> getUpdateResult(String sql, int updateCount, long startNanos) {
        return Primitives.of(
            QueryKeyEnum.SQL, sql,
            QueryKeyEnum.HAS_RESULT_SET, false,
            QueryKeyEnum.COLUMN_NAMES, List.of(),
            QueryKeyEnum.ROWS, List.of(),
            QueryKeyEnum.AFFECTED_ROWS, updateCount,
            QueryKeyEnum.IS_TRUNCATED, false,
            QueryKeyEnum.ELAPSED_MS, getElapsedMs(startNanos)
        );
    }

    /**
     * Binarios: los cortos (un UUID en BINARY(16)) en hexadecimal; los largos, solo su tamaño.
     */
    private String getCellText(ResultSet resultSet, int columnIndex, int columnType) throws SQLException {
        switch (columnType) {
            case Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY, Types.BLOB -> {
                byte[] bytes = resultSet.getBytes(columnIndex);
                if (bytes == null) return null;
                if (bytes.length <= MAX_HEX_BYTES) return "0x" + HexFormat.of().formatHex(bytes).toUpperCase();
                return "(BLOB) " + bytes.length + " bytes";
            }
            case Types.BOOLEAN -> {
                boolean isTrue = resultSet.getBoolean(columnIndex);
                return resultSet.wasNull() ? null : String.valueOf(isTrue);
            }
            case Types.BIT -> {
                // en PostgreSQL un boolean llega como BIT
                if (sqlDialect.isPgsql() && resultSet.getMetaData().getColumnTypeName(columnIndex).startsWith("bool")) {
                    boolean isTrue = resultSet.getBoolean(columnIndex);
                    return resultSet.wasNull() ? null : String.valueOf(isTrue);
                }
                long bits = resultSet.getLong(columnIndex);
                return resultSet.wasNull() ? null : String.valueOf(bits);
            }
            default -> {
                return resultSet.getString(columnIndex);
            }
        }
    }

    private long getElapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
