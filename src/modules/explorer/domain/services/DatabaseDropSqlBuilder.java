package modules.explorer.domain.services;

import java.util.List;

import modules.shared.domain.services.SqlDialect;
import modules.shared.domain.services.SqlIdentifierQuoter;

/**
 * "Eliminar BD" del arbol: MySQL/MariaDB DROP DATABASE; PostgreSQL DROP SCHEMA ... CASCADE (en el arbol la "BD" es un
 * esquema, y CASCADE se lleva sus tablas); SQL Server echa antes las otras sesiones (SINGLE_USER WITH ROLLBACK
 * IMMEDIATE) y se pasa a master, porque no se puede borrar una BD en uso. SQLite no borra BDs.
 */
public final class DatabaseDropSqlBuilder {

    private static final String MSSQL_MASTER = "master";

    private final String dbEngine;
    private final SqlIdentifierQuoter sqlIdentifierQuoter;

    private DatabaseDropSqlBuilder(String dbEngine) {
        this.dbEngine = dbEngine;
        this.sqlIdentifierQuoter = SqlIdentifierQuoter.getInstance(dbEngine);
    }

    public static DatabaseDropSqlBuilder getInstance(String dbEngine) {
        return new DatabaseDropSqlBuilder(dbEngine);
    }

    public boolean isSupported() {
        return SqlDialect.MYSQL.equals(dbEngine) || SqlDialect.PGSQL.equals(dbEngine) || SqlDialect.MSSQL.equals(dbEngine);
    }

    /**
     * Vacio si el motor no borra BDs.
     */
    public List<String> getDropStatements(String databaseName) {
        String quotedName = sqlIdentifierQuoter.getQuoted(databaseName);
        return switch (dbEngine) {
            case SqlDialect.MYSQL -> List.of("DROP DATABASE " + quotedName);
            case SqlDialect.PGSQL -> List.of("DROP SCHEMA " + quotedName + " CASCADE");
            case SqlDialect.MSSQL -> List.of(
                "USE " + sqlIdentifierQuoter.getQuoted(MSSQL_MASTER),
                "ALTER DATABASE " + quotedName + " SET SINGLE_USER WITH ROLLBACK IMMEDIATE",
                "DROP DATABASE " + quotedName
            );
            default -> List.of();
        };
    }
}
