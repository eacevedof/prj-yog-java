package modules.dbcopy.domain.services;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import modules.shared.domain.services.SqlIdentifierQuoter;
import modules.shared.domain.services.SqlLiteralQuoter;

import modules.dbcopy.domain.enums.DbCopyObjectTypeEnum;

/**
 * El SQL de la copia entre BD MySQL (feature 29): DROP, el CREATE leido con SHOW CREATE (sin DEFINER si se pide), la
 * lectura por paginas y los INSERT. Los CREATE se ejecutan con la BD destino como activa, por eso no se cualifican.
 */
public final class DbCopySqlBuilder {

    // los tipos que se leen como bytes y se escriben en hexadecimal (X'...'): binarios y geometrias
    private static final Set<String> BINARY_DATA_TYPES = Set.of(
        "binary", "varbinary", "tinyblob", "blob", "mediumblob", "longblob",
        "geometry", "point", "linestring", "polygon", "multipoint", "multilinestring", "multipolygon",
        "geometrycollection", "geomcollection"
    );
    private static final String QUOTED_OR_BARE_PART = "(?:`(?:[^`]|``)*`|'(?:[^'\\\\]|\\\\.|'')*'|\"(?:[^\"\\\\]|\\\\.)*\"|[^\\s@`'\"]+)";
    private static final Pattern DEFINER_PATTERN = Pattern.compile(
        "\\s+DEFINER\\s*=\\s*(?:CURRENT_USER(?:\\s*\\(\\s*\\))?|" + QUOTED_OR_BARE_PART + "\\s*@\\s*" + QUOTED_OR_BARE_PART + ")",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern CREATE_TABLE_PATTERN = Pattern.compile("^(\\s*CREATE\\s+TABLE\\s+)(?!IF\\s+NOT\\s+EXISTS)", Pattern.CASE_INSENSITIVE);

    private final SqlIdentifierQuoter sqlIdentifierQuoter = SqlIdentifierQuoter.getInstance();
    private final SqlLiteralQuoter sqlLiteralQuoter = SqlLiteralQuoter.getInstance();

    private DbCopySqlBuilder() {
    }

    public static DbCopySqlBuilder getInstance() {
        return new DbCopySqlBuilder();
    }

    public boolean isBinaryDataType(String dataType) {
        return dataType != null && BINARY_DATA_TYPES.contains(dataType.toLowerCase(Locale.ROOT));
    }

    /**
     * Un BIT se lee como numero (CAST ... AS UNSIGNED) y se escribe sin comillas: por JDBC llega en bytes y por el
     * tunel PHP en decimal; asi los dos caminos dan lo mismo.
     */
    public boolean isBitDataType(String dataType) {
        return "bit".equalsIgnoreCase(dataType);
    }

    /**
     * "DROP VIEW IF EXISTS `v`" en la BD activa (la destino).
     */
    public String getDropSql(String objectType, String objectName) {
        return "DROP " + getSqlKeyword(objectType) + " IF EXISTS " + sqlIdentifierQuoter.getQuoted(objectName);
    }

    /**
     * "SHOW CREATE TABLE `bd`.`t`".
     */
    public String getShowCreateSql(String objectType, String databaseName, String objectName) {
        return "SHOW CREATE " + getSqlKeyword(objectType) + " " + sqlIdentifierQuoter.getQuoted(databaseName, objectName);
    }

    /**
     * "CREATE TABLE IF NOT EXISTS ...": sin "Borrar en destino", la tabla que ya existe se queda y los datos se añaden.
     */
    public String getCreateTableIfNotExists(String createTableSql) {
        return CREATE_TABLE_PATTERN.matcher(createTableSql).replaceFirst("$1IF NOT EXISTS ");
    }

    /**
     * Quita el primer "DEFINER=`u`@`h`" (el de la cabecera; el cuerpo no lo lleva): el usuario del origen puede no
     * existir en el destino. "SQL SECURITY DEFINER" se queda.
     */
    public String getWithoutDefiner(String createSql) {
        Matcher matcher = DEFINER_PATTERN.matcher(createSql);
        if (!matcher.find()) return createSql;
        return createSql.substring(0, matcher.start()) + createSql.substring(matcher.end());
    }

    /**
     * "CREATE DATABASE IF NOT EXISTS `bd` DEFAULT CHARACTER SET utf8mb4 COLLATE ..." con el juego del origen.
     */
    public String getCreateDatabaseSql(String databaseName, String characterSet, String collation) {
        StringBuilder sql = new StringBuilder("CREATE DATABASE IF NOT EXISTS ").append(sqlIdentifierQuoter.getQuoted(databaseName));
        if (isSafeWord(characterSet)) sql.append(" DEFAULT CHARACTER SET ").append(characterSet);
        if (isSafeWord(collation)) sql.append(" COLLATE ").append(collation);
        return sql.toString();
    }

    /**
     * Renombrar BD: la original, una vez copiada y comprobada.
     */
    public String getDropDatabaseSql(String databaseName) {
        return "DROP DATABASE " + sqlIdentifierQuoter.getQuoted(databaseName);
    }

    /**
     * Renombrar BD: las filas exactas de una tabla, en la columna row_count.
     */
    public String getCountSql(String databaseName, String tableName) {
        return "SELECT COUNT(*) AS row_count FROM " + sqlIdentifierQuoter.getQuoted(databaseName, tableName);
    }

    /**
     * Una pagina de filas. Con clave primaria, por rango de clave (keyset): rapido en tablas enormes y estable; sin
     * ella, por OFFSET. lastKeyValues vacio = la primera pagina.
     */
    public String getSelectPageSql(
        String databaseName, String tableName, List<String> columnNames, Set<String> bitColumns, List<String> primaryKeyColumns,
        List<Object> lastKeyValues, int limit, long offset
    ) {
        StringBuilder sql = new StringBuilder("SELECT ").append(getSelectList(columnNames, bitColumns))
            .append(" FROM ").append(sqlIdentifierQuoter.getQuoted(databaseName, tableName));
        if (primaryKeyColumns.isEmpty()) {
            return sql.append(" LIMIT ").append(limit).append(" OFFSET ").append(offset).toString();
        }
        if (!lastKeyValues.isEmpty()) {
            sql.append(" WHERE ");
            if (primaryKeyColumns.size() == 1) {
                sql.append(sqlIdentifierQuoter.getQuoted(primaryKeyColumns.getFirst())).append(" > ").append(sqlLiteralQuoter.getLiteral(lastKeyValues.getFirst()));
            }
            else {
                sql.append("(").append(getQuotedList(primaryKeyColumns)).append(") > (").append(getLiteralList(lastKeyValues)).append(")");
            }
        }
        return sql.append(" ORDER BY ").append(getQuotedList(primaryKeyColumns)).append(" LIMIT ").append(limit).toString();
    }

    /**
     * "INSERT INTO `t` (`a`, `b`) VALUES " (sin las filas).
     */
    public String getInsertPrefix(String tableName, List<String> columnNames) {
        return "INSERT INTO " + sqlIdentifierQuoter.getQuoted(tableName) + " (" + getQuotedList(columnNames) + ") VALUES ";
    }

    /**
     * "('Ana', NULL, X'cafe')": cada celda es texto (lo que da el servidor), bytes (binarios) o null.
     */
    public String getRowTuple(List<Object> cells) {
        return "(" + getLiteralList(cells) + ")";
    }

    private String getSelectList(List<String> columnNames, Set<String> bitColumns) {
        StringBuilder selectList = new StringBuilder();
        for (String columnName : columnNames) {
            if (!selectList.isEmpty()) selectList.append(", ");
            String quotedName = sqlIdentifierQuoter.getQuoted(columnName);
            if (bitColumns.contains(columnName)) selectList.append("CAST(").append(quotedName).append(" AS UNSIGNED) AS ").append(quotedName);
            else selectList.append(quotedName);
        }
        return selectList.toString();
    }

    private String getQuotedList(List<String> identifiers) {
        StringBuilder quotedList = new StringBuilder();
        for (String identifier : identifiers) {
            if (!quotedList.isEmpty()) quotedList.append(", ");
            quotedList.append(sqlIdentifierQuoter.getQuoted(identifier));
        }
        return quotedList.toString();
    }

    private String getLiteralList(List<Object> values) {
        StringBuilder literalList = new StringBuilder();
        for (Object value : values) {
            if (!literalList.isEmpty()) literalList.append(", ");
            literalList.append(sqlLiteralQuoter.getLiteral(value));
        }
        return literalList.toString();
    }

    private boolean isSafeWord(String word) {
        return word != null && word.matches("[A-Za-z0-9_]+");
    }

    /**
     * La palabra de SQL de un tipo de objeto (DROP VIEW, SHOW CREATE PROCEDURE...).
     */
    private String getSqlKeyword(String objectType) {
        return switch (objectType) {
            case DbCopyObjectTypeEnum.VIEW -> "VIEW";
            case DbCopyObjectTypeEnum.PROCEDURE -> "PROCEDURE";
            case DbCopyObjectTypeEnum.FUNCTION -> "FUNCTION";
            case DbCopyObjectTypeEnum.TRIGGER -> "TRIGGER";
            case DbCopyObjectTypeEnum.EVENT -> "EVENT";
            default -> "TABLE";
        };
    }
}
