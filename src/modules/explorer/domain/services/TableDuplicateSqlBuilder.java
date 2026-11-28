package modules.explorer.domain.services;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import modules.shared.domain.services.SqlDialect;
import modules.shared.domain.services.SqlIdentifierQuoter;

/**
 * "Duplicar tabla" del arbol: estructura y datos en una tabla nueva &lt;tabla&gt;_copy_&lt;ymdhis&gt; de la misma BD.
 * <ul>
 * <li>MySQL: CREATE TABLE ... LIKE (indices, AUTO_INCREMENT y comentarios) + INSERT ... SELECT.</li>
 * <li>PostgreSQL: CREATE TABLE (LIKE ... INCLUDING ALL) + INSERT OVERRIDING SYSTEM VALUE (columnas identity).</li>
 * <li>SQLite: el CREATE TABLE guardado con el nombre nuevo (sin sus indices ni triggers) + INSERT ... SELECT.</li>
 * <li>SQL Server: SELECT ... INTO (columnas e IDENTITY) y la clave primaria aparte: los nombres de las restricciones
 * son unicos por esquema, asi que no se puede repetir el DDL.</li>
 * </ul>
 */
public final class TableDuplicateSqlBuilder {

    private static final String COPY_SUFFIX = "_copy_";
    private static final Pattern NEXT_STATEMENT_PATTERN = Pattern.compile(";\\s*\\n(?=CREATE\\s)", Pattern.CASE_INSENSITIVE);
    private static final Pattern CREATE_TABLE_NAME_PATTERN = Pattern.compile(
        "^\\s*CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?(?:\"(?:[^\"]|\"\")*\"|`(?:[^`]|``)*`|\\[[^\\]]*]|[^\\s(]+)",
        Pattern.CASE_INSENSITIVE
    );

    private final SqlDialect sqlDialect;
    private final SqlIdentifierQuoter sqlIdentifierQuoter;
    private final TableDropSqlBuilder tableDropSqlBuilder;

    private TableDuplicateSqlBuilder(String dbEngine) {
        this.sqlDialect = SqlDialect.getInstance(dbEngine);
        this.sqlIdentifierQuoter = SqlIdentifierQuoter.getInstance(dbEngine);
        this.tableDropSqlBuilder = TableDropSqlBuilder.getInstance(dbEngine);
    }

    public static TableDuplicateSqlBuilder getInstance(String dbEngine) {
        return new TableDuplicateSqlBuilder(dbEngine);
    }

    /**
     * clientes + "2026-11-26 15:30:45" -> clientes_copy_20261126153045.
     */
    public String getCopyTableName(String tableName, String nowDt) {
        return tableName + COPY_SUFFIX + nowDt.replaceAll("\\D", "");
    }

    /**
     * Las sentencias, en orden. sqliteCreateSql: el DDL de la tabla original (solo SQLite); primaryKeyColumns: su clave
     * primaria (solo SQL Server).
     * @throws IllegalArgumentException si en SQLite no llega el CREATE TABLE
     */
    public List<String> getStatements(
        String databaseName,
        String tableName,
        String copyTableName,
        String sqliteCreateSql,
        Set<String> primaryKeyColumns
    ) {
        String quotedTable = sqlIdentifierQuoter.getQuoted(databaseName, tableName);
        String quotedCopy = sqlIdentifierQuoter.getQuoted(databaseName, copyTableName);
        List<String> statements = new ArrayList<>();
        if (sqlDialect.isMssql()) {
            statements.add("SELECT * INTO " + quotedCopy + " FROM " + quotedTable);
            if (!primaryKeyColumns.isEmpty()) {
                List<String> quotedKeys = new ArrayList<>();
                for (String primaryKeyColumn : primaryKeyColumns) quotedKeys.add(sqlIdentifierQuoter.getQuoted(primaryKeyColumn));
                statements.add("ALTER TABLE " + quotedCopy + " ADD PRIMARY KEY (" + String.join(", ", quotedKeys) + ")");
            }
            return statements;
        }
        if (sqlDialect.isPgsql()) {
            statements.add("CREATE TABLE " + quotedCopy + " (LIKE " + quotedTable + " INCLUDING ALL)");
            statements.add("INSERT INTO " + quotedCopy + " OVERRIDING SYSTEM VALUE SELECT * FROM " + quotedTable);
            return statements;
        }
        if (sqlDialect.isSqlite()) {
            statements.add(getSqliteCreateSql(sqliteCreateSql, quotedCopy));
            statements.add("INSERT INTO " + quotedCopy + " SELECT * FROM " + quotedTable);
            return statements;
        }
        statements.add("CREATE TABLE " + quotedCopy + " LIKE " + quotedTable);
        statements.add("INSERT INTO " + quotedCopy + " SELECT * FROM " + quotedTable);
        return statements;
    }

    /**
     * Para deshacer la copia si falla a medias.
     */
    public String getDropSql(String databaseName, String copyTableName) {
        return tableDropSqlBuilder.getDropSql(databaseName, copyTableName);
    }

    /**
     * El primer CREATE TABLE del DDL de SQLite (sin los CREATE INDEX/TRIGGER que le siguen) con el nombre cambiado.
     */
    private String getSqliteCreateSql(String sqliteCreateSql, String quotedCopy) {
        String createTableSql = NEXT_STATEMENT_PATTERN.split(sqliteCreateSql == null ? "" : sqliteCreateSql.trim(), 2)[0].trim();
        if (createTableSql.endsWith(";")) createTableSql = createTableSql.substring(0, createTableSql.length() - 1);
        Matcher nameMatcher = CREATE_TABLE_NAME_PATTERN.matcher(createTableSql);
        if (!nameMatcher.lookingAt()) throw new IllegalArgumentException("No CREATE TABLE in: " + createTableSql);
        return "CREATE TABLE " + quotedCopy + createTableSql.substring(nameMatcher.end());
    }
}
