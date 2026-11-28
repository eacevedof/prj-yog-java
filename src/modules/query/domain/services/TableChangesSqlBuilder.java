package modules.query.domain.services;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import modules.shared.domain.services.SqlDialect;
import modules.shared.domain.services.SqlIdentifierQuoter;

import modules.query.domain.enums.TableChangeKeyEnum;
import modules.query.domain.entities.PreparedSqlEntity;

/**
 * Los cambios de la rejilla (feature 6) pasados a SQL con parametros: los valores nunca van dentro del SQL, solo los
 * nombres (entre comillas invertidas). UPDATE y DELETE van por clave primaria con LIMIT 1; el INSERT solo lleva las
 * columnas que se rellenaron (el resto toma su DEFAULT, el AUTO_INCREMENT incluido). Las columnas de DEFAULT_COLUMNS
 * van como DEFAULT (menu "Poner DEFAULT").
 */
public final class TableChangesSqlBuilder {

    private final SqlIdentifierQuoter sqlIdentifierQuoter;
    private final SqlDialect sqlDialect;

    private TableChangesSqlBuilder(SqlDialect sqlDialect) {
        this.sqlDialect = sqlDialect;
        this.sqlIdentifierQuoter = SqlIdentifierQuoter.getInstance(sqlDialect.getDbEngine());
    }

    /**
     * MySQL.
     */
    public static TableChangesSqlBuilder getInstance() {
        return new TableChangesSqlBuilder(SqlDialect.getInstance(SqlDialect.MYSQL));
    }

    /**
     * En PostgreSQL y SQLite: comillas dobles, sin LIMIT 1 (la clave primaria ya toca una sola fila) y
     * INSERT ... DEFAULT VALUES para un alta vacia.
     */
    public static TableChangesSqlBuilder getInstance(String dbEngine) {
        return new TableChangesSqlBuilder(SqlDialect.getInstance(dbEngine));
    }

    /**
     * @throws IllegalArgumentException si un cambio no trae la clave primaria completa o no tiene nada que hacer
     */
    @SuppressWarnings("unchecked")
    public List<PreparedSqlEntity> getStatements(
        String databaseName,
        String tableName,
        Set<String> primaryKeyColumns,
        List<Map<String, Object>> changes
    ) {
        String quotedTable = sqlIdentifierQuoter.getQuoted(databaseName, tableName);
        List<PreparedSqlEntity> statements = new ArrayList<>();
        for (Map<String, Object> change : changes) {
            String changeType = String.valueOf(change.get(TableChangeKeyEnum.CHANGE_TYPE));
            Map<String, String> keyValues = (Map<String, String>) change.getOrDefault(TableChangeKeyEnum.KEY_VALUES, Map.of());
            Map<String, String> newValues = (Map<String, String>) change.getOrDefault(TableChangeKeyEnum.NEW_VALUES, Map.of());
            List<String> defaultColumns = (List<String>) change.getOrDefault(TableChangeKeyEnum.DEFAULT_COLUMNS, List.of());
            switch (changeType) {
                case TableChangeKeyEnum.TYPE_INSERT -> statements.add(getInsert(quotedTable, newValues, defaultColumns));
                case TableChangeKeyEnum.TYPE_UPDATE -> {
                    if (newValues.isEmpty() && defaultColumns.isEmpty()) continue;
                    statements.add(getUpdate(quotedTable, primaryKeyColumns, keyValues, newValues, defaultColumns));
                }
                case TableChangeKeyEnum.TYPE_DELETE -> statements.add(getDelete(quotedTable, primaryKeyColumns, keyValues));
                default -> throw new IllegalArgumentException("Unknown change type " + changeType);
            }
        }
        return statements;
    }

    private PreparedSqlEntity getInsert(String quotedTable, Map<String, String> newValues, List<String> defaultColumns) {
        if (newValues.isEmpty() && defaultColumns.isEmpty()) {
            return PreparedSqlEntity.of("INSERT INTO " + quotedTable + sqlDialect.getEmptyInsertSuffix(), List.of(), false);
        }
        List<String> quotedColumns = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        for (Map.Entry<String, String> entry : newValues.entrySet()) {
            quotedColumns.add(sqlIdentifierQuoter.getQuoted(entry.getKey()));
            params.add(entry.getValue());
        }
        List<String> placeholders = new ArrayList<>(Collections.nCopies(params.size(), "?"));
        for (String defaultColumn : defaultColumns) {
            if (newValues.containsKey(defaultColumn)) continue;
            quotedColumns.add(sqlIdentifierQuoter.getQuoted(defaultColumn));
            placeholders.add("DEFAULT");
        }
        return PreparedSqlEntity.of(
            "INSERT INTO " + quotedTable + " (" + String.join(", ", quotedColumns) + ") VALUES (" + String.join(", ", placeholders) + ")",
            params,
            false
        );
    }

    private PreparedSqlEntity getUpdate(
        String quotedTable,
        Set<String> primaryKeyColumns,
        Map<String, String> keyValues,
        Map<String, String> newValues,
        List<String> defaultColumns
    ) {
        List<String> assignments = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        for (Map.Entry<String, String> entry : newValues.entrySet()) {
            assignments.add(sqlIdentifierQuoter.getQuoted(entry.getKey()) + " = ?");
            params.add(entry.getValue());
        }
        for (String defaultColumn : defaultColumns) {
            if (!newValues.containsKey(defaultColumn)) assignments.add(sqlIdentifierQuoter.getQuoted(defaultColumn) + " = DEFAULT");
        }
        String where = getWhereByKey(primaryKeyColumns, keyValues, params);
        return PreparedSqlEntity.of("UPDATE " + quotedTable + " SET " + String.join(", ", assignments) + where + getLimitOne(), params, true);
    }

    private PreparedSqlEntity getDelete(String quotedTable, Set<String> primaryKeyColumns, Map<String, String> keyValues) {
        List<Object> params = new ArrayList<>();
        String where = getWhereByKey(primaryKeyColumns, keyValues, params);
        return PreparedSqlEntity.of("DELETE FROM " + quotedTable + where + getLimitOne(), params, true);
    }

    private String getLimitOne() {
        return sqlDialect.isLimitInUpdateSupported() ? " LIMIT 1" : "";
    }

    /**
     * " WHERE `a` = ? AND `b` = ?" con la clave primaria entera; sin ella no se toca nada.
     */
    private String getWhereByKey(Set<String> primaryKeyColumns, Map<String, String> keyValues, List<Object> params) {
        if (primaryKeyColumns.isEmpty() || !keyValues.keySet().equals(primaryKeyColumns)) {
            throw new IllegalArgumentException("Primary key " + primaryKeyColumns + " expected, got " + keyValues.keySet());
        }
        List<String> conditions = new ArrayList<>();
        for (String primaryKeyColumn : primaryKeyColumns) {
            conditions.add(sqlIdentifierQuoter.getQuoted(primaryKeyColumn) + " = ?");
            params.add(keyValues.get(primaryKeyColumn));
        }
        return " WHERE " + String.join(" AND ", conditions);
    }
}
