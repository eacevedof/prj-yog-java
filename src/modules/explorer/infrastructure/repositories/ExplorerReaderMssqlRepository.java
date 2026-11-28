package modules.explorer.infrastructure.repositories;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import modules.shared.domain.services.SqlDialect;
import modules.shared.domain.services.SqlIdentifierQuoter;
import modules.shared.application.Primitives;
import static modules.shared.application.Primitives.getBool;
import static modules.shared.application.Primitives.getTrimmedString;

import modules.explorer.domain.enums.ExplorerKeyEnum;

/**
 * El arbol de SQL Server leido de las vistas sys.* de cada BD ([bd].sys.objects...): el arbol lista las BDs del
 * servidor, como en MySQL. Un objeto de dbo sale por su nombre y el de otro esquema como "esquema.objeto"
 * (SqlIdentifierQuoter lo deshace en [bd].[esquema].[objeto]). SQL Server no tiene SHOW CREATE TABLE: el DDL se arma
 * con columnas, restricciones e indices, como hace "Script Table as" de SSMS.
 */
public final class ExplorerReaderMssqlRepository extends AbstractExplorerReaderRepository {

    private static final String CATALOG = "{db}";
    private static final String OBJECT_NAME_SQL = "CASE WHEN s.name = 'dbo' THEN o.name ELSE s.name + '.' + o.name END";
    private static final String OBJECT_ID_SQL = """
        (SELECT o.object_id FROM {db}.sys.objects o
         JOIN {db}.sys.schemas s ON s.schema_id = o.schema_id
         WHERE s.name = ? AND o.name = ?)""";
    private static final String COLUMN_TYPE_SQL = """
        CASE
            WHEN t.name IN ('varchar', 'char', 'varbinary', 'binary')
                THEN t.name + '(' + CASE WHEN c.max_length = -1 THEN 'max' ELSE CAST(c.max_length AS varchar(10)) END + ')'
            WHEN t.name IN ('nvarchar', 'nchar')
                THEN t.name + '(' + CASE WHEN c.max_length = -1 THEN 'max' ELSE CAST(c.max_length / 2 AS varchar(10)) END + ')'
            WHEN t.name IN ('decimal', 'numeric')
                THEN t.name + '(' + CAST(c.precision AS varchar(10)) + ',' + CAST(c.scale AS varchar(10)) + ')'
            WHEN t.name IN ('datetime2', 'time', 'datetimeoffset')
                THEN t.name + '(' + CAST(c.scale AS varchar(10)) + ')'
            ELSE t.name
        END""";

    private final SqlIdentifierQuoter sqlIdentifierQuoter = SqlIdentifierQuoter.getInstance(SqlDialect.MSSQL);

    private ExplorerReaderMssqlRepository(Connection pdo) {
        super(pdo, SqlDialect.MSSQL);
    }

    public static ExplorerReaderMssqlRepository getInstance(Connection pdo) {
        return new ExplorerReaderMssqlRepository(pdo);
    }

    /**
     * Las BDs en linea a las que el usuario tiene acceso.
     */
    @Override
    public List<String> getDatabaseNames() throws SQLException {
        String sql = """
            -- getDatabaseNames
            SELECT name AS database_name
            FROM sys.databases
            WHERE 1=1
            AND state = 0
            AND HAS_DBACCESS(name) = 1
            ORDER BY name
            """;
        return getColumnValues(query(sql), ExplorerKeyEnum.DATABASE_NAME);
    }

