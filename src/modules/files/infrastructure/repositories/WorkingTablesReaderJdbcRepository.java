package modules.files.infrastructure.repositories;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import modules.shared.domain.services.SqlDialect;
import modules.shared.domain.services.SqlIdentifierQuoter;
import modules.shared.infrastructure.repositories.AbstractJdbcRepository;

/**
 * El SQLite de trabajo de una conexion "Ficheros": sus tablas y, de cada una, columnas y filas en el orden en que se
 * cargaron (por rowid).
 */
public final class WorkingTablesReaderJdbcRepository extends AbstractJdbcRepository {

    private final SqlIdentifierQuoter sqlIdentifierQuoter = SqlIdentifierQuoter.getInstance(SqlDialect.SQLITE);

    private WorkingTablesReaderJdbcRepository(Connection pdo) {
        super(pdo, SqlDialect.SQLITE);
    }

    public static WorkingTablesReaderJdbcRepository getInstance(Connection pdo) {
        return new WorkingTablesReaderJdbcRepository(pdo);
    }

    public List<String> getTableNames() throws SQLException {
        String sql = """
            -- getTableNames
            SELECT name
            FROM sqlite_master
            WHERE 1=1
            AND type = 'table'
            AND name NOT LIKE 'sqlite_%'
            ORDER BY name
            """;
        List<String> tableNames = new ArrayList<>();
        for (Map<String, Object> row : query(sql)) tableNames.add(String.valueOf(row.get("name")));
        return tableNames;
    }

    public List<String> getColumnNames(String tableName) throws SQLException {
        List<String> columnNames = new ArrayList<>();
        for (Map<String, Object> column : query("PRAGMA table_info(" + sqlIdentifierQuoter.getQuoted(tableName) + ")")) {
            columnNames.add(String.valueOf(column.get("name")));
        }
        return columnNames;
    }

    /**
     * Las filas como listas de valores, en el orden de getColumnNames.
     */
    public List<List<Object>> getRows(String tableName) throws SQLException {
        String sql = "SELECT * FROM " + sqlIdentifierQuoter.getQuoted(tableName) + " ORDER BY rowid";
        List<List<Object>> rows = new ArrayList<>();
        for (Map<String, Object> row : query(sql)) rows.add(new ArrayList<>(row.values()));
        return rows;
    }
}
