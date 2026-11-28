package modules.explorer.domain.services;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import modules.shared.domain.services.SqlDialect;
import modules.shared.domain.services.SqlIdentifierQuoter;
import modules.shared.domain.services.SqlLiteralQuoter;

import static core.config.Global.__;

import modules.explorer.domain.enums.ExplorerMessageEnum;
import modules.explorer.domain.enums.TableIndexTypeEnum;
import modules.explorer.domain.entities.TableColumnEntity;
import modules.explorer.domain.entities.TableDefinitionEntity;
import modules.explorer.domain.entities.TableForeignKeyEntity;
import modules.explorer.domain.entities.TableIndexEntity;

/**
 * El SQL del "Alter Table" (MySQL/MariaDB): compara la tabla tal como estaba con la editada y solo genera lo que
 * cambia, en este orden:
 * <ol>
 * <li>ALTER TABLE ... DROP FOREIGN KEY de las claves foraneas quitadas o cambiadas (MySQL no deja quitar y volver a
 * crear una con el mismo nombre en la misma sentencia).</li>
 * <li>Un ALTER TABLE con todo lo demas: DROP COLUMN, DROP INDEX, DROP PRIMARY KEY, ADD/CHANGE COLUMN (con FIRST/AFTER si
 * es nueva o se movio), ADD PRIMARY KEY, ADD INDEX y las opciones (ENGINE, COLLATE, COMMENT, AUTO_INCREMENT,
 * ROW_FORMAT).</li>
 * <li>ALTER TABLE ... ADD CONSTRAINT ... FOREIGN KEY de las nuevas o cambiadas.</li>
 * <li>RENAME TABLE si cambio el nombre (al final: todo lo anterior va con el nombre de antes).</li>
 * </ol>
 * Una tabla nueva ("Create Table") sale entera en un solo CREATE TABLE con las mismas definiciones.
 */
public final class TableAlterSqlBuilder {

    private static final String CLAUSE_SEPARATOR = ",\n    ";

    private final SqlIdentifierQuoter sqlIdentifierQuoter = SqlIdentifierQuoter.getInstance(SqlDialect.MYSQL);
    private final SqlLiteralQuoter sqlLiteralQuoter = SqlLiteralQuoter.getInstance(SqlDialect.MYSQL);
    private final TableNameValidator tableNameValidator = TableNameValidator.getInstance(SqlDialect.MYSQL);

    private TableAlterSqlBuilder() {
    }

    public static TableAlterSqlBuilder getInstance() {
        return new TableAlterSqlBuilder();
    }

    /**
     * Lo que impide guardar: nombres no validos o repetidos, una tabla sin columnas, PK o indices con columnas que no
     * existen. Vacio = se puede.
     */
    public List<String> getErrors(TableDefinitionEntity edited) {
        List<String> errors = new ArrayList<>();
        if (!tableNameValidator.isValid(edited.tableName())) errors.add(__(ExplorerMessageEnum.INVALID_TABLE_NAME_X, edited.tableName()));
        if (edited.columns().isEmpty()) errors.add(__(ExplorerMessageEnum.TABLE_WITHOUT_COLUMNS));
        Set<String> columnNames = new HashSet<>();
        for (TableColumnEntity column : edited.columns()) {
            if (!tableNameValidator.isValid(column.name())) errors.add(__(ExplorerMessageEnum.INVALID_COLUMN_NAME_X, column.name()));
            if (!columnNames.add(column.name().toLowerCase(Locale.ROOT))) errors.add(__(ExplorerMessageEnum.DUPLICATED_COLUMN_X, column.name()));
            if (column.dataType().isBlank()) errors.add(__(ExplorerMessageEnum.COLUMN_WITHOUT_TYPE_X, column.name()));
        }
        for (String primaryKeyColumn : edited.primaryKeyColumns()) {
            if (!columnNames.contains(primaryKeyColumn.toLowerCase(Locale.ROOT))) errors.add(__(ExplorerMessageEnum.UNKNOWN_COLUMN_X_IN_X, primaryKeyColumn, "PRIMARY KEY"));
        }
        Set<String> indexNames = new HashSet<>();
        for (TableIndexEntity index : edited.indexes()) {
            if (!tableNameValidator.isValid(index.name())) errors.add(__(ExplorerMessageEnum.INVALID_INDEX_NAME_X, index.name()));
            if (!indexNames.add(index.name().toLowerCase(Locale.ROOT))) errors.add(__(ExplorerMessageEnum.DUPLICATED_INDEX_X, index.name()));
            if (index.columns().isEmpty()) errors.add(__(ExplorerMessageEnum.INDEX_WITHOUT_COLUMNS_X, index.name()));
            for (String indexColumn : index.columns()) {
                if (!columnNames.contains(getWithoutPrefixLength(indexColumn).toLowerCase(Locale.ROOT))) {
                    errors.add(__(ExplorerMessageEnum.UNKNOWN_COLUMN_X_IN_X, indexColumn, index.name()));
                }
            }
        }
        for (TableForeignKeyEntity foreignKey : edited.foreignKeys()) {
            if (!tableNameValidator.isValid(foreignKey.name())) errors.add(__(ExplorerMessageEnum.INVALID_INDEX_NAME_X, foreignKey.name()));
            if (foreignKey.columns().isEmpty() || foreignKey.columns().size() != foreignKey.referencedColumns().size() || foreignKey.referencedTable().isBlank()) {
                errors.add(__(ExplorerMessageEnum.WRONG_FOREIGN_KEY_X, foreignKey.name()));
            }
            for (String foreignKeyColumn : foreignKey.columns()) {
                if (!columnNames.contains(foreignKeyColumn.toLowerCase(Locale.ROOT))) errors.add(__(ExplorerMessageEnum.UNKNOWN_COLUMN_X_IN_X, foreignKeyColumn, foreignKey.name()));
            }
        }
        return errors;
    }

