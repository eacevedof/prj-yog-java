package modules.explorer.infrastructure.repositories;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import modules.shared.domain.services.SqlIdentifierQuoter;
import modules.shared.application.Primitives;
import static modules.shared.application.Primitives.getTrimmedString;

import modules.explorer.domain.enums.ExplorerKeyEnum;

/**
 * Lo que pinta el arbol, leido de information_schema: el servidor ya filtra por los permisos del usuario.
 */
public final class ExplorerReaderMysqlRepository extends AbstractExplorerReaderRepository {

    private ExplorerReaderMysqlRepository(Connection pdo) {
        super(pdo);
    }

    public static ExplorerReaderMysqlRepository getInstance(Connection pdo) {
        return new ExplorerReaderMysqlRepository(pdo);
    }

    @Override
    public List<String> getDatabaseNames() throws SQLException {
        String sql = """
            -- getDatabaseNames
            SELECT SCHEMA_NAME AS database_name
            FROM information_schema.SCHEMATA
            ORDER BY SCHEMA_NAME
            """;
        return getColumnValues(query(sql), ExplorerKeyEnum.DATABASE_NAME);
    }

    /**
     * [tables => [...], views => [...]] de una BD, por nombre.
     */
    @Override
    public Map<String, Object> getTablesAndViews(String databaseName) throws SQLException {
        String sql = """
            -- getTablesAndViews
            SELECT TABLE_NAME AS table_name, TABLE_TYPE AS table_type
            FROM information_schema.TABLES
            WHERE 1=1
            AND TABLE_SCHEMA = ?
            ORDER BY TABLE_NAME
            """;
        List<String> tables = new ArrayList<>();
        List<String> views = new ArrayList<>();
        for (Map<String, Object> row : query(sql, databaseName)) {
            String tableName = getTrimmedString(row.get("table_name"));
            if (getTrimmedString(row.get(ExplorerKeyEnum.TABLE_TYPE)).contains(ExplorerKeyEnum.TABLE_TYPE_VIEW)) {
                views.add(tableName);
                continue;
            }
            tables.add(tableName);
        }
        return Primitives.of(ExplorerKeyEnum.TABLES, tables, ExplorerKeyEnum.VIEWS, views);
    }

    /**
     * [procedures => [...], functions => [...]] de una BD, por nombre.
     */
    @Override
    public Map<String, Object> getRoutines(String databaseName) throws SQLException {
        String sql = """
            -- getRoutines
            SELECT ROUTINE_NAME AS routine_name, ROUTINE_TYPE AS routine_type
            FROM information_schema.ROUTINES
            WHERE 1=1
            AND ROUTINE_SCHEMA = ?
            ORDER BY ROUTINE_NAME
            """;
        List<String> procedures = new ArrayList<>();
        List<String> functions = new ArrayList<>();
        for (Map<String, Object> row : query(sql, databaseName)) {
            String routineName = getTrimmedString(row.get("routine_name"));
            if ("FUNCTION".equals(getTrimmedString(row.get("routine_type")))) {
                functions.add(routineName);
                continue;
            }
            procedures.add(routineName);
        }
        return Primitives.of(ExplorerKeyEnum.PROCEDURES, procedures, ExplorerKeyEnum.FUNCTIONS, functions);
    }

    @Override
    public List<String> getTriggerNames(String databaseName) throws SQLException {
        String sql = """
            -- getTriggerNames
            SELECT TRIGGER_NAME AS trigger_name
            FROM information_schema.TRIGGERS
            WHERE 1=1
            AND TRIGGER_SCHEMA = ?
            ORDER BY TRIGGER_NAME
            """;
        return getColumnValues(query(sql, databaseName), "trigger_name");
    }

