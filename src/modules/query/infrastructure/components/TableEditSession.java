package modules.query.infrastructure.components;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import modules.shared.application.Primitives;

import modules.query.domain.enums.TableChangeKeyEnum;
import modules.query.domain.enums.TableEditRowStateEnum;

/**
 * Lo editado en la rejilla de la pestaña Datos (feature 6) hasta que se guarda: que filas son nuevas, cuales se han
 * cambiado (y sus valores originales, para el WHERE por clave primaria) y cuales se borraran. Las filas se siguen por
 * identidad (la misma List), asi que se pueden editar en sitio. Sin JavaFX: se prueba sola.
 */
public final class TableEditSession {

    private final List<String> columnNames;
    private final Set<String> primaryKeyColumns;
    private final Set<String> autoIncrementColumns;
    private final Map<List<String>, List<String>> originalValuesByRow = new IdentityHashMap<>();
    private final Map<List<String>, String> statesByRow = new IdentityHashMap<>();
    private final List<List<String>> newRowsInOrder = new ArrayList<>();
    // celdas puestas a DEFAULT (menu "Poner DEFAULT"): se ven como (DEFAULT) y van como DEFAULT en el SQL
    private final Map<List<String>, Set<Integer>> defaultColumnsByRow = new IdentityHashMap<>();

    private TableEditSession(List<String> columnNames, Set<String> primaryKeyColumns, Set<String> autoIncrementColumns) {
        this.columnNames = List.copyOf(columnNames);
        this.primaryKeyColumns = primaryKeyColumns;
        this.autoIncrementColumns = autoIncrementColumns;
    }

    /**
     * Una sesion de edicion nueva por rejilla: guarda los cambios pendientes de sus filas.
     */
    public static TableEditSession getInstance(List<String> columnNames, Set<String> primaryKeyColumns, Set<String> autoIncrementColumns) {
        return new TableEditSession(columnNames, primaryKeyColumns, autoIncrementColumns);
    }

    /**
     * Las filas que llegan del servidor: se apunta su valor original antes de que nadie las toque.
     */
    public void trackLoadedRows(List<List<String>> rows) {
        for (List<String> row : rows) {
            originalValuesByRow.put(row, new ArrayList<>(row));
        }
    }

    public String getState(List<String> row) {
        return statesByRow.get(row);
    }

    public void setValue(List<String> row, int columnIndex, String value) {
        row.set(columnIndex, value);
        Set<Integer> defaultColumns = defaultColumnsByRow.get(row);
        if (defaultColumns != null) defaultColumns.remove(columnIndex);
        updateLoadedRowState(row);
    }

    /**
     * La celda queda como DEFAULT: el servidor pone el valor por defecto de la columna al guardar.
     */
    public void setDefault(List<String> row, int columnIndex) {
        if (TableEditRowStateEnum.STATE_DELETED.equals(statesByRow.get(row))) return;
        row.set(columnIndex, null);
        defaultColumnsByRow.computeIfAbsent(row, defaultRow -> new HashSet<>()).add(columnIndex);
        updateLoadedRowState(row);
    }

    public boolean isDefault(List<String> row, int columnIndex) {
        Set<Integer> defaultColumns = defaultColumnsByRow.get(row);
        return defaultColumns != null && defaultColumns.contains(columnIndex);
    }

    /**
     * Una fila del servidor esta cambiada si algun valor difiere del original o tiene alguna celda a DEFAULT.
     */
    private void updateLoadedRowState(List<String> row) {
        if (TableEditRowStateEnum.STATE_NEW.equals(statesByRow.get(row)) || TableEditRowStateEnum.STATE_DELETED.equals(statesByRow.get(row))) return;
        List<String> originalValues = originalValuesByRow.get(row);
        if (originalValues == null) return;
        Set<Integer> defaultColumns = defaultColumnsByRow.get(row);
        if (originalValues.equals(row) && (defaultColumns == null || defaultColumns.isEmpty())) {
            statesByRow.remove(row);
            return;
        }
        statesByRow.put(row, TableEditRowStateEnum.STATE_MODIFIED);
    }

    /**
     * Fila vacia (todo NULL = DEFAULT al insertar).
     */
    public List<String> newRow() {
        List<String> row = new ArrayList<>();
        for (int i = 0; i < columnNames.size(); i++) {
            row.add(null);
        }
        statesByRow.put(row, TableEditRowStateEnum.STATE_NEW);
        newRowsInOrder.add(row);
        return row;
    }

    /**
     * Copia de una fila como nueva, sin las columnas AUTO_INCREMENT (las pone el servidor).
     */
    public List<String> duplicateRow(List<String> sourceRow) {
        List<String> row = newRow();
        for (int i = 0; i < columnNames.size(); i++) {
            row.set(i, autoIncrementColumns.contains(columnNames.get(i)) ? null : sourceRow.get(i));
        }
        return row;
    }

