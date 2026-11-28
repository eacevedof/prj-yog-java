package modules.files.domain.services;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import modules.files.domain.enums.FilesConfigEnum;

/**
 * Los nombres de columna de una tabla cargada: los de la cabecera o las claves del JSON, con nombre si vienen vacios
 * (column_3) y sin repetirse (SQLite no distingue mayusculas: "Id" e "id" chocan).
 */
public final class ColumnNamesBuilder {

    private ColumnNamesBuilder() {
    }

    public static ColumnNamesBuilder getInstance() {
        return new ColumnNamesBuilder();
    }

    public List<String> getColumnNames(List<String> rawNames) {
        List<String> columnNames = new ArrayList<>();
        Set<String> usedLowerNames = new HashSet<>();
        usedLowerNames.add(FilesConfigEnum.ROW_COLUMN);
        for (int i = 0; i < rawNames.size(); i++) {
            String rawName = rawNames.get(i) == null ? "" : rawNames.get(i).trim();
            String columnName = rawName.isEmpty() ? FilesConfigEnum.DEFAULT_COLUMN_PREFIX + (i + 1) : rawName;
            while (usedLowerNames.contains(columnName.toLowerCase(Locale.ROOT))) {
                columnName = columnName + FilesConfigEnum.DUPLICATE_SUFFIX;
            }
            usedLowerNames.add(columnName.toLowerCase(Locale.ROOT));
            columnNames.add(columnName);
        }
        return columnNames;
    }
}
