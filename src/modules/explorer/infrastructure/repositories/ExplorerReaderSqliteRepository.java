package modules.explorer.infrastructure.repositories;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import modules.shared.domain.services.SqlDialect;
import modules.shared.domain.services.SqlIdentifierQuoter;
import modules.shared.application.Primitives;
import static modules.shared.application.Primitives.getInt;
import static modules.shared.application.Primitives.getTrimmedString;

import modules.explorer.domain.enums.ExplorerKeyEnum;

/**
 * El arbol de SQLite: cada "BD" es una de la conexion (main y las adjuntas con ATTACH); los objetos salen de su
 * sqlite_master y las columnas e indices de las funciones pragma_*. No hay rutinas. El DDL es el que guardo SQLite.
 * <p>
 * Con "main" no se pone prefijo: por el tunel PHP solo hay una BD y su traductor no conoce otros esquemas.
 */
public final class ExplorerReaderSqliteRepository extends AbstractExplorerReaderRepository {

    private final SqlIdentifierQuoter sqlIdentifierQuoter = SqlIdentifierQuoter.getInstance(SqlDialect.SQLITE);

    private ExplorerReaderSqliteRepository(Connection pdo) {
        super(pdo, SqlDialect.SQLITE);
    }

    public static ExplorerReaderSqliteRepository getInstance(Connection pdo) {
        return new ExplorerReaderSqliteRepository(pdo);
    }

    /**
     * main y las adjuntas; temp no.
     */
    @Override
    public List<String> getDatabaseNames() throws SQLException {
        String sql = """
            -- getDatabaseNames
            SELECT name AS database_name FROM pragma_database_list WHERE name <> 'temp' ORDER BY seq
            """;
        return getColumnValues(query(sql), ExplorerKeyEnum.DATABASE_NAME);
    }

    @Override
    public Map<String, Object> getTablesAndViews(String databaseName) throws SQLException {
        String sql = """
            -- getTablesAndViews
            SELECT name AS table_name, type AS table_type
            FROM %s
            WHERE type IN ('table', 'view')
            AND substr(name, 1, 7) <> 'sqlite_'
            ORDER BY name
            """.formatted(getMasterTable(databaseName));
        List<String> tables = new ArrayList<>();
        List<String> views = new ArrayList<>();
        for (Map<String, Object> row : query(sql)) {
            String tableName = getTrimmedString(row.get("table_name"));
            if ("view".equals(getTrimmedString(row.get("table_type")))) {
                views.add(tableName);
                continue;
            }
            tables.add(tableName);
        }
        return Primitives.of(ExplorerKeyEnum.TABLES, tables, ExplorerKeyEnum.VIEWS, views);
    }

    /**
     * SQLite no tiene procedimientos ni funciones guardadas.
     */
    @Override
    public Map<String, Object> getRoutines(String databaseName) {
        return Primitives.of(ExplorerKeyEnum.PROCEDURES, List.of(), ExplorerKeyEnum.FUNCTIONS, List.of());
    }

    @Override
    public List<String> getTriggerNames(String databaseName) throws SQLException {
        String sql = """
            -- getTriggerNames
            SELECT name AS trigger_name FROM %s WHERE type = 'trigger' ORDER BY name
            """.formatted(getMasterTable(databaseName));
        return getColumnValues(query(sql), "trigger_name");
    }

