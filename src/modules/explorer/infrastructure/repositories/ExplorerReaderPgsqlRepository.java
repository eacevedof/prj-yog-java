package modules.explorer.infrastructure.repositories;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
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
 * El arbol de PostgreSQL leido de pg_catalog: cada "BD" del arbol es un esquema de la BD conectada (sin los de TOAST
 * ni los temporales). PostgreSQL no tiene SHOW CREATE TABLE: el DDL se arma con format_type, pg_get_constraintdef y
 * pg_get_indexdef, que es lo que hace psql \d.
 */
public final class ExplorerReaderPgsqlRepository extends AbstractExplorerReaderRepository {

    private static final String RELATION_OID_SQL = """
        (SELECT c.oid FROM pg_catalog.pg_class c
         JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
         WHERE n.nspname = ? AND c.relname = ?)""";

    private ExplorerReaderPgsqlRepository(Connection pdo) {
        super(pdo, SqlDialect.PGSQL);
    }

    public static ExplorerReaderPgsqlRepository getInstance(Connection pdo) {
        return new ExplorerReaderPgsqlRepository(pdo);
    }

    @Override
    public List<String> getDatabaseNames() throws SQLException {
        String sql = """
            -- getDatabaseNames (esquemas)
            SELECT nspname AS database_name
            FROM pg_catalog.pg_namespace
            WHERE 1=1
            AND nspname NOT LIKE 'pg\\_toast%'
            AND nspname NOT LIKE 'pg\\_temp\\_%'
            ORDER BY nspname
            """;
        return getColumnValues(query(sql), ExplorerKeyEnum.DATABASE_NAME);
    }

    /**
     * Tablas (normales, particionadas y foraneas) y vistas (tambien las materializadas).
     */
    @Override
    public Map<String, Object> getTablesAndViews(String databaseName) throws SQLException {
        String sql = """
            -- getTablesAndViews
            SELECT c.relname AS table_name, c.relkind AS relkind
            FROM pg_catalog.pg_class c
            JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
            WHERE 1=1
            AND n.nspname = ?
            AND c.relkind IN ('r', 'p', 'f', 'v', 'm')
            AND NOT c.relispartition
            ORDER BY c.relname
            """;
        List<String> tables = new ArrayList<>();
        List<String> views = new ArrayList<>();
        for (Map<String, Object> row : query(sql, databaseName)) {
            String tableName = getTrimmedString(row.get("table_name"));
            String relkind = getTrimmedString(row.get("relkind"));
            if ("v".equals(relkind) || "m".equals(relkind)) {
                views.add(tableName);
                continue;
            }
            tables.add(tableName);
        }
        return Primitives.of(ExplorerKeyEnum.TABLES, tables, ExplorerKeyEnum.VIEWS, views);
    }

    /**
     * Las funciones sobrecargadas salen una vez (el arbol va por nombre).
     */
    @Override
    public Map<String, Object> getRoutines(String databaseName) throws SQLException {
        String sql = """
            -- getRoutines
            SELECT DISTINCT p.proname AS routine_name, p.prokind AS prokind
            FROM pg_catalog.pg_proc p
            JOIN pg_catalog.pg_namespace n ON n.oid = p.pronamespace
            WHERE 1=1
            AND n.nspname = ?
            AND p.prokind IN ('f', 'p')
            ORDER BY p.proname
            """;
        List<String> procedures = new ArrayList<>();
        List<String> functions = new ArrayList<>();
        for (Map<String, Object> row : query(sql, databaseName)) {
            String routineName = getTrimmedString(row.get("routine_name"));
            if ("p".equals(getTrimmedString(row.get("prokind")))) {
                procedures.add(routineName);
                continue;
            }
            functions.add(routineName);
        }
        return Primitives.of(ExplorerKeyEnum.PROCEDURES, procedures, ExplorerKeyEnum.FUNCTIONS, functions);
    }

    @Override
    public List<String> getTriggerNames(String databaseName) throws SQLException {
        String sql = """
            -- getTriggerNames
            SELECT DISTINCT t.tgname AS trigger_name
            FROM pg_catalog.pg_trigger t
            JOIN pg_catalog.pg_class c ON c.oid = t.tgrelid
            JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
            WHERE 1=1
            AND n.nspname = ?
            AND NOT t.tgisinternal
            ORDER BY t.tgname
            """;
        return getColumnValues(query(sql, databaseName), "trigger_name");
    }

