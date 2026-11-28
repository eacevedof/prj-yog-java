package modules.explorer.infrastructure.repositories;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import modules.shared.domain.services.SqlLiteralQuoter;
import modules.shared.infrastructure.repositories.AbstractJdbcRepository;
import static modules.shared.application.Primitives.getTrimmedString;

import modules.explorer.domain.enums.ExplorerKeyEnum;
import modules.explorer.domain.enums.TableIndexTypeEnum;
import modules.explorer.domain.entities.TableColumnEntity;
import modules.explorer.domain.entities.TableDefinitionEntity;
import modules.explorer.domain.entities.TableForeignKeyEntity;
import modules.explorer.domain.entities.TableIndexEntity;

/**
 * La definicion de una tabla MySQL/MariaDB para el editor de tablas, leida de information_schema: columnas (con el tipo
 * partido en base, longitud y flags), clave primaria, indices, claves foraneas y opciones. Los DEFAULT salen como
 * fragmento SQL listo para escribir tras DEFAULT: MySQL los da sin comillas (y marca las expresiones con
 * DEFAULT_GENERATED), MariaDB ya como SQL.
 */
public final class TableDefinitionReaderMysqlRepository extends AbstractJdbcRepository {

    private static final Pattern ON_UPDATE_PATTERN = Pattern.compile("on update (current_timestamp(?:\\(\\d*\\))?)", Pattern.CASE_INSENSITIVE);
    private static final Pattern NUMBER_PATTERN = Pattern.compile("-?\\d+(?:\\.\\d+)?(?:[eE][-+]?\\d+)?");
    private static final Pattern CURRENT_TIMESTAMP_PATTERN = Pattern.compile("(?i)(current_timestamp|now|localtime|localtimestamp)(\\(\\d*\\))?");
    private static final Set<String> NUMERIC_TYPES = Set.of(
        "tinyint", "smallint", "mediumint", "int", "integer", "bigint", "decimal", "numeric", "float", "double", "real", "bit", "year"
    );

    private final SqlLiteralQuoter sqlLiteralQuoter = SqlLiteralQuoter.getInstance();

    private TableDefinitionReaderMysqlRepository(Connection pdo) {
        super(pdo);
    }

    public static TableDefinitionReaderMysqlRepository getInstance(Connection pdo) {
        return new TableDefinitionReaderMysqlRepository(pdo);
    }

    public TableDefinitionEntity getTableDefinition(String databaseName, String tableName) throws SQLException {
        boolean isMariaDb = getText(query("SELECT VERSION() AS server_version").getFirst().get("server_version")).toLowerCase(Locale.ROOT).contains("mariadb");
        List<TableColumnEntity> columns = getColumns(databaseName, tableName, isMariaDb);
        List<Map<String, Object>> indexRows = getIndexRows(databaseName, tableName);
        List<String> primaryKeyColumns = getPrimaryKeyColumns(indexRows);
        List<TableIndexEntity> indexes = getIndexes(indexRows);
        Map<String, Object> options = query("""
            -- getTableOptions
            SELECT ENGINE AS engine, TABLE_COLLATION AS table_collation, TABLE_COMMENT AS table_comment,
                AUTO_INCREMENT AS auto_increment, ROW_FORMAT AS row_format
            FROM information_schema.TABLES
            WHERE 1
            AND TABLE_SCHEMA = ?
            AND TABLE_NAME = ?
            """, databaseName, tableName).stream().findFirst().orElse(Map.of());
        return new TableDefinitionEntity(
            tableName,
            columns,
            primaryKeyColumns,
            indexes,
            getForeignKeys(databaseName, tableName),
            getText(options.get("engine")),
            getText(options.get("table_collation")),
            getText(options.get("table_comment")),
            getText(options.get("auto_increment")),
            getText(options.get("row_format"))
        );
    }

    /**
     * Los motores que el servidor deja usar (los de SHOW ENGINES con Support YES o DEFAULT).
     */
    public List<String> getEngines() throws SQLException {
        List<String> engines = new ArrayList<>();
        for (Map<String, Object> row : query("SELECT ENGINE AS engine, SUPPORT AS support FROM information_schema.ENGINES ORDER BY ENGINE")) {
            String support = getText(row.get("support")).toUpperCase(Locale.ROOT);
            if ("YES".equals(support) || "DEFAULT".equals(support)) engines.add(getText(row.get("engine")));
        }
        return engines;
    }

    public List<String> getCollations() throws SQLException {
        List<String> collations = new ArrayList<>();
        for (Map<String, Object> row : query("SELECT COLLATION_NAME AS collation_name FROM information_schema.COLLATIONS ORDER BY COLLATION_NAME")) {
            collations.add(getText(row.get("collation_name")));
        }
        return collations;
    }