    @Override
    public Map<String, Object> getTablesAndViews(String databaseName) throws SQLException {
        String sql = """
            -- getTablesAndViews
            SELECT %s AS table_name, o.type AS object_type
            FROM {db}.sys.objects o
            JOIN {db}.sys.schemas s ON s.schema_id = o.schema_id
            WHERE 1=1
            AND o.type IN ('U', 'V')
            AND o.is_ms_shipped = 0
            ORDER BY table_name
            """.formatted(OBJECT_NAME_SQL);
        List<String> tables = new ArrayList<>();
        List<String> views = new ArrayList<>();
        for (Map<String, Object> row : query(getInCatalog(sql, databaseName))) {
            String tableName = getTrimmedString(row.get("table_name"));
            if ("V".equals(getTrimmedString(row.get("object_type")))) {
                views.add(tableName);
                continue;
            }
            tables.add(tableName);
        }
        return Primitives.of(ExplorerKeyEnum.TABLES, tables, ExplorerKeyEnum.VIEWS, views);
    }

    /**
     * Procedimientos (P, y PC de CLR) y funciones (escalares, de tabla en linea y de tabla, tambien las de CLR).
     */
    @Override
    public Map<String, Object> getRoutines(String databaseName) throws SQLException {
        String sql = """
            -- getRoutines
            SELECT %s AS routine_name, o.type AS object_type
            FROM {db}.sys.objects o
            JOIN {db}.sys.schemas s ON s.schema_id = o.schema_id
            WHERE 1=1
            AND o.type IN ('P', 'PC', 'FN', 'IF', 'TF', 'FS', 'FT')
            AND o.is_ms_shipped = 0
            ORDER BY routine_name
            """.formatted(OBJECT_NAME_SQL);
        List<String> procedures = new ArrayList<>();
        List<String> functions = new ArrayList<>();
        for (Map<String, Object> row : query(getInCatalog(sql, databaseName))) {
            String routineName = getTrimmedString(row.get("routine_name"));
            String objectType = getTrimmedString(row.get("object_type"));
            if ("P".equals(objectType) || "PC".equals(objectType)) {
                procedures.add(routineName);
                continue;
            }
            functions.add(routineName);
        }
        return Primitives.of(ExplorerKeyEnum.PROCEDURES, procedures, ExplorerKeyEnum.FUNCTIONS, functions);
    }

    /**
     * Los triggers de tabla y de vista (un trigger es del esquema de su tabla).
     */
    @Override
    public List<String> getTriggerNames(String databaseName) throws SQLException {
        String sql = """
            -- getTriggerNames
            SELECT %s AS trigger_name
            FROM {db}.sys.objects o
            JOIN {db}.sys.schemas s ON s.schema_id = o.schema_id
            WHERE 1=1
            AND o.type = 'TR'
            AND o.is_ms_shipped = 0
            ORDER BY trigger_name
            """.formatted(OBJECT_NAME_SQL);
        return getColumnValues(query(getInCatalog(sql, databaseName)), "trigger_name");
    }

    /**
     * column_type como lo escribe SSMS (nvarchar(80), decimal(10,2), varchar(max)...); una columna IDENTITY lleva
     * extra = auto_increment y una calculada, generated.
     */
    @Override
    public List<Map<String, Object>> getTableColumns(String databaseName, String tableName) throws SQLException {
        String sql = """
            -- getTableColumns
            SELECT c.name AS column_name,
                %s AS column_type,
                c.is_nullable AS is_nullable,
                CASE
                    WHEN EXISTS (
                        SELECT 1 FROM {db}.sys.indexes i
                        JOIN {db}.sys.index_columns ic ON ic.object_id = i.object_id AND ic.index_id = i.index_id
                        WHERE i.object_id = c.object_id AND i.is_primary_key = 1 AND ic.column_id = c.column_id
                    ) THEN 'PRI'
                    WHEN EXISTS (
                        SELECT 1 FROM {db}.sys.indexes i
                        JOIN {db}.sys.index_columns ic ON ic.object_id = i.object_id AND ic.index_id = i.index_id
                        WHERE i.object_id = c.object_id AND i.is_unique = 1 AND ic.column_id = c.column_id AND ic.is_included_column = 0
                        AND (SELECT COUNT(*) FROM {db}.sys.index_columns x
                             WHERE x.object_id = i.object_id AND x.index_id = i.index_id AND x.is_included_column = 0) = 1
                    ) THEN 'UNI'
                    ELSE ''
                END AS column_key,
                t.name AS data_type,
                CASE WHEN c.is_identity = 1 THEN 'auto_increment' WHEN c.is_computed = 1 THEN 'generated' ELSE '' END AS extra
            FROM {db}.sys.columns c
            JOIN {db}.sys.types t ON t.user_type_id = c.user_type_id
            WHERE c.object_id = %s
            ORDER BY c.column_id
            """.formatted(COLUMN_TYPE_SQL, OBJECT_ID_SQL);
        List<Map<String, Object>> columns = new ArrayList<>();
        for (Map<String, Object> row : queryObject(sql, databaseName, tableName)) {
            columns.add(Primitives.of(
                ExplorerKeyEnum.COLUMN_NAME, getTrimmedString(row.get("column_name")),
                ExplorerKeyEnum.COLUMN_TYPE, getTrimmedString(row.get("column_type")),
                ExplorerKeyEnum.IS_NULLABLE, isTrue(row.get("is_nullable")),
                ExplorerKeyEnum.COLUMN_KEY, getTrimmedString(row.get("column_key")),
                ExplorerKeyEnum.DATA_TYPE, getTrimmedString(row.get("data_type")),
                ExplorerKeyEnum.EXTRA, getTrimmedString(row.get("extra"))
            ));
        }
        return columns;
    }