    /**
     * column_type es el tipo tal como se declaro; data_type, su nombre base en minusculas. Una INTEGER PRIMARY KEY (el
     * rowid) lleva extra = auto_increment y una columna generada, generated.
     */
    @Override
    public List<Map<String, Object>> getTableColumns(String databaseName, String tableName) throws SQLException {
        String sql = """
            -- getTableColumns
            SELECT name AS column_name, type AS column_type, "notnull" AS is_not_null, pk AS pk_position, hidden AS hidden
            FROM pragma_table_xinfo(?, ?)
            WHERE hidden <> 1
            ORDER BY cid
            """;
        List<Map<String, Object>> rows = query(sql, tableName, databaseName);
        Set<String> uniqueColumns = getSingleColumnUniqueColumns(databaseName, tableName);
        long primaryKeyColumnCount = rows.stream().filter(row -> getInt(row.get("pk_position")) > 0).count();

        List<Map<String, Object>> columns = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String columnName = getTrimmedString(row.get("column_name"));
            String columnType = getTrimmedString(row.get("column_type"));
            boolean isPrimaryKey = getInt(row.get("pk_position")) > 0;
            String extra = "";
            if (isPrimaryKey && primaryKeyColumnCount == 1 && "INTEGER".equalsIgnoreCase(columnType)) extra = "auto_increment";
            if (getInt(row.get("hidden")) >= 2) extra = "generated";
            columns.add(Primitives.of(
                ExplorerKeyEnum.COLUMN_NAME, columnName,
                ExplorerKeyEnum.COLUMN_TYPE, columnType.isEmpty() ? "(sin tipo)" : columnType.toLowerCase(Locale.ROOT),
                ExplorerKeyEnum.IS_NULLABLE, getInt(row.get("is_not_null")) == 0 && !isPrimaryKey,
                ExplorerKeyEnum.COLUMN_KEY, isPrimaryKey ? "PRI" : uniqueColumns.contains(columnName) ? "UNI" : "",
                ExplorerKeyEnum.DATA_TYPE, getBaseType(columnType),
                ExplorerKeyEnum.EXTRA, extra
            ));
        }
        return columns;
    }

    /**
     * SQLite no guarda estadisticas: las filas se cuentan (exactas) y no hay tamaño por tabla.
     */
    @Override
    public Map<String, Object> getTableStatus(String databaseName, String tableName) throws SQLException {
        String sql = """
            -- getTableStatus
            SELECT type AS table_type FROM %s WHERE name = ? AND type IN ('table', 'view')
            """.formatted(getMasterTable(databaseName));
        List<Map<String, Object>> rows = query(sql, tableName);
        if (rows.isEmpty()) return Primitives.of();
        boolean isView = "view".equals(getTrimmedString(rows.getFirst().get("table_type")));
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("table_type", isView ? "VIEW" : "BASE TABLE");
        status.put("engine", "SQLite");
        if (!isView) {
            List<Map<String, Object>> countRows = query("SELECT COUNT(*) AS table_rows FROM " + getQualifiedName(databaseName, tableName));
            status.put("table_rows", countRows.getFirst().get("table_rows"));
        }
        return status;
    }

    /**
     * Una fila por indice y columna. La INTEGER PRIMARY KEY no tiene indice propio (es el rowid): sale como PRIMARY.
     */
    @Override
    public List<Map<String, Object>> getIndexes(String databaseName, String tableName) throws SQLException {
        String sql = """
            -- getIndexes
            SELECT il.name AS index_name, il."unique" AS is_unique, il.origin AS origin, ii.seqno AS seqno, ii.name AS column_name
            FROM pragma_index_list(?, ?) il
            JOIN pragma_index_info(il.name, ?) ii
            ORDER BY il.origin = 'pk' DESC, il.name, ii.seqno
            """;
        List<Map<String, Object>> indexes = new ArrayList<>();
        boolean hasPrimaryIndex = false;
        for (Map<String, Object> row : query(sql, tableName, databaseName, databaseName)) {
            boolean isPrimary = "pk".equals(getTrimmedString(row.get("origin")));
            hasPrimaryIndex |= isPrimary;
            indexes.add(Primitives.of(
                "index_name", isPrimary ? "PRIMARY" : getTrimmedString(row.get("index_name")),
                "non_unique", getInt(row.get("is_unique")) == 1 ? 0 : 1,
                "seq_in_index", getInt(row.get("seqno")) + 1,
                "column_name", getTrimmedString(row.get("column_name")),
                "index_type", "btree"
            ));
        }
        if (!hasPrimaryIndex) {
            List<Map<String, Object>> rowidKey = new ArrayList<>();
            int position = 1;
            for (String columnName : getPrimaryKeyColumns(databaseName, tableName)) {
                rowidKey.add(Primitives.of("index_name", "PRIMARY", "non_unique", 0, "seq_in_index", position++, "column_name", columnName, "index_type", "rowid"));
            }
            indexes.addAll(0, rowidKey);
        }
        return indexes;
    }

    /**
     * El CREATE que guardo SQLite y, detras, los CREATE INDEX de la tabla.
     */
    @Override
    public String getCreateStatement(String databaseName, String tableName, boolean isView) throws SQLException {
        String sql = """
            -- getCreateStatement
            SELECT sql AS create_sql FROM %s
            WHERE (name = ? AND type IN ('table', 'view'))
            OR (tbl_name = ? AND type IN ('index', 'trigger') AND sql IS NOT NULL)
            ORDER BY type IN ('table', 'view') DESC, type, name
            """.formatted(getMasterTable(databaseName));
        List<String> statements = new ArrayList<>();
        for (Map<String, Object> row : query(sql, tableName, tableName)) {
            statements.add(getTrimmedString(row.get("create_sql")) + ";");
        }
        return String.join("\n", statements);
    }

    @Override
    public Set<String> getPrimaryKeyColumns(String databaseName, String tableName) throws SQLException {
        String sql = """
            -- getPrimaryKeyColumns
            SELECT name AS column_name FROM pragma_table_info(?, ?) WHERE pk > 0 ORDER BY pk
            """;
        return new LinkedHashSet<>(getColumnValues(query(sql, tableName, databaseName), "column_name"));
    }

    private Set<String> getSingleColumnUniqueColumns(String databaseName, String tableName) throws SQLException {
        String sql = """
            SELECT MIN(ii.name) AS column_name
            FROM pragma_index_list(?, ?) il
            JOIN pragma_index_info(il.name, ?) ii
            WHERE il."unique" = 1 AND il.origin <> 'pk'
            GROUP BY il.name
            HAVING COUNT(*) = 1
            """;
        return new LinkedHashSet<>(getColumnValues(query(sql, tableName, databaseName, databaseName), "column_name"));
    }

    private String getMasterTable(String databaseName) {
        return isMain(databaseName) ? "sqlite_master" : sqlIdentifierQuoter.getQuoted(databaseName) + ".sqlite_master";
    }

    private String getQualifiedName(String databaseName, String tableName) {
        return isMain(databaseName) ? sqlIdentifierQuoter.getQuoted(tableName) : sqlIdentifierQuoter.getQuoted(databaseName, tableName);
    }

    private boolean isMain(String databaseName) {
        return databaseName == null || databaseName.isEmpty() || SqlDialect.SQLITE_MAIN.equals(databaseName);
    }

    /**
     * "VARCHAR(80)" → "varchar"; sin tipo declarado, vacio (se puede editar como texto).
     */
    private String getBaseType(String columnType) {
        String baseType = columnType.replaceAll("\\(.*$", "").trim().toLowerCase(Locale.ROOT);
        return baseType;
    }
}