    /**
     * Vacio si no hay nada que cambiar.
     */
    public List<String> getStatements(String databaseName, TableDefinitionEntity original, TableDefinitionEntity edited) {
        String quotedTable = sqlIdentifierQuoter.getQuoted(databaseName, original.tableName());
        List<String> statements = new ArrayList<>();

        Map<String, TableForeignKeyEntity> editedForeignKeysByOriginal = new HashMap<>();
        for (TableForeignKeyEntity foreignKey : edited.foreignKeys()) {
            if (!foreignKey.isNew()) editedForeignKeysByOriginal.put(foreignKey.originalName(), foreignKey);
        }
        for (TableForeignKeyEntity originalForeignKey : original.foreignKeys()) {
            TableForeignKeyEntity editedForeignKey = editedForeignKeysByOriginal.get(originalForeignKey.originalName());
            if (editedForeignKey == null || !editedForeignKey.equals(originalForeignKey)) {
                statements.add("ALTER TABLE " + quotedTable + " DROP FOREIGN KEY " + sqlIdentifierQuoter.getQuoted(originalForeignKey.originalName()));
            }
        }

        List<String> clauses = new ArrayList<>();
        addColumnDrops(original, edited, clauses);
        addIndexDrops(original, edited, clauses);
        boolean isPrimaryKeyChanged = !original.primaryKeyColumns().equals(edited.primaryKeyColumns());
        if (isPrimaryKeyChanged && !original.primaryKeyColumns().isEmpty()) clauses.add("DROP PRIMARY KEY");
        addColumnChanges(original, edited, clauses);
        if (isPrimaryKeyChanged && !edited.primaryKeyColumns().isEmpty()) clauses.add("ADD PRIMARY KEY (" + getQuotedList(edited.primaryKeyColumns()) + ")");
        addIndexAdds(original, edited, clauses);
        addOptionChanges(original, edited, clauses);
        if (!clauses.isEmpty()) statements.add("ALTER TABLE " + quotedTable + "\n    " + String.join(CLAUSE_SEPARATOR, clauses));

        Map<String, TableForeignKeyEntity> originalForeignKeysByName = new HashMap<>();
        for (TableForeignKeyEntity foreignKey : original.foreignKeys()) originalForeignKeysByName.put(foreignKey.originalName(), foreignKey);
        for (TableForeignKeyEntity foreignKey : edited.foreignKeys()) {
            if (!foreignKey.isNew() && foreignKey.equals(originalForeignKeysByName.get(foreignKey.originalName()))) continue;
            statements.add("ALTER TABLE " + quotedTable + " ADD " + getForeignKeyDefinition(foreignKey));
        }

        if (!original.tableName().equals(edited.tableName())) {
            statements.add("RENAME TABLE " + quotedTable + " TO " + sqlIdentifierQuoter.getQuoted(databaseName, edited.tableName()));
        }
        return statements;
    }