    /**
     * true: la fila era nueva y desaparece sin mas (la rejilla la quita); false: queda marcada para borrar.
     */
    public boolean deleteRow(List<String> row) {
        if (TableEditRowStateEnum.STATE_NEW.equals(statesByRow.get(row))) {
            statesByRow.remove(row);
            newRowsInOrder.remove(row);
            defaultColumnsByRow.remove(row);
            return true;
        }
        if (originalValuesByRow.containsKey(row)) {
            // se borra la fila tal como estaba en el servidor
            row.clear();
            row.addAll(originalValuesByRow.get(row));
            defaultColumnsByRow.remove(row);
            statesByRow.put(row, TableEditRowStateEnum.STATE_DELETED);
        }
        return false;
    }

    public void undeleteRow(List<String> row) {
        if (TableEditRowStateEnum.STATE_DELETED.equals(statesByRow.get(row))) statesByRow.remove(row);
    }

    public int getChangeCount() {
        return statesByRow.size();
    }

    public boolean hasChanges() {
        return !statesByRow.isEmpty();
    }

    /**
     * Las filas del servidor marcadas para borrar: se confirma antes de guardar.
     */
    public int getDeletedCount() {
        return (int) statesByRow.values().stream().filter(TableEditRowStateEnum.STATE_DELETED::equals).count();
    }

    /**
     * Para ApplyTableChangesService: primero los borrados, luego los cambios y al final las altas (asi un alta puede
     * reutilizar una clave unica que se acaba de borrar).
     */
    public List<Map<String, Object>> getChanges() {
        List<Map<String, Object>> deletes = new ArrayList<>();
        List<Map<String, Object>> updates = new ArrayList<>();
        for (Map.Entry<List<String>, String> entry : statesByRow.entrySet()) {
            List<String> row = entry.getKey();
            if (TableEditRowStateEnum.STATE_DELETED.equals(entry.getValue())) {
                deletes.add(getChange(TableChangeKeyEnum.TYPE_DELETE, getKeyValues(row), Map.of(), List.of()));
            }
            else if (TableEditRowStateEnum.STATE_MODIFIED.equals(entry.getValue())) {
                updates.add(getChange(TableChangeKeyEnum.TYPE_UPDATE, getKeyValues(row), getModifiedValues(row), getDefaultColumnNames(row)));
            }
        }
        List<Map<String, Object>> changes = new ArrayList<>(deletes);
        changes.addAll(updates);
        for (List<String> row : newRowsInOrder) {
            changes.add(getChange(TableChangeKeyEnum.TYPE_INSERT, Map.of(), getFilledValues(row), getDefaultColumnNames(row)));
        }
        return changes;
    }

    private Map<String, Object> getChange(
        String changeType,
        Map<String, String> keyValues,
        Map<String, String> newValues,
        List<String> defaultColumnNames
    ) {
        return Primitives.of(
            TableChangeKeyEnum.CHANGE_TYPE, changeType,
            TableChangeKeyEnum.KEY_VALUES, keyValues,
            TableChangeKeyEnum.NEW_VALUES, newValues,
            TableChangeKeyEnum.DEFAULT_COLUMNS, defaultColumnNames
        );
    }

    /**
     * La clave primaria con los valores ORIGINALES: si se edita la propia clave, el WHERE busca la fila de antes.
     */
    private Map<String, String> getKeyValues(List<String> row) {
        List<String> originalValues = originalValuesByRow.get(row);
        Map<String, String> keyValues = new LinkedHashMap<>();
        for (int i = 0; i < columnNames.size(); i++) {
            if (primaryKeyColumns.contains(columnNames.get(i))) {
                keyValues.put(columnNames.get(i), originalValues.get(i));
            }
        }
        return keyValues;
    }

    private List<String> getDefaultColumnNames(List<String> row) {
        List<String> defaultColumnNames = new ArrayList<>();
        for (int i = 0; i < columnNames.size(); i++) {
            if (isDefault(row, i)) defaultColumnNames.add(columnNames.get(i));
        }
        return defaultColumnNames;
    }

    private Map<String, String> getModifiedValues(List<String> row) {
        List<String> originalValues = originalValuesByRow.get(row);
        Map<String, String> modifiedValues = new LinkedHashMap<>();
        for (int i = 0; i < columnNames.size(); i++) {
            if (!isDefault(row, i) && !Objects.equals(originalValues.get(i), row.get(i))) {
                modifiedValues.put(columnNames.get(i), row.get(i));
            }
        }
        return modifiedValues;
    }

    /**
     * En un alta solo van las columnas con valor: NULL se queda fuera y toma su DEFAULT.
     */
    private Map<String, String> getFilledValues(List<String> row) {
        Map<String, String> filledValues = new LinkedHashMap<>();
        for (int i = 0; i < columnNames.size(); i++) {
            if (row.get(i) != null) {
                filledValues.put(columnNames.get(i), row.get(i));
            }
        }
        return filledValues;
    }
}