    /**
     * column_type como lo escribe psql (character varying(80), numeric(10,2)...); data_type es el nombre del tipo base
     * (int4, varchar, jsonb, bytea...). Una columna serial o identity lleva extra = auto_increment.
     */
    @Override
    public List<Map<String, Object>> getTableColumns(String databaseName, String tableName) throws SQLException {
        String sql = """
            -- getTableColumns
            SELECT a.attname AS column_name,
                pg_catalog.format_type(a.atttypid, a.atttypmod) AS column_type,
                NOT a.attnotnull AS is_nullable,
                CASE
                    WHEN EXISTS (SELECT 1 FROM pg_catalog.pg_index i WHERE i.indrelid = a.attrelid AND i.indisprimary AND a.attnum = ANY (i.indkey)) THEN 'PRI'
                    WHEN EXISTS (SELECT 1 FROM pg_catalog.pg_index i WHERE i.indrelid = a.attrelid AND i.indisunique AND i.indnatts = 1 AND a.attnum = ANY (i.indkey)) THEN 'UNI'
                    ELSE ''
                END AS column_key,
                t.typname AS data_type,
                CASE
                    WHEN a.attidentity <> '' OR pg_catalog.pg_get_expr(d.adbin, d.adrelid) LIKE 'nextval(%%' THEN 'auto_increment'
                    WHEN a.attgenerated <> '' THEN 'generated'
                    ELSE ''
                END AS extra
            FROM pg_catalog.pg_attribute a
            JOIN pg_catalog.pg_type t ON t.oid = a.atttypid
            LEFT JOIN pg_catalog.pg_attrdef d ON d.adrelid = a.attrelid AND d.adnum = a.attnum
            WHERE 1=1
            AND a.attrelid = %s
            AND a.attnum > 0
            AND NOT a.attisdropped
            ORDER BY a.attnum
            """.formatted(RELATION_OID_SQL);
        List<Map<String, Object>> columns = new ArrayList<>();
        for (Map<String, Object> row : query(sql, databaseName, tableName)) {
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
     * Filas estimadas (reltuples, -1 si nunca se analizo), tamaño de la tabla y de sus indices y el comentario.
     */
    @Override
    public Map<String, Object> getTableStatus(String databaseName, String tableName) throws SQLException {
        String sql = """
            -- getTableStatus
            SELECT CASE c.relkind WHEN 'v' THEN 'VIEW' WHEN 'm' THEN 'MATERIALIZED VIEW' ELSE 'BASE TABLE' END AS table_type,
                CASE c.relkind WHEN 'p' THEN 'partitioned' WHEN 'f' THEN 'foreign' ELSE am.amname END AS engine,
                GREATEST(c.reltuples, 0)::bigint AS table_rows,
                pg_catalog.pg_relation_size(c.oid) AS data_length,
                pg_catalog.pg_indexes_size(c.oid) AS index_length,
                NULL AS auto_increment,
                NULL AS table_collation,
                NULL AS create_time,
                NULL AS update_time,
                pg_catalog.obj_description(c.oid, 'pg_class') AS table_comment
            FROM pg_catalog.pg_class c
            LEFT JOIN pg_catalog.pg_am am ON am.oid = c.relam
            WHERE c.oid = %s
            """.formatted(RELATION_OID_SQL);
        List<Map<String, Object>> rows = query(sql, databaseName, tableName);
        return rows.isEmpty() ? Primitives.of() : rows.getFirst();
    }

    /**
     * Una fila por indice y columna; las columnas de un indice sobre expresiones salen como la expresion.
     */
    @Override
    public List<Map<String, Object>> getIndexes(String databaseName, String tableName) throws SQLException {
        String sql = """
            -- getIndexes
            SELECT CASE WHEN ix.indisprimary THEN 'PRIMARY' ELSE ic.relname END AS index_name,
                CASE WHEN ix.indisunique THEN 0 ELSE 1 END AS non_unique,
                k.ord AS seq_in_index,
                COALESCE(a.attname, pg_catalog.pg_get_indexdef(ix.indexrelid, k.ord::int, true)) AS column_name,
                am.amname AS index_type
            FROM pg_catalog.pg_index ix
            JOIN pg_catalog.pg_class ic ON ic.oid = ix.indexrelid
            JOIN pg_catalog.pg_am am ON am.oid = ic.relam
            CROSS JOIN LATERAL unnest(ix.indkey::int2[]) WITH ORDINALITY AS k(attnum, ord)
            LEFT JOIN pg_catalog.pg_attribute a ON a.attrelid = ix.indrelid AND a.attnum = k.attnum AND k.attnum > 0
            WHERE ix.indrelid = %s
            AND k.ord <= ix.indnkeyatts
            ORDER BY ix.indisprimary DESC, index_name, k.ord
            """.formatted(RELATION_OID_SQL);
        return query(sql, databaseName, tableName);
    }

    /**
     * Vista: su definicion. Tabla: columnas con tipo, NOT NULL y DEFAULT, sus restricciones (PK, UNIQUE, FK, CHECK) y
     * los indices que no salen de una restriccion.
     */
    @Override
    public String getCreateStatement(String databaseName, String tableName, boolean isView) throws SQLException {
        String quotedName = SqlIdentifierQuoter.getInstance(SqlDialect.PGSQL).getQuoted(databaseName, tableName);
        if (isView) {
            String sql = "SELECT pg_catalog.pg_get_viewdef(%s, true) AS view_definition".formatted(RELATION_OID_SQL);
            List<Map<String, Object>> rows = query(sql, databaseName, tableName);
            if (rows.isEmpty()) return "";
            return "CREATE VIEW " + quotedName + " AS\n" + getTrimmedString(rows.getFirst().get("view_definition"));
        }

        List<String> definitions = new ArrayList<>();
        String columnsSql = """
            SELECT a.attname AS column_name, pg_catalog.format_type(a.atttypid, a.atttypmod) AS column_type,
                a.attnotnull AS is_not_null, pg_catalog.pg_get_expr(d.adbin, d.adrelid) AS default_value,
                a.attidentity AS identity_kind
            FROM pg_catalog.pg_attribute a
            LEFT JOIN pg_catalog.pg_attrdef d ON d.adrelid = a.attrelid AND d.adnum = a.attnum
            WHERE a.attrelid = %s AND a.attnum > 0 AND NOT a.attisdropped
            ORDER BY a.attnum
            """.formatted(RELATION_OID_SQL);
        SqlIdentifierQuoter sqlIdentifierQuoter = SqlIdentifierQuoter.getInstance(SqlDialect.PGSQL);
        for (Map<String, Object> column : query(columnsSql, databaseName, tableName)) {
            StringBuilder definition = new StringBuilder("    ")
                .append(sqlIdentifierQuoter.getQuoted(getTrimmedString(column.get("column_name"))))
                .append(' ').append(getTrimmedString(column.get("column_type")));
            String identityKind = getTrimmedString(column.get("identity_kind"));
            if ("a".equals(identityKind)) definition.append(" GENERATED ALWAYS AS IDENTITY");
            if ("d".equals(identityKind)) definition.append(" GENERATED BY DEFAULT AS IDENTITY");
            if (isTrue(column.get("is_not_null"))) definition.append(" NOT NULL");
            String defaultValue = getTrimmedString(column.get("default_value"));
            if (!defaultValue.isEmpty()) definition.append(" DEFAULT ").append(defaultValue);
            definitions.add(definition.toString());
        }

        String constraintsSql = """
            SELECT conname AS constraint_name, pg_catalog.pg_get_constraintdef(oid, true) AS constraint_definition
            FROM pg_catalog.pg_constraint
            WHERE conrelid = %s AND contype IN ('p', 'u', 'f', 'c', 'x')
            ORDER BY contype = 'p' DESC, contype, conname
            """.formatted(RELATION_OID_SQL);
        for (Map<String, Object> constraint : query(constraintsSql, databaseName, tableName)) {
            definitions.add("    CONSTRAINT " + sqlIdentifierQuoter.getQuoted(getTrimmedString(constraint.get("constraint_name")))
                + " " + getTrimmedString(constraint.get("constraint_definition")));
        }

        StringBuilder createStatement = new StringBuilder("CREATE TABLE ").append(quotedName).append(" (\n")
            .append(String.join(",\n", definitions)).append("\n);");
        String indexesSql = """
            SELECT pg_catalog.pg_get_indexdef(ix.indexrelid) AS index_definition
            FROM pg_catalog.pg_index ix
            WHERE ix.indrelid = %s
            AND NOT EXISTS (SELECT 1 FROM pg_catalog.pg_constraint co WHERE co.conindid = ix.indexrelid)
            ORDER BY 1
            """.formatted(RELATION_OID_SQL);
        for (Map<String, Object> index : query(indexesSql, databaseName, tableName)) {
            createStatement.append('\n').append(getTrimmedString(index.get("index_definition"))).append(';');
        }
        return createStatement.toString();
    }

    @Override
    public Set<String> getPrimaryKeyColumns(String databaseName, String tableName) throws SQLException {
        String sql = """
            -- getPrimaryKeyColumns
            SELECT a.attname AS column_name
            FROM pg_catalog.pg_index ix
            CROSS JOIN LATERAL unnest(ix.indkey::int2[]) WITH ORDINALITY AS k(attnum, ord)
            JOIN pg_catalog.pg_attribute a ON a.attrelid = ix.indrelid AND a.attnum = k.attnum
            WHERE ix.indrelid = %s
            AND ix.indisprimary
            ORDER BY k.ord
            """.formatted(RELATION_OID_SQL);
        return new LinkedHashSet<>(getColumnValues(query(sql, databaseName, tableName), "column_name"));
    }

    /**
     * El driver devuelve los boolean de pg como Boolean (por un tunel llegan como "1"/"t").
     */
    private boolean isTrue(Object value) {
        if (value instanceof Boolean bool) return bool;
        String text = getTrimmedString(value);
        return "t".equalsIgnoreCase(text) || "true".equalsIgnoreCase(text) || getBool(text);
    }
}
