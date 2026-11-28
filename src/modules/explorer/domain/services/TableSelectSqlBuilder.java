package modules.explorer.domain.services;

import java.util.ArrayList;
import java.util.List;

import modules.shared.domain.services.SqlDialect;
import modules.shared.domain.services.SqlIdentifierQuoter;

/**
 * El "SELECT en pestaña nueva" del arbol, como en SQLyog: una columna por linea, para quitar o reordenar a mano. Sin
 * columnas (no se pudieron leer), SELECT *. En SQL Server, SELECT TOP n en vez de LIMIT.
 * <pre>
 * SELECT
 *     `id`,
 *     `email`
 * FROM `bd`.`tabla`
 * LIMIT 1000;
 * </pre>
 */
public final class TableSelectSqlBuilder {

    private static final String COLUMN_INDENT = "    ";

    private final SqlDialect sqlDialect;
    private final SqlIdentifierQuoter sqlIdentifierQuoter;

    private TableSelectSqlBuilder(String dbEngine) {
        this.sqlDialect = SqlDialect.getInstance(dbEngine);
        this.sqlIdentifierQuoter = SqlIdentifierQuoter.getInstance(dbEngine);
    }

    public static TableSelectSqlBuilder getInstance(String dbEngine) {
        return new TableSelectSqlBuilder(dbEngine);
    }

    /**
     * limit <= 0: sin limite.
     */
    public String getSelectSql(String databaseName, String tableName, List<String> columnNames, int limit) {
        List<String> quotedColumns = new ArrayList<>();
        for (String columnName : columnNames) {
            quotedColumns.add(COLUMN_INDENT + sqlIdentifierQuoter.getQuoted(columnName));
        }
        boolean hasLimit = limit > 0;
        String top = hasLimit && sqlDialect.isTopLimit() ? " TOP " + limit : "";
        String columns = quotedColumns.isEmpty() ? COLUMN_INDENT + "*" : String.join(",\n", quotedColumns);
        StringBuilder selectSql = new StringBuilder("SELECT").append(top).append('\n').append(columns)
            .append("\nFROM ").append(sqlIdentifierQuoter.getQuoted(databaseName, tableName));
        if (hasLimit && !sqlDialect.isTopLimit()) selectSql.append("\nLIMIT ").append(limit);
        return selectSql.append(';').toString();
    }
}
