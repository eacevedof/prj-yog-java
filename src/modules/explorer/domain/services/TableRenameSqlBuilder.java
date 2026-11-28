package modules.explorer.domain.services;

import modules.shared.domain.services.SqlDialect;
import modules.shared.domain.services.SqlIdentifierQuoter;

/**
 * F2 en el arbol: renombrar una tabla o una columna, con el SQL de cada motor.
 * <ul>
 * <li>MySQL: RENAME TABLE y ALTER TABLE ... RENAME COLUMN (MySQL 8 / MariaDB 10.5).</li>
 * <li>PostgreSQL y SQLite: ALTER TABLE ... RENAME TO y RENAME COLUMN; el nombre nuevo va sin esquema.</li>
 * <li>SQL Server: sp_rename de la BD de la tabla; el esquema no cambia, solo el nombre.</li>
 * </ul>
 */
public final class TableRenameSqlBuilder {

    private final SqlDialect sqlDialect;
    private final SqlIdentifierQuoter sqlIdentifierQuoter;

    private TableRenameSqlBuilder(String dbEngine) {
        this.sqlDialect = SqlDialect.getInstance(dbEngine);
        this.sqlIdentifierQuoter = SqlIdentifierQuoter.getInstance(dbEngine);
    }

    public static TableRenameSqlBuilder getInstance(String dbEngine) {
        return new TableRenameSqlBuilder(dbEngine);
    }

    public String getRenameTableSql(String databaseName, String tableName, String newTableName) {
        String quotedTable = sqlIdentifierQuoter.getQuoted(databaseName, tableName);
        if (sqlDialect.isMysql()) {
            return "RENAME TABLE " + quotedTable + " TO " + sqlIdentifierQuoter.getQuoted(databaseName, newTableName);
        }
        if (sqlDialect.isMssql()) {
            return getSpRename(databaseName, getMssqlQualified(tableName), getObjectPart(newTableName), "");
        }
        return "ALTER TABLE " + quotedTable + " RENAME TO " + sqlIdentifierQuoter.getQuoted(newTableName);
    }

    public String getRenameColumnSql(String databaseName, String tableName, String columnName, String newColumnName) {
        if (sqlDialect.isMssql()) {
            return getSpRename(databaseName, getMssqlQualified(tableName) + "." + sqlIdentifierQuoter.getQuoted(columnName), newColumnName, "COLUMN");
        }
        return "ALTER TABLE " + sqlIdentifierQuoter.getQuoted(databaseName, tableName)
            + " RENAME COLUMN " + sqlIdentifierQuoter.getQuoted(columnName)
            + " TO " + sqlIdentifierQuoter.getQuoted(newColumnName);
    }

    /**
     * SQL Server: el nombre nuevo de una tabla no lleva esquema ("ventas.pedidos2" -> "pedidos2").
     */
    public String getObjectPart(String tableName) {
        int dotIndex = tableName.indexOf('.');
        return dotIndex == -1 ? tableName : tableName.substring(dotIndex + 1);
    }

    /**
     * SQL Server: el esquema de una tabla ("pedidos" es de dbo).
     */
    public String getSchemaPart(String tableName) {
        int dotIndex = tableName.indexOf('.');
        return dotIndex == -1 ? SqlDialect.MSSQL_DEFAULT_SCHEMA : tableName.substring(0, dotIndex);
    }

    private String getMssqlQualified(String tableName) {
        return sqlIdentifierQuoter.getQuoted(getSchemaPart(tableName)) + "." + sqlIdentifierQuoter.getQuoted(getObjectPart(tableName));
    }

    private String getSpRename(String databaseName, String objectName, String newName, String objectType) {
        String procedure = databaseName == null || databaseName.isEmpty()
            ? "sp_rename"
            : sqlIdentifierQuoter.getQuoted(databaseName) + ".sys.sp_rename";
        String sql = "EXEC " + procedure + " " + getNString(objectName) + ", " + getNString(newName);
        return objectType.isEmpty() ? sql : sql + ", " + getNString(objectType);
    }

    private String getNString(String value) {
        return "N'" + value.replace("'", "''") + "'";
    }
}