    /**
     * El CREATE TABLE de una tabla nueva: columnas, PK, indices y claves foraneas dentro; las opciones que no esten
     * vacias detras (vacias = las del servidor).
     */
    public List<String> getCreateStatements(String databaseName, TableDefinitionEntity edited) {
        List<String> definitions = new ArrayList<>();
        for (TableColumnEntity column : edited.columns()) definitions.add(getColumnDefinition(column));
        if (!edited.primaryKeyColumns().isEmpty()) definitions.add("PRIMARY KEY (" + getQuotedList(edited.primaryKeyColumns()) + ")");
        for (TableIndexEntity index : edited.indexes()) definitions.add(getIndexDefinition(index));
        for (TableForeignKeyEntity foreignKey : edited.foreignKeys()) definitions.add(getForeignKeyDefinition(foreignKey));

        List<String> options = new ArrayList<>();
        if (!edited.engine().isBlank()) options.add("ENGINE = " + edited.engine().trim());
        if (!edited.collation().isBlank()) options.add("COLLATE = " + edited.collation().trim());
        if (!edited.comment().isEmpty()) options.add("COMMENT = " + sqlLiteralQuoter.getLiteral(edited.comment()));
        if (!edited.autoIncrement().isBlank()) options.add("AUTO_INCREMENT = " + edited.autoIncrement().trim());
        if (!edited.rowFormat().isBlank()) options.add("ROW_FORMAT = " + edited.rowFormat().trim());

        String createSql = "CREATE TABLE " + sqlIdentifierQuoter.getQuoted(databaseName, edited.tableName())
            + " (\n    " + String.join(CLAUSE_SEPARATOR, definitions) + "\n)";
        return List.of(options.isEmpty() ? createSql : createSql + " " + String.join(" ", options));
    }

    /**
     * "`precio` decimal(10,2) unsigned NOT NULL DEFAULT '0.00' COMMENT 'sin IVA'": lo que va tras ADD/CHANGE COLUMN.
     */
    public String getColumnDefinition(TableColumnEntity column) {
        StringBuilder definition = new StringBuilder(sqlIdentifierQuoter.getQuoted(column.name())).append(' ').append(column.dataType().trim());
        if (!column.length().isBlank()) definition.append('(').append(column.length().trim()).append(')');
        if (column.isUnsigned()) definition.append(" UNSIGNED");
        if (column.isZerofill()) definition.append(" ZEROFILL");
        if (!column.characterSet().isBlank()) definition.append(" CHARACTER SET ").append(column.characterSet().trim());
        if (!column.collation().isBlank()) definition.append(" COLLATE ").append(column.collation().trim());
        definition.append(column.isNotNull() ? " NOT NULL" : " NULL");
        if (!column.defaultSql().isBlank()) definition.append(" DEFAULT ").append(column.defaultSql().trim());
        if (!column.onUpdateSql().isBlank()) definition.append(" ON UPDATE ").append(column.onUpdateSql().trim());
        if (column.isAutoIncrement()) definition.append(" AUTO_INCREMENT");
        if (!column.comment().isEmpty()) definition.append(" COMMENT ").append(sqlLiteralQuoter.getLiteral(column.comment()));
        return definition.toString();
    }

    private void addColumnDrops(TableDefinitionEntity original, TableDefinitionEntity edited, List<String> clauses) {
        Set<String> keptOriginalNames = new HashSet<>();
        for (TableColumnEntity column : edited.columns()) {
            if (!column.isNew()) keptOriginalNames.add(column.originalName());
        }
        for (TableColumnEntity column : original.columns()) {
            if (!keptOriginalNames.contains(column.originalName())) clauses.add("DROP COLUMN " + sqlIdentifierQuoter.getQuoted(column.originalName()));
        }
    }

    /**
     * Nuevas: ADD COLUMN con su sitio. Existentes: CHANGE COLUMN si cambio algo o si se movio (con su sitio). Se recorren
     * en el orden final: cada AFTER apunta a una columna que ya esta en su sitio.
     */
    private void addColumnChanges(TableDefinitionEntity original, TableDefinitionEntity edited, List<String> clauses) {
        Map<String, TableColumnEntity> originalColumnsByName = new HashMap<>();
        for (TableColumnEntity column : original.columns()) originalColumnsByName.put(column.originalName(), column);
        Set<String> keptOriginalNames = new HashSet<>();
        for (TableColumnEntity column : edited.columns()) {
            if (!column.isNew()) keptOriginalNames.add(column.originalName());
        }
        Map<String, String> originalPredecessors = new HashMap<>();
        String previousKept = "";
        for (TableColumnEntity column : original.columns()) {
            if (!keptOriginalNames.contains(column.originalName())) continue;
            originalPredecessors.put(column.originalName(), previousKept);
            previousKept = column.originalName();
        }

        String previousName = "";
        String previousExistingOriginal = "";
        for (TableColumnEntity column : edited.columns()) {
            String position = previousName.isEmpty() ? " FIRST" : " AFTER " + sqlIdentifierQuoter.getQuoted(previousName);
            if (column.isNew()) {
                clauses.add("ADD COLUMN " + getColumnDefinition(column) + position);
            }
            else {
                boolean isMoved = !previousExistingOriginal.equals(originalPredecessors.getOrDefault(column.originalName(), ""));
                boolean isChanged = !column.equals(originalColumnsByName.get(column.originalName()));
                if (isChanged || isMoved) {
                    clauses.add("CHANGE COLUMN " + sqlIdentifierQuoter.getQuoted(column.originalName()) + " " + getColumnDefinition(column) + (isMoved ? position : ""));
                }
                previousExistingOriginal = column.originalName();
            }
            previousName = column.name();
        }
    }