    /**
     * Filas (de sys.partitions, sin COUNT), tamaño de datos e indices en bytes (paginas de 8 KB), fechas y la
     * descripcion (MS_Description). engine: clustered si tiene indice agrupado, heap si no.
     */
    @Override
    public Map<String, Object> getTableStatus(String databaseName, String tableName) throws SQLException {
        String sql = """
            -- getTableStatus
            SELECT CASE o.type WHEN 'V' THEN 'VIEW' ELSE 'BASE TABLE' END AS table_type,
                CASE WHEN o.type = 'V' THEN NULL
                     WHEN EXISTS (SELECT 1 FROM {db}.sys.indexes i WHERE i.object_id = o.object_id AND i.index_id = 1) THEN 'clustered'
                     ELSE 'heap' END AS engine,
                (SELECT SUM(p.rows) FROM {db}.sys.partitions p WHERE p.object_id = o.object_id AND p.index_id IN (0, 1)) AS table_rows,
                (SELECT SUM(a.used_pages) * 8192 FROM {db}.sys.partitions p
                 JOIN {db}.sys.allocation_units a ON a.container_id = p.partition_id
                 WHERE p.object_id = o.object_id AND p.index_id IN (0, 1)) AS data_length,
                (SELECT SUM(a.used_pages) * 8192 FROM {db}.sys.partitions p
                 JOIN {db}.sys.allocation_units a ON a.container_id = p.partition_id
                 WHERE p.object_id = o.object_id AND p.index_id > 1) AS index_length,
                NULL AS auto_increment,
                NULL AS table_collation,
                o.create_date AS create_time,
                o.modify_date AS update_time,
                CAST(ep.value AS nvarchar(4000)) AS table_comment
            FROM {db}.sys.objects o
            LEFT JOIN {db}.sys.extended_properties ep
                ON ep.class = 1 AND ep.major_id = o.object_id AND ep.minor_id = 0 AND ep.name = 'MS_Description'
            WHERE o.object_id = %s
            """.formatted(OBJECT_ID_SQL);
        List<Map<String, Object>> rows = queryObject(sql, databaseName, tableName);
        return rows.isEmpty() ? Primitives.of() : rows.getFirst();
    }