    private List<TableColumnEntity> getColumns(String databaseName, String tableName, boolean isMariaDb) throws SQLException {
        List<TableColumnEntity> columns = new ArrayList<>();
        for (Map<String, Object> row : query("""
            -- getColumns
            SELECT COLUMN_NAME AS column_name, DATA_TYPE AS data_type, COLUMN_TYPE AS column_type, IS_NULLABLE AS is_nullable,
                COLUMN_DEFAULT AS column_default, EXTRA AS extra, CHARACTER_SET_NAME AS character_set_name,
                COLLATION_NAME AS collation_name, COLUMN_COMMENT AS column_comment
            FROM information_schema.COLUMNS
            WHERE 1
            AND TABLE_SCHEMA = ?
            AND TABLE_NAME = ?
            ORDER BY ORDINAL_POSITION
            """, databaseName, tableName)) {
            String columnName = getText(row.get("column_name"));
            String dataType = getText(row.get("data_type")).toLowerCase(Locale.ROOT);
            String columnType = getText(row.get("column_type"));
            String extra = getText(row.get("extra"));
            int openIndex = columnType.indexOf('(');
            int closeIndex = columnType.lastIndexOf(')');
            String length = openIndex != -1 && closeIndex > openIndex ? columnType.substring(openIndex + 1, closeIndex) : "";
            String typeFlags = (closeIndex != -1 ? columnType.substring(closeIndex + 1) : columnType).toLowerCase(Locale.ROOT);
            Matcher onUpdateMatcher = ON_UPDATE_PATTERN.matcher(extra);
            String rawDefault = row.get("column_default") == null ? null : getText(row.get("column_default"));
            columns.add(new TableColumnEntity(
                columnName,
                columnName,
                dataType,
                length,
                typeFlags.contains("unsigned"),
                typeFlags.contains("zerofill"),
                "NO".equalsIgnoreCase(getText(row.get("is_nullable"))),
                extra.toLowerCase(Locale.ROOT).contains("auto_increment"),
                getDefaultSql(rawDefault, dataType, extra, isMariaDb),
                onUpdateMatcher.find() ? onUpdateMatcher.group(1).toUpperCase(Locale.ROOT) : "",
                getText(row.get("character_set_name")),
                getText(row.get("collation_name")),
                getText(row.get("column_comment")),
                extra.toUpperCase(Locale.ROOT).contains("GENERATED") && !extra.toUpperCase(Locale.ROOT).contains("DEFAULT_GENERATED")
            ));
        }
        return columns;
    }

    /**
     * El DEFAULT como fragmento SQL. MariaDB ya lo da asi ('texto', NULL, current_timestamp()). MySQL: sin DEFAULT =
     * vacio; una expresion (DEFAULT_GENERATED) tal cual si es CURRENT_TIMESTAMP y entre parentesis si es otra; un
     * numero o un b'0' sin comillas; el resto, literal entre comillas.
     */
    private String getDefaultSql(String rawDefault, String dataType, String extra, boolean isMariaDb) {
        if (rawDefault == null) return "";
        if (isMariaDb) return rawDefault;
        if (extra.toUpperCase(Locale.ROOT).contains("DEFAULT_GENERATED")) {
            return CURRENT_TIMESTAMP_PATTERN.matcher(rawDefault).matches() ? rawDefault.toUpperCase(Locale.ROOT) : "(" + rawDefault + ")";
        }
        if (NUMERIC_TYPES.contains(dataType) && (NUMBER_PATTERN.matcher(rawDefault).matches() || rawDefault.startsWith("b'"))) return rawDefault;
        return sqlLiteralQuoter.getLiteral(rawDefault);
    }

    /**
     * Las columnas de todos los indices de la tabla, la clave primaria primero y en su orden.
     */
    private List<Map<String, Object>> getIndexRows(String databaseName, String tableName) throws SQLException {
        return query("""
            -- getIndexes
            SELECT INDEX_NAME AS index_name, NON_UNIQUE AS non_unique, COLUMN_NAME AS column_name, SUB_PART AS sub_part,
                INDEX_TYPE AS index_type
            FROM information_schema.STATISTICS
            WHERE 1
            AND TABLE_SCHEMA = ?
            AND TABLE_NAME = ?
            ORDER BY INDEX_NAME = 'PRIMARY' DESC, INDEX_NAME, SEQ_IN_INDEX
            """, databaseName, tableName);
    }

    private List<String> getPrimaryKeyColumns(List<Map<String, Object>> indexRows) {
        List<String> primaryKeyColumns = new ArrayList<>();
        for (Map<String, Object> row : indexRows) {
            String columnName = getText(row.get("column_name"));
            if (ExplorerKeyEnum.PRIMARY_INDEX_NAME.equals(getText(row.get("index_name"))) && !columnName.isEmpty()) primaryKeyColumns.add(columnName);
        }
        return primaryKeyColumns;
    }

