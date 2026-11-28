package modules.explorer.domain.services;

import modules.shared.domain.services.SqlIdentifierQuoter;

/**
 * "Eliminar tabla" del arbol (y deshacer una copia a medias de "Duplicar tabla"): DROP TABLE con la BD delante,
 * entrecomillado segun el motor.
 */
public final class TableDropSqlBuilder {

    private final SqlIdentifierQuoter sqlIdentifierQuoter;

    private TableDropSqlBuilder(String dbEngine) {
        this.sqlIdentifierQuoter = SqlIdentifierQuoter.getInstance(dbEngine);
    }

    public static TableDropSqlBuilder getInstance(String dbEngine) {
        return new TableDropSqlBuilder(dbEngine);
    }

    public String getDropSql(String databaseName, String tableName) {
        return "DROP TABLE " + sqlIdentifierQuoter.getQuoted(databaseName, tableName);
    }
}