    /**
     * Una fila por indice y columna clave (sin las INCLUDE); la clave primaria se llama "PRIMARY".
     */
    @Override
    public List<Map<String, Object>> getIndexes(String databaseName, String tableName) throws SQLException {
        String sql = """
            -- getIndexes
            SELECT CASE WHEN i.is_primary_key = 1 THEN 'PRIMARY' ELSE i.name END AS index_name,
                CASE WHEN i.is_unique = 1 THEN 0 ELSE 1 END AS non_unique,
                CAST(ic.key_ordinal AS int) AS seq_in_index,
                c.name AS column_name,
                i.type_desc AS index_type
            FROM {db}.sys.indexes i
            JOIN {db}.sys.index_columns ic ON ic.object_id = i.object_id AND ic.index_id = i.index_id
            JOIN {db}.sys.columns c ON c.object_id = ic.object_id AND c.column_id = ic.column_id
            WHERE i.object_id = %s
            AND i.type > 0
            AND ic.is_included_column = 0
            ORDER BY i.is_primary_key DESC, index_name, ic.key_ordinal
            """.formatted(OBJECT_ID_SQL);
        return queryObject(sql, databaseName, tableName);
    }

    /**
     * Vista: su texto de sys.sql_modules. Tabla: columnas (tipo, IDENTITY, calculadas, NULL y DEFAULT), PK, UNIQUE, FK y
     * CHECK, y los indices que no salen de una restriccion.
     */
    @Override
    public String getCreateStatement(String databaseName, String tableName, boolean isView) throws SQLException {
        if (isView) {
            String viewSql = "SELECT m.definition AS view_definition FROM {db}.sys.sql_modules m WHERE m.object_id = " + OBJECT_ID_SQL;
            List<Map<String, Object>> rows = queryObject(viewSql, databaseName, tableName);
            return rows.isEmpty() ? "" : getTrimmedString(rows.getFirst().get("view_definition"));
        }

        String quotedTable = sqlIdentifierQuoter.getQuoted("", tableName);
        List<String> definitions = new ArrayList<>();
        definitions.addAll(getColumnDefinitions(databaseName, tableName));
        definitions.addAll(getKeyConstraintDefinitions(databaseName, tableName));
        definitions.addAll(getForeignKeyDefinitions(databaseName, tableName));
        String checksSql = """
            SELECT cc.name AS constraint_name, cc.definition AS constraint_definition
            FROM {db}.sys.check_constraints cc
            WHERE cc.parent_object_id = %s
            ORDER BY cc.name
            """.formatted(OBJECT_ID_SQL);
        for (Map<String, Object> check : queryObject(checksSql, databaseName, tableName)) {
            definitions.add("    CONSTRAINT " + sqlIdentifierQuoter.getQuoted(getTrimmedString(check.get("constraint_name")))
                + " CHECK " + getTrimmedString(check.get("constraint_definition")));
        }

        StringBuilder createStatement = new StringBuilder("CREATE TABLE ").append(quotedTable).append(" (\n")
            .append(String.join(",\n", definitions)).append("\n);");
        for (String indexDefinition : getIndexDefinitions(databaseName, tableName, quotedTable)) {
            createStatement.append('\n').append(indexDefinition);
        }
        return createStatement.toString();
    }

    @Override
    public Set<String> getPrimaryKeyColumns(String databaseName, String tableName) throws SQLException {
        String sql = """
            -- getPrimaryKeyColumns
            SELECT c.name AS column_name
            FROM {db}.sys.indexes i
            JOIN {db}.sys.index_columns ic ON ic.object_id = i.object_id AND ic.index_id = i.index_id
            JOIN {db}.sys.columns c ON c.object_id = ic.object_id AND c.column_id = ic.column_id
            WHERE i.object_id = %s
            AND i.is_primary_key = 1
            ORDER BY ic.key_ordinal
            """.formatted(OBJECT_ID_SQL);
        return new LinkedHashSet<>(getColumnValues(queryObject(sql, databaseName, tableName), "column_name"));
    }

