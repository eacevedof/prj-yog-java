package modules.shared.domain.services;

/**
 * Un identificador entre comillas con las de dentro dobladas: `nombre` en MySQL, "nombre" en PostgreSQL y SQLite,
 * [nombre] en SQL Server (la ] de dentro, doblada). Vale
 * para cualquier identificador (espacios, guiones, palabras reservadas). Los identificadores no se pueden enlazar con ?
 * en JDBC: cuando un nombre va dentro del SQL, pasa por aqui.
 */
public final class SqlIdentifierQuoter {

    private final SqlDialect sqlDialect;
    private final String quote;
    private final String closeQuote;

    private SqlIdentifierQuoter(SqlDialect sqlDialect) {
        this.sqlDialect = sqlDialect;
        this.quote = String.valueOf(sqlDialect.getIdentifierQuote());
        this.closeQuote = String.valueOf(sqlDialect.getIdentifierCloseQuote());
    }

    /**
     * MySQL (comillas invertidas).
     */
    public static SqlIdentifierQuoter getInstance() {
        return new SqlIdentifierQuoter(SqlDialect.getInstance(SqlDialect.MYSQL));
    }

    public static SqlIdentifierQuoter getInstance(String dbEngine) {
        return new SqlIdentifierQuoter(SqlDialect.getInstance(dbEngine));
    }

    public String getQuoted(String identifier) {
        return quote + identifier.replace(closeQuote, closeQuote + closeQuote) + closeQuote;
    }

    /**
     * `bd`.`tabla` (en PostgreSQL "esquema"."tabla"). En SQLite, la BD main no se pone: es la de por defecto y, por el
     * tunel PHP, la unica. En SQL Server, [bd].[esquema].[objeto]: "ventas.pedidos" es del esquema ventas y un objeto
     * sin punto, de dbo.
     */
    public String getQuoted(String databaseName, String objectName) {
        if (sqlDialect.isMssql()) {
            int dotIndex = objectName.indexOf('.');
            String schemaName = dotIndex == -1 ? SqlDialect.MSSQL_DEFAULT_SCHEMA : objectName.substring(0, dotIndex);
            String quotedObject = getQuoted(schemaName) + "." + getQuoted(objectName.substring(dotIndex + 1));
            return databaseName == null || databaseName.isEmpty() ? quotedObject : getQuoted(databaseName) + "." + quotedObject;
        }
        if (sqlDialect.isSqlite() && (databaseName == null || databaseName.isEmpty() || SqlDialect.SQLITE_MAIN.equals(databaseName))) {
            return getQuoted(objectName);
        }
        return getQuoted(databaseName) + "." + getQuoted(objectName);
    }
}
