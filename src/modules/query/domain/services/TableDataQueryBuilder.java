package modules.query.domain.services;

import java.util.List;

import modules.shared.domain.services.SqlDialect;
import modules.shared.domain.services.SqlIdentifierQuoter;
import modules.shared.domain.services.SqlLiteralQuoter;

import modules.query.domain.enums.GridFilterOperatorEnum;

/**
 * El SELECT de la pestaña Datos de una tabla (feature 7): SELECT * FROM `bd`.`tabla` [WHERE ...] [ORDER BY ...]
 * LIMIT n. El filtro y el orden los escribe el usuario en SQL, como en SQLyog: van entre parentesis para que un OR del
 * filtro no se mezcle con nada.
 */
public final class TableDataQueryBuilder {

    private final SqlIdentifierQuoter sqlIdentifierQuoter;
    private final SqlLiteralQuoter sqlLiteralQuoter;
    private final boolean isTopLimit;

    private TableDataQueryBuilder(SqlIdentifierQuoter sqlIdentifierQuoter, SqlLiteralQuoter sqlLiteralQuoter, boolean isTopLimit) {
        this.sqlIdentifierQuoter = sqlIdentifierQuoter;
        this.sqlLiteralQuoter = sqlLiteralQuoter;
        this.isTopLimit = isTopLimit;
    }

    /**
     * MySQL.
     */
    public static TableDataQueryBuilder getInstance() {
        return new TableDataQueryBuilder(SqlIdentifierQuoter.getInstance(), SqlLiteralQuoter.getInstance(), false);
    }

    /**
     * LIMIT n vale en MySQL, PostgreSQL y SQLite; solo cambian las comillas de los nombres. SQL Server: SELECT TOP n.
     */
    public static TableDataQueryBuilder getInstance(String dbEngine) {
        return new TableDataQueryBuilder(
            SqlIdentifierQuoter.getInstance(dbEngine),
            SqlLiteralQuoter.getInstance(dbEngine),
            SqlDialect.getInstance(dbEngine).isTopLimit()
        );
    }

    /**
     * Sin ORDER BY del usuario, por la clave primaria: sin orden, PostgreSQL (y SQL Server o SQLite) devuelve las filas
     * en orden fisico, y una fila que se acaba de editar pasa al final y "desaparece" de donde estaba al recargar
     * (parecia que no se habia guardado). Sin clave primaria, como venga.
     */
    public String getSelectSqlOrderedByKey(
        String databaseName,
        String tableName,
        String whereFilter,
        String orderBy,
        int limit,
        List<String> primaryKeyColumns
    ) {
        if (!getWithoutTrailingSemicolons(orderBy).isEmpty() || primaryKeyColumns.isEmpty()) {
            return getSelectSql(databaseName, tableName, whereFilter, orderBy, limit);
        }
        List<String> quotedKeyColumns = primaryKeyColumns.stream().map(sqlIdentifierQuoter::getQuoted).toList();
        return getSelectSql(databaseName, tableName, whereFilter, String.join(", ", quotedKeyColumns), limit);
    }

    /**
     * limit <= 0: sin LIMIT.
     */
    public String getSelectSql(String databaseName, String tableName, String whereFilter, String orderBy, int limit) {
        String top = isTopLimit && limit > 0 ? "TOP " + limit + " " : "";
        StringBuilder selectSql = new StringBuilder("SELECT " + top + "* FROM ")
            .append(sqlIdentifierQuoter.getQuoted(databaseName, tableName));
        String cleanWhere = getWithoutTrailingSemicolons(whereFilter);
        if (!cleanWhere.isEmpty()) {
            selectSql.append("\nWHERE (").append(cleanWhere).append(")");
        }
        String cleanOrderBy = getWithoutTrailingSemicolons(orderBy);
        if (!cleanOrderBy.isEmpty()) {
            selectSql.append("\nORDER BY ").append(cleanOrderBy);
        }
        if (limit > 0 && !isTopLimit) {
            selectSql.append("\nLIMIT ").append(limit);
        }
        return selectSql.toString();
    }

    /**
     * La condicion del submenu Filtro (`columna` = 'valor'...). Con NULL: = y LIKE pasan a IS NULL; <>, NOT LIKE, > y <
     * a IS NOT NULL.
     * @throws IllegalArgumentException si el operador no es de GridFilterOperatorEnum
     */
    public String getColumnCondition(String columnName, String operator, String value) {
        if (!GridFilterOperatorEnum.VALUES.contains(operator)) throw new IllegalArgumentException("Unknown filter operator " + operator);
        String quotedColumn = sqlIdentifierQuoter.getQuoted(columnName);
        if (value == null) {
            boolean isNullMatch = GridFilterOperatorEnum.EQUAL.equals(operator) || GridFilterOperatorEnum.LIKE.equals(operator);
            return quotedColumn + (isNullMatch ? " IS NULL" : " IS NOT NULL");
        }
        boolean isLike = GridFilterOperatorEnum.LIKE.equals(operator) || GridFilterOperatorEnum.NOT_LIKE.equals(operator);
        String literal = sqlLiteralQuoter.getLiteral(isLike ? "%" + value + "%" : value);
        return quotedColumn + " " + operator + " " + literal;
    }

    private String getWithoutTrailingSemicolons(String sqlPart) {
        String cleanPart = sqlPart == null ? "" : sqlPart.trim();
        while (cleanPart.endsWith(";")) {
            cleanPart = cleanPart.substring(0, cleanPart.length() - 1).trim();
        }
        return cleanPart;
    }
}