    private List<String> getColumnDefinitions(String databaseName, String tableName) throws SQLException {
        String sql = """
            SELECT c.name AS column_name, %s AS column_type, c.is_nullable AS is_nullable,
                dc.definition AS default_value, cc.definition AS computed_definition,
                CAST(idc.seed_value AS varchar(40)) AS identity_seed, CAST(idc.increment_value AS varchar(40)) AS identity_increment
            FROM {db}.sys.columns c
            JOIN {db}.sys.types t ON t.user_type_id = c.user_type_id
            LEFT JOIN {db}.sys.default_constraints dc ON dc.parent_object_id = c.object_id AND dc.parent_column_id = c.column_id
            LEFT JOIN {db}.sys.computed_columns cc ON cc.object_id = c.object_id AND cc.column_id = c.column_id
            LEFT JOIN {db}.sys.identity_columns idc ON idc.object_id = c.object_id AND idc.column_id = c.column_id
            WHERE c.object_id = %s
            ORDER BY c.column_id
            """.formatted(COLUMN_TYPE_SQL, OBJECT_ID_SQL);
        List<String> definitions = new ArrayList<>();
        for (Map<String, Object> column : queryObject(sql, databaseName, tableName)) {
            StringBuilder definition = new StringBuilder("    ")
                .append(sqlIdentifierQuoter.getQuoted(getTrimmedString(column.get("column_name"))));
            String computedDefinition = getTrimmedString(column.get("computed_definition"));
            if (!computedDefinition.isEmpty()) {
                definitions.add(definition.append(" AS ").append(computedDefinition).toString());
                continue;
            }
            definition.append(' ').append(getTrimmedString(column.get("column_type")));
            String identitySeed = getTrimmedString(column.get("identity_seed"));
            if (!identitySeed.isEmpty()) {
                definition.append(" IDENTITY(").append(identitySeed).append(", ").append(getTrimmedString(column.get("identity_increment"))).append(')');
            }
            definition.append(isTrue(column.get("is_nullable")) ? " NULL" : " NOT NULL");
            String defaultValue = getTrimmedString(column.get("default_value"));
            if (!defaultValue.isEmpty()) definition.append(" DEFAULT ").append(defaultValue);
            definitions.add(definition.toString());
        }
        return definitions;
    }

    /**
     * CONSTRAINT [pk] PRIMARY KEY CLUSTERED ([a], [b] DESC) y los UNIQUE.
     */
    private List<String> getKeyConstraintDefinitions(String databaseName, String tableName) throws SQLException {
        String sql = """
            SELECT kc.name AS constraint_name, kc.type AS constraint_type, i.type_desc AS index_type,
                c.name AS column_name, ic.is_descending_key AS is_descending
            FROM {db}.sys.key_constraints kc
            JOIN {db}.sys.indexes i ON i.object_id = kc.parent_object_id AND i.index_id = kc.unique_index_id
            JOIN {db}.sys.index_columns ic ON ic.object_id = i.object_id AND ic.index_id = i.index_id
            JOIN {db}.sys.columns c ON c.object_id = ic.object_id AND c.column_id = ic.column_id
            WHERE kc.parent_object_id = %s
            AND ic.is_included_column = 0
            ORDER BY kc.type, kc.name, ic.key_ordinal
            """.formatted(OBJECT_ID_SQL);
        Map<String, String> headers = new LinkedHashMap<>();
        Map<String, List<String>> columnsByConstraint = new LinkedHashMap<>();
        for (Map<String, Object> row : queryObject(sql, databaseName, tableName)) {
            String constraintName = getTrimmedString(row.get("constraint_name"));
            String keyword = "PK".equals(getTrimmedString(row.get("constraint_type"))) ? "PRIMARY KEY" : "UNIQUE";
            headers.putIfAbsent(constraintName, "    CONSTRAINT " + sqlIdentifierQuoter.getQuoted(constraintName)
                + " " + keyword + " " + getTrimmedString(row.get("index_type")));
            columnsByConstraint.computeIfAbsent(constraintName, name -> new ArrayList<>())
                .add(getIndexColumn(getTrimmedString(row.get("column_name")), isTrue(row.get("is_descending"))));
        }
        List<String> definitions = new ArrayList<>();
        for (Map.Entry<String, String> header : headers.entrySet()) {
            definitions.add(header.getValue() + " (" + String.join(", ", columnsByConstraint.get(header.getKey())) + ")");
        }
        return definitions;
    }