    private void addIndexDrops(TableDefinitionEntity original, TableDefinitionEntity edited, List<String> clauses) {
        Map<String, TableIndexEntity> editedIndexesByOriginal = new HashMap<>();
        for (TableIndexEntity index : edited.indexes()) {
            if (!index.isNew()) editedIndexesByOriginal.put(index.originalName(), index);
        }
        for (TableIndexEntity originalIndex : original.indexes()) {
            TableIndexEntity editedIndex = editedIndexesByOriginal.get(originalIndex.originalName());
            if (editedIndex == null || !editedIndex.equals(originalIndex)) clauses.add("DROP INDEX " + sqlIdentifierQuoter.getQuoted(originalIndex.originalName()));
        }
    }

    private void addIndexAdds(TableDefinitionEntity original, TableDefinitionEntity edited, List<String> clauses) {
        Map<String, TableIndexEntity> originalIndexesByName = new HashMap<>();
        for (TableIndexEntity index : original.indexes()) originalIndexesByName.put(index.originalName(), index);
        for (TableIndexEntity index : edited.indexes()) {
            if (!index.isNew() && index.equals(originalIndexesByName.get(index.originalName()))) continue;
            clauses.add("ADD " + getIndexDefinition(index));
        }
    }

    /**
     * "UNIQUE INDEX `ix_email` (`email`)": lo que va tras ADD o dentro de un CREATE TABLE.
     */
    private String getIndexDefinition(TableIndexEntity index) {
        String keyword = TableIndexTypeEnum.INDEX.equals(index.indexType()) ? "INDEX" : index.indexType() + " INDEX";
        return keyword + " " + sqlIdentifierQuoter.getQuoted(index.name()) + " (" + getIndexColumnList(index.columns()) + ")";
    }

    private void addOptionChanges(TableDefinitionEntity original, TableDefinitionEntity edited, List<String> clauses) {
        if (isOptionChanged(original.engine(), edited.engine())) clauses.add("ENGINE = " + edited.engine().trim());
        if (isOptionChanged(original.collation(), edited.collation())) clauses.add("COLLATE = " + edited.collation().trim());
        if (!Objects.equals(original.comment(), edited.comment())) clauses.add("COMMENT = " + sqlLiteralQuoter.getLiteral(edited.comment()));
        if (isOptionChanged(original.autoIncrement(), edited.autoIncrement())) clauses.add("AUTO_INCREMENT = " + edited.autoIncrement().trim());
        if (isOptionChanged(original.rowFormat(), edited.rowFormat())) clauses.add("ROW_FORMAT = " + edited.rowFormat().trim());
    }

    /**
     * Una opcion vaciada no se toca (no hay forma de "quitar" el motor o el cotejamiento).
     */
    private boolean isOptionChanged(String originalValue, String editedValue) {
        return !editedValue.isBlank() && !editedValue.trim().equalsIgnoreCase(originalValue.trim());
    }

    private String getForeignKeyDefinition(TableForeignKeyEntity foreignKey) {
        return "CONSTRAINT " + sqlIdentifierQuoter.getQuoted(foreignKey.name())
            + " FOREIGN KEY (" + getQuotedList(foreignKey.columns()) + ")"
            + " REFERENCES " + sqlIdentifierQuoter.getQuoted(foreignKey.referencedTable()) + " (" + getQuotedList(foreignKey.referencedColumns()) + ")"
            + " ON DELETE " + foreignKey.onDelete() + " ON UPDATE " + foreignKey.onUpdate();
    }

    /**
     * Columnas de un indice; "nombre(10)" es un prefijo: `nombre`(10).
     */
    private String getIndexColumnList(List<String> columns) {
        List<String> quotedColumns = new ArrayList<>();
        for (String column : columns) {
            String columnName = getWithoutPrefixLength(column);
            quotedColumns.add(sqlIdentifierQuoter.getQuoted(columnName) + column.substring(columnName.length()).trim());
        }
        return String.join(", ", quotedColumns);
    }

    private String getWithoutPrefixLength(String indexColumn) {
        int parenthesisIndex = indexColumn.indexOf('(');
        return (parenthesisIndex == -1 ? indexColumn : indexColumn.substring(0, parenthesisIndex)).trim();
    }

    private String getQuotedList(List<String> identifiers) {
        List<String> quotedIdentifiers = new ArrayList<>();
        for (String identifier : identifiers) quotedIdentifiers.add(sqlIdentifierQuoter.getQuoted(identifier));
        return String.join(", ", quotedIdentifiers);
    }
}