    /**
     * Los indices que no son la clave primaria. Los indices sobre expresiones (MySQL 8) no se editan: no salen.
     */
    private List<TableIndexEntity> getIndexes(List<Map<String, Object>> indexRows) {
        Map<String, String> typesByIndex = new LinkedHashMap<>();
        Map<String, List<String>> columnsByIndex = new LinkedHashMap<>();
        List<String> expressionIndexes = new ArrayList<>();
        for (Map<String, Object> row : indexRows) {
            String indexName = getText(row.get("index_name"));
            String columnName = getText(row.get("column_name"));
            if (columnName.isEmpty()) {
                expressionIndexes.add(indexName);
                continue;
            }
            String subPart = getText(row.get("sub_part"));
            if (ExplorerKeyEnum.PRIMARY_INDEX_NAME.equals(indexName)) continue;
            String indexType = getText(row.get("index_type")).toUpperCase(Locale.ROOT);
            typesByIndex.putIfAbsent(indexName, switch (indexType) {
                case TableIndexTypeEnum.FULLTEXT -> TableIndexTypeEnum.FULLTEXT;
                case TableIndexTypeEnum.SPATIAL -> TableIndexTypeEnum.SPATIAL;
                default -> "0".equals(getText(row.get("non_unique"))) ? TableIndexTypeEnum.UNIQUE : TableIndexTypeEnum.INDEX;
            });
            columnsByIndex.computeIfAbsent(indexName, name -> new ArrayList<>()).add(subPart.isEmpty() ? columnName : columnName + "(" + subPart + ")");
        }
        List<TableIndexEntity> indexes = new ArrayList<>();
        for (Map.Entry<String, String> entry : typesByIndex.entrySet()) {
            if (expressionIndexes.contains(entry.getKey())) continue;
            indexes.add(new TableIndexEntity(entry.getKey(), entry.getKey(), entry.getValue(), List.copyOf(columnsByIndex.get(entry.getKey()))));
        }
        return indexes;
    }

    private List<TableForeignKeyEntity> getForeignKeys(String databaseName, String tableName) throws SQLException {
        Map<String, Map<String, Object>> firstRowsByName = new LinkedHashMap<>();
        Map<String, List<String>> columnsByName = new LinkedHashMap<>();
        Map<String, List<String>> referencedColumnsByName = new LinkedHashMap<>();
        for (Map<String, Object> row : query("""
            -- getForeignKeys
            SELECT rc.CONSTRAINT_NAME AS constraint_name, rc.REFERENCED_TABLE_NAME AS referenced_table, rc.DELETE_RULE AS delete_rule,
                rc.UPDATE_RULE AS update_rule, k.COLUMN_NAME AS column_name, k.REFERENCED_COLUMN_NAME AS referenced_column
            FROM information_schema.REFERENTIAL_CONSTRAINTS rc
            JOIN information_schema.KEY_COLUMN_USAGE k
                ON k.CONSTRAINT_SCHEMA = rc.CONSTRAINT_SCHEMA AND k.CONSTRAINT_NAME = rc.CONSTRAINT_NAME AND k.TABLE_NAME = rc.TABLE_NAME
            WHERE 1
            AND rc.CONSTRAINT_SCHEMA = ?
            AND rc.TABLE_NAME = ?
            ORDER BY rc.CONSTRAINT_NAME, k.ORDINAL_POSITION
            """, databaseName, tableName)) {
            String constraintName = getText(row.get("constraint_name"));
            firstRowsByName.putIfAbsent(constraintName, row);
            columnsByName.computeIfAbsent(constraintName, name -> new ArrayList<>()).add(getText(row.get("column_name")));
            referencedColumnsByName.computeIfAbsent(constraintName, name -> new ArrayList<>()).add(getText(row.get("referenced_column")));
        }
        List<TableForeignKeyEntity> foreignKeys = new ArrayList<>();
        for (Map.Entry<String, Map<String, Object>> entry : firstRowsByName.entrySet()) {
            Map<String, Object> row = entry.getValue();
            foreignKeys.add(new TableForeignKeyEntity(
                entry.getKey(),
                entry.getKey(),
                List.copyOf(columnsByName.get(entry.getKey())),
                getText(row.get("referenced_table")),
                List.copyOf(referencedColumnsByName.get(entry.getKey())),
                getText(row.get("delete_rule")).toUpperCase(Locale.ROOT),
                getText(row.get("update_rule")).toUpperCase(Locale.ROOT)
            ));
        }
        return foreignKeys;
    }

    /**
     * information_schema por el tunel puede llegar en bytes.
     */
    private String getText(Object value) {
        if (value instanceof byte[] bytes) return new String(bytes, StandardCharsets.UTF_8);
        return getTrimmedString(value);
    }
}