    private List<String> getForeignKeyDefinitions(String databaseName, String tableName) throws SQLException {
        String sql = """
            SELECT fk.name AS constraint_name, pc.name AS column_name,
                rs.name AS referenced_schema, rt.name AS referenced_table, rc.name AS referenced_column,
                fk.delete_referential_action_desc AS on_delete, fk.update_referential_action_desc AS on_update
            FROM {db}.sys.foreign_keys fk
            JOIN {db}.sys.foreign_key_columns fkc ON fkc.constraint_object_id = fk.object_id
            JOIN {db}.sys.columns pc ON pc.object_id = fkc.parent_object_id AND pc.column_id = fkc.parent_column_id
            JOIN {db}.sys.objects rt ON rt.object_id = fkc.referenced_object_id
            JOIN {db}.sys.schemas rs ON rs.schema_id = rt.schema_id
            JOIN {db}.sys.columns rc ON rc.object_id = fkc.referenced_object_id AND rc.column_id = fkc.referenced_column_id
            WHERE fk.parent_object_id = %s
            ORDER BY fk.name, fkc.constraint_column_id
            """.formatted(OBJECT_ID_SQL);
        Map<String, Map<String, Object>> firstRows = new LinkedHashMap<>();
        Map<String, List<String>> columns = new LinkedHashMap<>();
        Map<String, List<String>> referencedColumns = new LinkedHashMap<>();
        for (Map<String, Object> row : queryObject(sql, databaseName, tableName)) {
            String constraintName = getTrimmedString(row.get("constraint_name"));
            firstRows.putIfAbsent(constraintName, row);
            columns.computeIfAbsent(constraintName, name -> new ArrayList<>())
                .add(sqlIdentifierQuoter.getQuoted(getTrimmedString(row.get("column_name"))));
            referencedColumns.computeIfAbsent(constraintName, name -> new ArrayList<>())
                .add(sqlIdentifierQuoter.getQuoted(getTrimmedString(row.get("referenced_column"))));
        }
        List<String> definitions = new ArrayList<>();
        for (Map.Entry<String, Map<String, Object>> entry : firstRows.entrySet()) {
            String constraintName = entry.getKey();
            Map<String, Object> row = entry.getValue();
            StringBuilder definition = new StringBuilder("    CONSTRAINT ").append(sqlIdentifierQuoter.getQuoted(constraintName))
                .append(" FOREIGN KEY (").append(String.join(", ", columns.get(constraintName))).append(") REFERENCES ")
                .append(sqlIdentifierQuoter.getQuoted(getTrimmedString(row.get("referenced_schema"))))
                .append('.').append(sqlIdentifierQuoter.getQuoted(getTrimmedString(row.get("referenced_table"))))
                .append(" (").append(String.join(", ", referencedColumns.get(constraintName))).append(')');
            definition.append(getReferentialAction("ON DELETE", getTrimmedString(row.get("on_delete"))));
            definition.append(getReferentialAction("ON UPDATE", getTrimmedString(row.get("on_update"))));
            definitions.add(definition.toString());
        }
        return definitions;
    }

