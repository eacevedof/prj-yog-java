package modules.dbcopy.infrastructure.repositories;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import modules.shared.domain.services.SqlDialect;
import modules.shared.infrastructure.components.sqltrace.SqlTraceComponent;
import modules.shared.infrastructure.repositories.AbstractJdbcRepository;

import modules.connections.infrastructure.components.httptunnel.HttpTunnelResult;
import modules.connections.infrastructure.components.httptunnel.HttpTunnelSession;

import modules.dbcopy.domain.services.DbCopySqlBuilder;

/**
 * Copia entre motores: las filas del origen (cualquier motor SQL) por paginas, sobre una sesion propia. Cada celda
 * llega como texto (getString) o, en las columnas binarias, como bytes: PortableCopySqlBuilder la convierte en el
 * literal del destino. Asi no importa que tipo Java de cada driver. Por el tunel HTTP la pagina va en un lote (con la
 * zona horaria delante en MySQL, porque cada peticion es una sesion nueva).
 */
public final class DbCopyRowsReaderJdbcRepository extends AbstractJdbcRepository {

    private final HttpTunnelSession httpTunnelSession;
    private final boolean isMysql;

    private DbCopyRowsReaderJdbcRepository(Connection pdo, String dbEngine) throws SQLException {
        super(pdo, dbEngine);
        this.httpTunnelSession = pdo.isWrapperFor(HttpTunnelSession.class) ? pdo.unwrap(HttpTunnelSession.class) : null;
        this.isMysql = SqlDialect.getInstance(dbEngine).isMysql();
    }

    public static DbCopyRowsReaderJdbcRepository getInstance(Connection pdo, String dbEngine) throws SQLException {
        return new DbCopyRowsReaderJdbcRepository(pdo, dbEngine);
    }

    /**
     * MySQL: la zona horaria a UTC (un TIMESTAMP viaja como texto). Por el tunel va con cada pagina.
     */
    public void prepareSession() throws SQLException {
        if (!isMysql || httpTunnelSession != null) return;
        SqlTraceComponent.getInstance().addSql(DbCopySqlBuilder.SOURCE_SESSION_SQL);
        try (Statement statement = pdo.createStatement()) {
            statement.execute(DbCopySqlBuilder.SOURCE_SESSION_SQL);
        }
    }

    /**
     * Las filas del SELECT de una pagina; binaryColumns[i]: la columna i se lee en bytes.
     */
    public List<List<Object>> getRows(String selectSql, boolean[] binaryColumns) throws SQLException {
        SqlTraceComponent sqlTraceComponent = SqlTraceComponent.getInstance();
        sqlTraceComponent.addSql(selectSql);
        List<List<Object>> rows = new ArrayList<>();
        if (httpTunnelSession != null) {
            List<String> batch = isMysql ? List.of(DbCopySqlBuilder.SOURCE_SESSION_SQL, selectSql) : List.of(selectSql);
            HttpTunnelResult httpTunnelResult = httpTunnelSession.executeBatch(batch);
            for (List<byte[]> tunnelRow : httpTunnelResult.rows()) {
                List<Object> row = new ArrayList<>(binaryColumns.length);
                for (int i = 0; i < binaryColumns.length; i++) {
                    byte[] cell = tunnelRow.get(i);
                    row.add(cell == null || binaryColumns[i] ? cell : new String(cell, StandardCharsets.UTF_8));
                }
                rows.add(row);
            }
            sqlTraceComponent.addRowsCount(rows.size());
            return rows;
        }
        try (Statement statement = pdo.createStatement(); ResultSet resultSet = statement.executeQuery(selectSql)) {
            while (resultSet.next()) {
                List<Object> row = new ArrayList<>(binaryColumns.length);
                for (int i = 0; i < binaryColumns.length; i++) {
                    row.add(binaryColumns[i] ? resultSet.getBytes(i + 1) : resultSet.getString(i + 1));
                }
                rows.add(row);
            }
        }
        sqlTraceComponent.addRowsCount(rows.size());
        return rows;
    }
}