    /**
     * Las columnas de una tabla o vista, en su orden: nombre, tipo, si admite NULL y su clave (PRI, UNI, MUL).
     */
    @Override
    public List<Map<String, Object>> getTableColumns(String databaseName, String tableName) throws SQLException {
        String sql = """
            -- getTableColumns
            SELECT COLUMN_NAME AS column_name, COLUMN_TYPE AS column_type,
                IS_NULLABLE AS is_nullable, COLUMN_KEY AS column_key, DATA_TYPE AS data_type, EXTRA AS extra
            FROM information_schema.COLUMNS
            WHERE 1=1
            AND TABLE_SCHEMA = ?
            AND TABLE_NAME = ?
            ORDER BY ORDINAL_POSITION
            """;
        List<Map<String, Object>> columns = new ArrayList<>();
        for (Map<String, Object> row : query(sql, databaseName, tableName)) {
            columns.add(Primitives.of(
                ExplorerKeyEnum.COLUMN_NAME, getTrimmedString(row.get("column_name")),
                ExplorerKeyEnum.COLUMN_TYPE, getTrimmedString(row.get("column_type")),
                ExplorerKeyEnum.IS_NULLABLE, "YES".equals(getTrimmedString(row.get("is_nullable"))),
                ExplorerKeyEnum.COLUMN_KEY, getTrimmedString(row.get("column_key")),
                ExplorerKeyEnum.DATA_TYPE, getTrimmedString(row.get("data_type")).toLowerCase(Locale.ROOT),
                ExplorerKeyEnum.EXTRA, getTrimmedString(row.get("extra")).toLowerCase(Locale.ROOT)
            ));
        }
        return columns;
    }

    /**
     * Engine, filas (estimadas en InnoDB), tamaños, auto_increment, cotejamiento y fechas de una tabla o vista. Vacio si
     * no existe.
     */
    @Override
    public Map<String, Object> getTableStatus(String databaseName, String tableName) throws SQLException {
        String sql = """
            -- getTableStatus
            SELECT TABLE_TYPE AS table_type, ENGINE AS engine, TABLE_ROWS AS table_rows,
                DATA_LENGTH AS data_length, INDEX_LENGTH AS index_length, AUTO_INCREMENT AS auto_increment,
                TABLE_COLLATION AS table_collation, CREATE_TIME AS create_time, UPDATE_TIME AS update_time,
                TABLE_COMMENT AS table_comment
            FROM information_schema.TABLES
            WHERE 1=1
            AND TABLE_SCHEMA = ?
            AND TABLE_NAME = ?
            """;
        List<Map<String, Object>> rows = query(sql, databaseName, tableName);
        return rows.isEmpty() ? Primitives.of() : rows.getFirst();
    }

    /**
     * Un indice por fila y columna (los compuestos salen en varias filas, en su orden).
     */
    @Override
    public List<Map<String, Object>> getIndexes(String databaseName, String tableName) throws SQLException {
        String sql = """
            -- getIndexes
            SELECT INDEX_NAME AS index_name, NON_UNIQUE AS non_unique, SEQ_IN_INDEX AS seq_in_index,
                COLUMN_NAME AS column_name, INDEX_TYPE AS index_type
            FROM information_schema.STATISTICS
            WHERE 1=1
            AND TABLE_SCHEMA = ?
            AND TABLE_NAME = ?
            ORDER BY INDEX_NAME = 'PRIMARY' DESC, INDEX_NAME, SEQ_IN_INDEX
            """;
        return query(sql, databaseName, tableName);
    }

    /**
     * El DDL: SHOW CREATE TABLE o SHOW CREATE VIEW (el nombre no se puede enlazar con ?: va entre comillas invertidas).
     */
    @Override
    public String getCreateStatement(String databaseName, String tableName, boolean isView) throws SQLException {
        String sql = "SHOW CREATE " + (isView ? "VIEW " : "TABLE ") + SqlIdentifierQuoter.getInstance().getQuoted(databaseName, tableName);
        List<Map<String, Object>> rows = query(sql);
        if (rows.isEmpty()) return "";
        // la segunda columna es el DDL ("Create Table" o "Create View")
        return getTrimmedString(rows.getFirst().values().stream().skip(1).findFirst().orElse(""));
    }

    @Override
    public Set<String> getPrimaryKeyColumns(String databaseName, String tableName) throws SQLException {
        String sql = """
            -- getPrimaryKeyColumns
            SELECT COLUMN_NAME AS column_name
            FROM information_schema.KEY_COLUMN_USAGE
            WHERE 1=1
            AND TABLE_SCHEMA = ?
            AND TABLE_NAME = ?
            AND CONSTRAINT_NAME = 'PRIMARY'
            ORDER BY ORDINAL_POSITION
            """;
        return new LinkedHashSet<>(getColumnValues(query(sql, databaseName, tableName), "column_name"));
    }
}