    /**
     * CREATE [UNIQUE] NONCLUSTERED INDEX [ix] ON [esquema].[tabla] ([a], [b] DESC) INCLUDE ([c]);
     */
    private List<String> getIndexDefinitions(String databaseName, String tableName, String quotedTable) throws SQLException {
        String sql = """
            SELECT i.name AS index_name, i.is_unique AS is_unique, i.type_desc AS index_type,
                c.name AS column_name, ic.is_descending_key AS is_descending, ic.is_included_column AS is_included
            FROM {db}.sys.indexes i
            JOIN {db}.sys.index_columns ic ON ic.object_id = i.object_id AND ic.index_id = i.index_id
            JOIN {db}.sys.columns c ON c.object_id = ic.object_id AND c.column_id = ic.column_id
            WHERE i.object_id = %s
            AND i.type > 0
            AND i.is_primary_key = 0
            AND i.is_unique_constraint = 0
            ORDER BY i.name, ic.is_included_column, ic.key_ordinal, ic.index_column_id
            """.formatted(OBJECT_ID_SQL);
        Map<String, String> headers = new LinkedHashMap<>();
        Map<String, List<String>> keyColumns = new LinkedHashMap<>();
        Map<String, List<String>> includedColumns = new LinkedHashMap<>();
        for (Map<String, Object> row : queryObject(sql, databaseName, tableName)) {
            String indexName = getTrimmedString(row.get("index_name"));
            headers.putIfAbsent(indexName, "CREATE " + (isTrue(row.get("is_unique")) ? "UNIQUE " : "")
                + getTrimmedString(row.get("index_type")) + " INDEX " + sqlIdentifierQuoter.getQuoted(indexName) + " ON " + quotedTable);
            String columnName = getTrimmedString(row.get("column_name"));
            if (isTrue(row.get("is_included"))) {
                includedColumns.computeIfAbsent(indexName, name -> new ArrayList<>()).add(sqlIdentifierQuoter.getQuoted(columnName));
                continue;
            }
            keyColumns.computeIfAbsent(indexName, name -> new ArrayList<>()).add(getIndexColumn(columnName, isTrue(row.get("is_descending"))));
        }
        List<String> definitions = new ArrayList<>();
        for (Map.Entry<String, String> header : headers.entrySet()) {
            StringBuilder definition = new StringBuilder(header.getValue());
            List<String> keys = keyColumns.getOrDefault(header.getKey(), List.of());
            if (!keys.isEmpty()) definition.append(" (").append(String.join(", ", keys)).append(')');
            List<String> includes = includedColumns.getOrDefault(header.getKey(), List.of());
            if (!includes.isEmpty()) definition.append(" INCLUDE (").append(String.join(", ", includes)).append(')');
            definitions.add(definition.append(';').toString());
        }
        return definitions;
    }

    private String getIndexColumn(String columnName, boolean isDescending) {
        return sqlIdentifierQuoter.getQuoted(columnName) + (isDescending ? " DESC" : "");
    }

    /**
     * NO_ACTION es el de por defecto: no se escribe. CASCADE, SET_NULL, SET_DEFAULT sin el guion bajo.
     */
    private String getReferentialAction(String clause, String action) {
        if (action.isEmpty() || "NO_ACTION".equals(action)) return "";
        return " " + clause + " " + action.replace('_', ' ');
    }

    /**
     * La consulta en la BD del arbol: {db} pasa a ser [bd] (las vistas sys.* de cada BD van por nombre de tres partes).
     */
    private String getInCatalog(String sql, String databaseName) {
        return sql.replace(CATALOG, sqlIdentifierQuoter.getQuoted(databaseName));
    }

    /**
     * Una consulta de un objeto ("esquema.objeto" o, sin punto, de dbo): sus ? son el esquema y el nombre de
     * OBJECT_ID_SQL.
     */
    private List<Map<String, Object>> queryObject(String sql, String databaseName, String objectName) throws SQLException {
        int dotIndex = objectName.indexOf('.');
        String schemaName = dotIndex == -1 ? SqlDialect.MSSQL_DEFAULT_SCHEMA : objectName.substring(0, dotIndex);
        return query(getInCatalog(sql, databaseName), schemaName, objectName.substring(dotIndex + 1));
    }

    /**
     * El driver devuelve los bit como Boolean.
     */
    private boolean isTrue(Object value) {
        if (value instanceof Boolean bool) return bool;
        String text = getTrimmedString(value);
        return "true".equalsIgnoreCase(text) || getBool(text);
    }
}
