package modules.files.infrastructure.repositories;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import modules.shared.domain.services.SqlDialect;
import modules.shared.domain.services.SqlIdentifierQuoter;
import modules.shared.infrastructure.repositories.AbstractJdbcRepository;

/**
 * Carga un fichero en el SQLite de trabajo: la tabla se crea de nuevo con la columna clave delante (rowColumn,
 * INTEGER PRIMARY KEY, para que la rejilla pueda editarla) y sus filas, todo en una transaccion.
 */
public final class WorkingTablesWriterJdbcRepository extends AbstractJdbcRepository {

    private final SqlIdentifierQuoter sqlIdentifierQuoter = SqlIdentifierQuoter.getInstance(SqlDialect.SQLITE);

    private WorkingTablesWriterJdbcRepository(Connection pdo) {
        super(pdo, SqlDialect.SQLITE);
    }

    public static WorkingTablesWriterJdbcRepository getInstance(Connection pdo) {
        return new WorkingTablesWriterJdbcRepository(pdo);
    }

    public void replaceTable(String tableName, String rowColumn, List<String> columnNames, List<String> sqlTypes, List<List<Object>> rows) throws SQLException {
        String quotedTable = sqlIdentifierQuoter.getQuoted(tableName);
        List<String> columnDefinitions = new ArrayList<>();
        columnDefinitions.add(sqlIdentifierQuoter.getQuoted(rowColumn) + " INTEGER PRIMARY KEY");
        List<String> quotedColumns = new ArrayList<>();
        for (int i = 0; i < columnNames.size(); i++) {
            String quotedColumn = sqlIdentifierQuoter.getQuoted(columnNames.get(i));
            quotedColumns.add(quotedColumn);
            columnDefinitions.add(quotedColumn + " " + sqlTypes.get(i));
        }
        String createSql = "CREATE TABLE " + quotedTable + " (" + String.join(", ", columnDefinitions) + ")";
        String insertSql = columnNames.isEmpty() ? "" : "INSERT INTO " + quotedTable + " (" + String.join(", ", quotedColumns) + ") VALUES ("
            + String.join(", ", Collections.nCopies(columnNames.size(), "?")) + ")";
        executeInTransaction(() -> {
            try (Statement statement = pdo.createStatement()) {
                statement.execute("DROP TABLE IF EXISTS " + quotedTable);
                statement.execute(createSql);
            }
            if (insertSql.isEmpty() || rows.isEmpty()) return null;
            try (PreparedStatement preparedStatement = pdo.prepareStatement(insertSql)) {
                for (List<Object> row : rows) {
                    for (int i = 0; i < columnNames.size(); i++) preparedStatement.setObject(i + 1, i < row.size() ? row.get(i) : null);
                    preparedStatement.addBatch();
                }
                preparedStatement.executeBatch();
            }
            return null;
        });
    }

    public void dropTable(String tableName) throws SQLException {
        try (Statement statement = pdo.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS " + sqlIdentifierQuoter.getQuoted(tableName));
        }
    }
}
