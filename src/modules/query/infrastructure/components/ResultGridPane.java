package modules.query.infrastructure.components;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.geometry.Pos;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TablePosition;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import modules.shared.application.Primitives;
import modules.shared.infrastructure.components.icons.AppIconEnum;
import modules.shared.infrastructure.components.icons.AppIcon;

import static core.config.Global.__;

import modules.query.domain.enums.GridFilterOperatorEnum;
import modules.query.domain.enums.ResultExportFormatEnum;
import modules.query.domain.enums.TableEditRowStateEnum;
import modules.query.domain.entities.ResultExportOptionsEntity;
import modules.query.domain.services.ResultExportFormatter;

/**
 * Una rejilla de resultado (feature 42): buscar en todas las columnas a la vez, ocultar/mostrar columnas (menu "+" de
 * la cabecera) y copiar celdas con Ctrl+C. El texto buscado y las columnas ocultas los guarda quien la crea, asi se
 * conservan al volver a ejecutar. Doble clic en una celda: onCellOpened (el visor de la feature 43). Clic en la cabecera
 * ordena; el menu contextual es el de SQLyog (editar, NULL/vacio/DEFAULT, quitar orden, Filtro, Exportar, Copiar).
 */
public final class ResultGridPane extends VBox {

    private static final int MAX_CELL_TEXT = 500;
    private static final String COLUMN_INDEX_KEY = "javayog.columnIndex";
    private static final String DEFAULT_TEXT = "(DEFAULT)";
    private static final Pattern NUMBER_PATTERN = Pattern.compile("-?\\d+(\\.\\d+)?([eE][-+]?\\d+)?");

    private final TextField searchField = new TextField();
    private final Label rowCountLabel = new Label();
    private final TableView<List<String>> resultTableView = new TableView<>();
    private final ObservableList<List<String>> sourceRows;
    private final FilteredList<List<String>> filteredRows;
    private final SortedList<List<String>> sortedRows;
    private final List<String> columnNames;
    private TableEditSession editSession;
    // por que no se edita (sale en el menu en lugar de las opciones de editar); vacio = el aviso general
    private String readOnlyReason = "";
    private Set<Integer> readOnlyColumnIndexes = Set.of();
    private boolean isDefaultSupported;
    private Runnable onEdited = () -> { };
    // guardar y cancelar los cambios los hace quien crea la rejilla (la pestaña Datos); sin ellos, el menu los apaga
    private Runnable onSaveRequested;
    private Runnable onDiscardRequested;
    // filtro del menu contextual: por defecto, sobre las filas cargadas; la pestaña Datos lo pasa a su WHERE
    private ColumnFilterHandler onColumnFilter = this::applyColumnFilter;
    private Runnable onColumnFilterReset = () -> applyColumnFilter(null, null, null);
    private Predicate<List<String>> columnFilterPredicate;
    private String exportTableName = "resultado";
    private Callable<String> createTableSqlLoader;
    // la columna del numero de fila: menu compartido por sus celdas y la fila desde la que se extiende con Mayusculas
    private final ContextMenu rowNumberContextMenu = getRowNumberContextMenu();
    private int rowSelectionAnchor = -1;
    private Consumer<String> onSearchChanged = searchText -> { };
    private Consumer<Set<String>> onHiddenColumnsChanged = hiddenColumnNames -> { };
    // por defecto, el visor de la celda (feature 43)
    private CellOpenedHandler onCellOpened = (columnName, value) -> CellViewerComponent.getInstance().show(
        getScene().getWindow(), columnName, value
    );

    /**
     * El nombre de la columna y el valor (null = NULL) de la celda abierta con doble clic.
     */
    @FunctionalInterface
    public interface CellOpenedHandler {
        void open(String columnName, String value);
    }

    /**
     * Submenu Filtro: la columna, el operador (GridFilterOperatorEnum) y el valor de la celda (null = NULL).
     */
    @FunctionalInterface
    public interface ColumnFilterHandler {
        void filter(String columnName, String operator, String value);
    }

    private ResultGridPane(List<String> columnNames, List<List<String>> rows, String searchText, Set<String> hiddenColumnNames) {
        getStyleClass().add("result-grid");
        this.columnNames = List.copyOf(columnNames);
        sourceRows = FXCollections.observableArrayList(rows);
        filteredRows = new FilteredList<>(sourceRows);
        sortedRows = new SortedList<>(filteredRows);

        searchField.setPromptText(__("Buscar en todas las columnas"));
        searchField.setText(searchText);
        HBox.setHgrow(searchField, Priority.ALWAYS);
        HBox searchBar = new HBox(8, searchField, rowCountLabel);
        searchBar.setAlignment(Pos.CENTER_LEFT);
        searchBar.getStyleClass().add("result-grid-bar");

        buildColumns(columnNames, hiddenColumnNames);
        resultTableView.setItems(sortedRows);
        // clic en la cabecera: ascendente, descendente y sin ordenar (como SQLyog)
        sortedRows.comparatorProperty().bind(resultTableView.comparatorProperty());
        resultTableView.setTableMenuButtonVisible(true);
        resultTableView.getSelectionModel().setCellSelectionEnabled(true);
        resultTableView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        resultTableView.setOnKeyPressed(keyEvent -> {
            if (keyEvent.isShortcutDown() && keyEvent.isShiftDown() && keyEvent.getCode() == KeyCode.E) {
                exportRows(null);
                keyEvent.consume();
            }
            else if (keyEvent.isShortcutDown() && keyEvent.getCode() == KeyCode.C) {
                copySelectedCells();
                keyEvent.consume();
            }
        });
        resultTableView.setContextMenu(getGridContextMenu());
        VBox.setVgrow(resultTableView, Priority.ALWAYS);
        getChildren().addAll(searchBar, resultTableView);

        searchField.textProperty().addListener((observable, oldText, newText) -> {
            applySearch(newText);
            onSearchChanged.accept(newText);
        });
        applySearch(searchText);
    }

    /**
     * Una rejilla nueva por resultado. searchText NO se recorta: es lo que el usuario escribio en el buscador.
     */
    public static ResultGridPane getInstance(List<String> columnNames, List<List<String>> rows, String searchText, Set<String> hiddenColumnNames) {
        return new ResultGridPane(columnNames, rows, searchText, hiddenColumnNames);
    }

    public void setOnSearchChanged(Consumer<String> onSearchChanged) {
        this.onSearchChanged = onSearchChanged;
    }

    public void setOnHiddenColumnsChanged(Consumer<Set<String>> onHiddenColumnsChanged) {
        this.onHiddenColumnsChanged = onHiddenColumnsChanged;
    }

    public void setOnCellOpened(CellOpenedHandler onCellOpened) {
        this.onCellOpened = onCellOpened;
    }

    public void setOnSaveRequested(Runnable onSaveRequested) {
        this.onSaveRequested = onSaveRequested;
    }

    public void setOnDiscardRequested(Runnable onDiscardRequested) {
        this.onDiscardRequested = onDiscardRequested;
    }

    /**
     * El motivo por el que esta rejilla no se edita ("Solo lectura: ..."), para el menu contextual.
     */
    public void setReadOnlyReason(String readOnlyReason) {
        this.readOnlyReason = readOnlyReason == null ? "" : readOnlyReason;
    }

    /**
     * Para filtrar en el servidor (la pestaña Datos): el submenu Filtro llama a onColumnFilter y "Quitar filtro" a
     * onColumnFilterReset en vez de filtrar las filas cargadas.
     */
    public void setOnColumnFilter(ColumnFilterHandler onColumnFilter, Runnable onColumnFilterReset) {
        this.onColumnFilter = onColumnFilter;
        this.onColumnFilterReset = onColumnFilterReset;
    }

    /**
     * La tabla de los INSERT al exportar como SQL.
     */
    public void setExportTableName(String exportTableName) {
        this.exportTableName = exportTableName;
    }

    /**
     * Si la rejilla es de una tabla: lee su CREATE TABLE para "Incluir CREATE TABLE" al exportar como SQL (se llama en
     * segundo plano, desde el dialogo).
     */
    public void setCreateTableSqlLoader(Callable<String> createTableSqlLoader) {
        this.createTableSqlLoader = createTableSqlLoader;
    }

    /**
     * "Poner DEFAULT" solo donde el motor entiende DEFAULT en INSERT y UPDATE (MySQL y PostgreSQL, no SQLite).
     */
    public void setDefaultSupported(boolean isDefaultSupported) {
        this.isDefaultSupported = isDefaultSupported;
    }

    public TableView<List<String>> getTableView() {
        return resultTableView;
    }

    public int getVisibleRowCount() {
        return filteredRows.size();
    }

    /**
     * Feature 6: la rejilla pasa a editable. Las filas que lleguen tienen que ser listas mutables (se editan en sitio).
     * Doble clic, F2 o Enter editan; el menu contextual pone NULL, añade, duplica o borra filas y abre el visor.
     * readOnlyColumnIndexes: columnas que no se editan (BLOB, BIT, binarias).
     */
    public void enableEditing(TableEditSession tableEditSession, Set<Integer> readOnlyColumnIndexes, Runnable onEdited) {
        this.editSession = tableEditSession;
        this.readOnlyColumnIndexes = readOnlyColumnIndexes;
        this.onEdited = onEdited;
        tableEditSession.trackLoadedRows(sourceRows);
        resultTableView.setEditable(true);
        for (TableColumn<List<String>, ?> tableColumn : resultTableView.getColumns()) {
            int columnIndex = getDataIndex(tableColumn);
            tableColumn.setEditable(columnIndex >= 0 && !readOnlyColumnIndexes.contains(columnIndex));
        }
        resultTableView.setRowFactory(tableView -> new EditableRow());
        resultTableView.addEventFilter(KeyEvent.KEY_PRESSED, keyEvent -> {
            if (keyEvent.getCode() == KeyCode.F2 || (keyEvent.getCode() == KeyCode.ENTER && resultTableView.getEditingCell() == null)) {
                editFocusedCell();
                keyEvent.consume();
            }
            else if (keyEvent.getCode() == KeyCode.DELETE && resultTableView.getEditingCell() == null) {
                deleteSelectedRows();
                keyEvent.consume();
            }
        });
    }

    public void addNewRow() {
        if (editSession == null) return;
        List<String> row = editSession.newRow();
        searchField.clear();
        sourceRows.add(row);
        resultTableView.scrollTo(row);
        resultTableView.getSelectionModel().clearAndSelect(resultTableView.getItems().indexOf(row), getFirstDataColumn());
        afterEdit();
    }

    public void duplicateSelectedRow() {
        List<String> selectedRow = getFocusedRow();
        if (editSession == null || selectedRow == null) return;
        List<String> row = editSession.duplicateRow(selectedRow);
        sourceRows.add(sourceRows.indexOf(selectedRow) + 1, row);
        resultTableView.getSelectionModel().clearAndSelect(resultTableView.getItems().indexOf(row), getFirstDataColumn());
        afterEdit();
    }

    /**
     * Las filas nuevas desaparecen; las que vienen del servidor quedan tachadas hasta guardar (Supr vuelve a marcarlas).
     */
    public void deleteSelectedRows() {
        if (editSession == null) return;
        Set<List<String>> selectedRows = new LinkedHashSet<>();
        for (TablePosition<?, ?> tablePosition : resultTableView.getSelectionModel().getSelectedCells()) {
            selectedRows.add(resultTableView.getItems().get(tablePosition.getRow()));
        }
        for (List<String> row : selectedRows) {
            if (TableEditRowStateEnum.STATE_DELETED.equals(editSession.getState(row))) {
                editSession.undeleteRow(row);
                continue;
            }
            if (editSession.deleteRow(row)) sourceRows.remove(row);
        }
        afterEdit();
    }

    public void setSelectedCellsNull() {
        setSelectedCellsValue(null);
    }

    public void setSelectedCellsEmpty() {
        setSelectedCellsValue("");
    }

    public void setSelectedCellsDefault() {
        if (editSession == null || !isDefaultSupported) return;
        for (TablePosition<?, ?> tablePosition : resultTableView.getSelectionModel().getSelectedCells()) {
            int columnIndex = getDataIndex(tablePosition.getTableColumn());
            if (columnIndex < 0 || readOnlyColumnIndexes.contains(columnIndex)) continue;
            editSession.setDefault(resultTableView.getItems().get(tablePosition.getRow()), columnIndex);
        }
        afterEdit();
    }

    private void setSelectedCellsValue(String value) {
        if (editSession == null) return;
        for (TablePosition<?, ?> tablePosition : resultTableView.getSelectionModel().getSelectedCells()) {
            int columnIndex = getDataIndex(tablePosition.getTableColumn());
            if (columnIndex < 0 || readOnlyColumnIndexes.contains(columnIndex)) continue;
            editSession.setValue(resultTableView.getItems().get(tablePosition.getRow()), columnIndex, value);
        }
        afterEdit();
    }

    /**
     * La posicion del valor en la fila. No vale TablePosition.getColumn(): cuenta solo las columnas visibles y se
     * descuadra al ocultar alguna.
     */
    private int getDataIndex(TableColumn<?, ?> tableColumn) {
        if (tableColumn == null) return -1;
        Object columnIndex = tableColumn.getProperties().get(COLUMN_INDEX_KEY);
        return columnIndex instanceof Integer index ? index : -1;
    }

    private void afterEdit() {
        resultTableView.refresh();
        applySearch(searchField.getText());
        onEdited.run();
    }

    private void editFocusedCell() {
        TablePosition<List<String>, ?> focusedCell = resultTableView.getFocusModel().getFocusedCell();
        if (focusedCell == null || focusedCell.getTableColumn() == null || focusedCell.getRow() < 0) return;
        resultTableView.edit(focusedCell.getRow(), focusedCell.getTableColumn());
    }

    private List<String> getFocusedRow() {
        int focusedIndex = resultTableView.getFocusModel().getFocusedIndex();
        return focusedIndex < 0 ? null : resultTableView.getItems().get(focusedIndex);
    }

    /**
     * La celda con el foco: su columna y su valor, o null si no hay ninguna.
     */
    private FocusedCell getFocusedCell() {
        TablePosition<List<String>, ?> focusedCell = resultTableView.getFocusModel().getFocusedCell();
        if (focusedCell == null || focusedCell.getRow() < 0 || focusedCell.getRow() >= resultTableView.getItems().size()) return null;
        int columnIndex = getDataIndex(focusedCell.getTableColumn());
        if (columnIndex < 0) return null;
        List<String> row = resultTableView.getItems().get(focusedCell.getRow());
        return new FocusedCell(columnNames.get(columnIndex), columnIndex < row.size() ? row.get(columnIndex) : null);
    }

    private record FocusedCell(String columnName, String value) {
    }

    /**
     * El menu de SQLyog: editar (si la rejilla es editable), quitar el orden, filtrar por el valor de la celda,
     * exportar, copiar y duplicar. Lo que no se puede hacer se ve apagado.
     */
    private ContextMenu getGridContextMenu() {
        MenuItem viewMenuItem = new MenuItem(__("Ver valor"), AppIcon.of(AppIconEnum.SEARCH));
        viewMenuItem.setOnAction(actionEvent -> {
            FocusedCell focusedCell = getFocusedCell();
            if (focusedCell != null) onCellOpened.open(focusedCell.columnName(), focusedCell.value());
        });
        MenuItem addMenuItem = new MenuItem(__("Insertar fila nueva"), AppIcon.of(AppIconEnum.ADD_ROW));
        addMenuItem.setOnAction(actionEvent -> addNewRow());
        MenuItem saveMenuItem = new MenuItem(__("Guardar cambios (Ctrl+S)"), AppIcon.of(AppIconEnum.SAVE));
        saveMenuItem.setOnAction(actionEvent -> {
            if (onSaveRequested != null) onSaveRequested.run();
        });
        MenuItem deleteMenuItem = new MenuItem(__("Borrar / recuperar fila(s) seleccionada(s) (Supr)"), AppIcon.of(AppIconEnum.DELETE));
        deleteMenuItem.setOnAction(actionEvent -> deleteSelectedRows());
        MenuItem discardMenuItem = new MenuItem(__("Cancelar cambios"), AppIcon.of(AppIconEnum.DISCARD));
        discardMenuItem.setOnAction(actionEvent -> {
            if (onDiscardRequested != null) onDiscardRequested.run();
        });

        MenuItem emptyMenuItem = new MenuItem(__("Poner cadena vacía"));
        emptyMenuItem.setOnAction(actionEvent -> setSelectedCellsEmpty());
        MenuItem nullMenuItem = new MenuItem(__("Poner NULL"), AppIcon.of(AppIconEnum.SET_NULL));
        nullMenuItem.setOnAction(actionEvent -> setSelectedCellsNull());
        MenuItem defaultMenuItem = new MenuItem(__("Poner DEFAULT"));
        defaultMenuItem.setOnAction(actionEvent -> setSelectedCellsDefault());

        MenuItem unsortMenuItem = new MenuItem(__("Quitar orden"));
        unsortMenuItem.setOnAction(actionEvent -> resultTableView.getSortOrder().clear());

        Menu filterMenu = new Menu(__("Filtro"));

        MenuItem exportMenuItem = new MenuItem(__("Exportar las filas como... (Ctrl+Shift+E)"), AppIcon.of(AppIconEnum.SAVE));
        exportMenuItem.setOnAction(actionEvent -> exportRows(null));

        MenuItem copyCellsMenuItem = new MenuItem(__("Celdas seleccionadas (Ctrl+C)"));
        copyCellsMenuItem.setOnAction(actionEvent -> copySelectedCells());
        MenuItem copyRowsMenuItem = new MenuItem(__("Filas seleccionadas"));
        copyRowsMenuItem.setOnAction(actionEvent -> copySelectedRows());
        MenuItem copyAllMenuItem = new MenuItem(__("Todas las filas con tabuladores y cabecera"));
        copyAllMenuItem.setOnAction(actionEvent -> copyAllRows());
        MenuItem copyColumnNameMenuItem = new MenuItem(__("Nombre de la columna"));
        copyColumnNameMenuItem.setOnAction(actionEvent -> {
            FocusedCell focusedCell = getFocusedCell();
            if (focusedCell != null) putInClipboard(focusedCell.columnName());
        });
        Menu copyMenu = new Menu(__("Copiar"), AppIcon.of(AppIconEnum.COPY), copyCellsMenuItem, copyRowsMenuItem, copyAllMenuItem, copyColumnNameMenuItem);

        MenuItem duplicateMenuItem = new MenuItem(__("Duplicar fila actual"), AppIcon.of(AppIconEnum.COPY));
        duplicateMenuItem.setOnAction(actionEvent -> duplicateSelectedRow());

        MenuItem readOnlyMenuItem = getReadOnlyMenuItem();
        SeparatorMenuItem editSeparator = new SeparatorMenuItem();
        SeparatorMenuItem cellSeparator = new SeparatorMenuItem();
        ContextMenu contextMenu = new ContextMenu(
            viewMenuItem, new SeparatorMenuItem(),
            readOnlyMenuItem, addMenuItem, saveMenuItem, deleteMenuItem, discardMenuItem, editSeparator,
            emptyMenuItem, nullMenuItem, defaultMenuItem, cellSeparator,
            unsortMenuItem, new SeparatorMenuItem(),
            filterMenu, new SeparatorMenuItem(),
            exportMenuItem, copyMenu, duplicateMenuItem
        );
        contextMenu.setOnShowing(windowEvent -> {
            boolean isEditable = editSession != null;
            boolean hasChanges = isEditable && editSession.hasChanges();
            boolean hasCell = getFocusedCell() != null;
            viewMenuItem.setDisable(!hasCell);
            addMenuItem.setDisable(!isEditable);
            saveMenuItem.setDisable(!hasChanges || onSaveRequested == null);
            deleteMenuItem.setDisable(!isEditable || resultTableView.getSelectionModel().isEmpty());
            discardMenuItem.setDisable(!hasChanges || onDiscardRequested == null);
            emptyMenuItem.setDisable(!isEditable || !hasCell);
            nullMenuItem.setDisable(!isEditable || !hasCell);
            defaultMenuItem.setDisable(!isEditable || !hasCell || !isDefaultSupported);
            unsortMenuItem.setDisable(resultTableView.getSortOrder().isEmpty());
            filterMenu.getItems().setAll(getFilterMenuItems());
            copyRowsMenuItem.setDisable(resultTableView.getSelectionModel().isEmpty());
            copyCellsMenuItem.setDisable(resultTableView.getSelectionModel().isEmpty());
            copyColumnNameMenuItem.setDisable(!hasCell);
            duplicateMenuItem.setDisable(!isEditable || getFocusedRow() == null);
            // si no se edita: en vez de opciones bloqueadas, se dice por que (o donde se edita)
            readOnlyMenuItem.setText(getReadOnlyText());
            readOnlyMenuItem.setVisible(!isEditable);
            for (MenuItem editMenuItem : List.of(addMenuItem, saveMenuItem, deleteMenuItem, discardMenuItem, emptyMenuItem, nullMenuItem, defaultMenuItem, cellSeparator, duplicateMenuItem)) {
                editMenuItem.setVisible(isEditable);
            }
        });
        return contextMenu;
    }

    /**
     * `columna` = valor, <> valor, LIKE %valor%... con el valor de la celda con el foco; y quitar el filtro.
     */
    private List<MenuItem> getFilterMenuItems() {
        List<MenuItem> filterMenuItems = new ArrayList<>();
        FocusedCell focusedCell = getFocusedCell();
        if (focusedCell != null) {
            String shownValue = getShortValue(focusedCell.value());
            for (String operator : GridFilterOperatorEnum.VALUES) {
                MenuItem filterMenuItem = new MenuItem(focusedCell.columnName() + " " + operator + " " + shownValue);
                filterMenuItem.setMnemonicParsing(false);
                filterMenuItem.setOnAction(actionEvent -> onColumnFilter.filter(focusedCell.columnName(), operator, focusedCell.value()));
                filterMenuItems.add(filterMenuItem);
            }
            filterMenuItems.add(new SeparatorMenuItem());
        }
        MenuItem resetMenuItem = new MenuItem(__("Quitar filtro"));
        resetMenuItem.setOnAction(actionEvent -> onColumnFilterReset.run());
        filterMenuItems.add(resetMenuItem);
        return filterMenuItems;
    }

    private String getShortValue(String value) {
        if (value == null) return "NULL";
        String oneLine = value.replace('\n', ' ').replace('\r', ' ');
        return "'" + (oneLine.length() > 30 ? oneLine.substring(0, 30) + "..." : oneLine) + "'";
    }

    /**
     * El filtro por defecto, sobre las filas cargadas (columnName null = quitarlo). Compara como el orden de la
     * columna: los numeros por su valor; LIKE busca el texto en cualquier parte sin distinguir mayusculas.
     */
    private void applyColumnFilter(String columnName, String operator, String value) {
        int columnIndex = columnName == null ? -1 : columnNames.indexOf(columnName);
        columnFilterPredicate = columnIndex < 0 ? null : row -> isColumnMatch(columnIndex < row.size() ? row.get(columnIndex) : null, operator, value);
        applySearch(searchField.getText());
    }

    private boolean isColumnMatch(String cellValue, String operator, String value) {
        if (value == null) {
            boolean isNullMatch = GridFilterOperatorEnum.EQUAL.equals(operator) || GridFilterOperatorEnum.LIKE.equals(operator);
            return isNullMatch == (cellValue == null);
        }
        if (cellValue == null) return false;
        boolean isContained = cellValue.toLowerCase(Locale.ROOT).contains(value.toLowerCase(Locale.ROOT));
        return switch (operator) {
            case GridFilterOperatorEnum.EQUAL -> compareCellValues(cellValue, value) == 0;
            case GridFilterOperatorEnum.NOT_EQUAL -> compareCellValues(cellValue, value) != 0;
            case GridFilterOperatorEnum.LIKE -> isContained;
            case GridFilterOperatorEnum.NOT_LIKE -> !isContained;
            case GridFilterOperatorEnum.GREATER -> compareCellValues(cellValue, value) > 0;
            case GridFilterOperatorEnum.LESS -> compareCellValues(cellValue, value) < 0;
            default -> true;
        };
    }

    /**
     * El dialogo "Exportar como" con las filas tal como se ven (buscadas, filtradas y ordenadas), las marcadas en la
     * columna 0 y solo las columnas visibles. preselectedFormat: el del menu de la esquina (null = el ultimo usado).
     */
    private void exportRows(String preselectedFormat) {
        List<Integer> columnIndexes = getVisibleDataIndexes();
        List<String> exportColumnNames = new ArrayList<>();
        for (int columnIndex : columnIndexes) {
            exportColumnNames.add(columnNames.get(columnIndex));
        }
        List<List<String>> exportRows = new ArrayList<>();
        List<List<String>> markedRows = new ArrayList<>();
        List<List<String>> items = resultTableView.getItems();
        for (int rowIndex = 0; rowIndex < items.size(); rowIndex++) {
            List<String> values = getValues(items.get(rowIndex), columnIndexes);
            exportRows.add(values);
            if (isRowSelected(rowIndex)) markedRows.add(values);
        }
        ResultExportComponent.getInstance().openDialog(getScene().getWindow(), preselectedFormat, exportTableName, exportColumnNames, exportRows, markedRows, createTableSqlLoader);
    }

    private List<Integer> getVisibleDataIndexes() {
        List<Integer> columnIndexes = new ArrayList<>();
        for (TableColumn<List<String>, ?> tableColumn : resultTableView.getVisibleLeafColumns()) {
            int columnIndex = getDataIndex(tableColumn);
            if (columnIndex >= 0) columnIndexes.add(columnIndex);
        }
        return columnIndexes;
    }

    private List<String> getValues(List<String> row, List<Integer> columnIndexes) {
        List<String> values = new ArrayList<>();
        for (int columnIndex : columnIndexes) {
            values.add(columnIndex < row.size() ? row.get(columnIndex) : null);
        }
        return values;
    }

    /**
     * Sin distinguir mayusculas, en cualquier columna; "null" tambien encuentra los NULL. Se suma al filtro por
     * columna del menu, si lo hay.
     */
    private void applySearch(String searchText) {
        String needle = searchText == null ? "" : searchText.trim().toLowerCase(Locale.ROOT);
        Predicate<List<String>> searchPredicate = needle.isEmpty() ? null : row -> isRowContaining(row, needle);
        Predicate<List<String>> predicate = searchPredicate;
        if (columnFilterPredicate != null) predicate = searchPredicate == null ? columnFilterPredicate : searchPredicate.and(columnFilterPredicate);
        filteredRows.setPredicate(predicate);
        boolean isFiltered = predicate != null;
        rowCountLabel.setText(isFiltered ? __("{0} de {1} fila(s)", filteredRows.size(), sourceRows.size()) : __("{0} fila(s)", sourceRows.size()));
    }

    private boolean isRowContaining(List<String> row, String needle) {
        for (String value : row) {
            String text = value == null ? "null" : value.toLowerCase(Locale.ROOT);
            if (text.contains(needle)) return true;
        }
        return false;
    }

    private void buildColumns(List<String> columnNames, Set<String> hiddenColumnNames) {
        resultTableView.getColumns().add(getRowNumberColumn());
        for (int i = 0; i < columnNames.size(); i++) {
            int columnIndex = i;
            String columnName = columnNames.get(i);
            // con texto (no un graphic): el menu de ocultar/mostrar columnas lista los textos de las columnas
            TableColumn<List<String>, String> tableColumn = new TableColumn<>(columnName);
            tableColumn.setUserData(columnName);
            tableColumn.getProperties().put(COLUMN_INDEX_KEY, columnIndex);
            tableColumn.setComparator(this::compareCellValues);
            tableColumn.setPrefWidth(Math.min(300, Math.max(80, columnName.length() * 9 + 24)));
            tableColumn.setCellValueFactory(cellData -> new SimpleStringProperty(
                columnIndex < cellData.getValue().size() ? cellData.getValue().get(columnIndex) : null
            ));
            tableColumn.setCellFactory(column -> new ResultCell());
            tableColumn.setOnEditCommit(editEvent -> {
                if (editSession == null) return;
                editSession.setValue(editEvent.getRowValue(), columnIndex, editEvent.getNewValue());
                afterEdit();
            });
            tableColumn.setVisible(!hiddenColumnNames.contains(columnName));
            tableColumn.visibleProperty().addListener((observable, wasVisible, isVisible) -> onHiddenColumnsChanged.accept(getHiddenColumnNames()));
            resultTableView.getColumns().add(tableColumn);
        }
    }

    /**
     * La columna del numero de fila, a la izquierda: solo lectura, sin ordenar ni mover, y hace de boton de la fila
     * (clic la selecciona entera; clic derecho, su menu). No esta en COLUMN_INDEX_KEY: no es un dato.
     */
    private TableColumn<List<String>, String> getRowNumberColumn() {
        TableColumn<List<String>, String> rowNumberColumn = new TableColumn<>();
        // la esquina (fila y columna), como en SQLyog una casilla: marca o desmarca todo lo que se ve (a medias si solo
        // hay algunas filas); clic derecho, exportar en un formato
        CheckBox cornerCheckBox = new CheckBox();
        cornerCheckBox.setMouseTransparent(true);
        cornerCheckBox.setFocusTraversable(false);
        Label cornerLabel = new Label("#", cornerCheckBox);
        cornerLabel.setMaxWidth(Double.MAX_VALUE);
        cornerLabel.setPrefWidth(60);
        cornerLabel.getStyleClass().add("row-number-corner");
        cornerLabel.setOnMouseClicked(mouseEvent -> {
            if (mouseEvent.getButton() != MouseButton.PRIMARY) return;
            if (cornerCheckBox.isSelected()) {
                resultTableView.getSelectionModel().clearSelection();
                return;
            }
            selectAllRows();
        });
        resultTableView.getSelectionModel().getSelectedCells().addListener((ListChangeListener<TablePosition>) change -> {
            int rowCount = resultTableView.getItems().size();
            long selectedRows = resultTableView.getSelectionModel().getSelectedCells().stream().mapToInt(TablePosition::getRow).distinct().count();
            cornerCheckBox.setIndeterminate(selectedRows > 0 && selectedRows < rowCount);
            cornerCheckBox.setSelected(rowCount > 0 && selectedRows == rowCount);
        });
        rowNumberColumn.setGraphic(cornerLabel);
        rowNumberColumn.setContextMenu(getCornerContextMenu());
        rowNumberColumn.setSortable(false);
        rowNumberColumn.setReorderable(false);
        rowNumberColumn.setEditable(false);
        rowNumberColumn.setResizable(false);
        rowNumberColumn.setPrefWidth(70);
        rowNumberColumn.getStyleClass().add("row-number-column");
        rowNumberColumn.setCellFactory(column -> new RowNumberCell());
        return rowNumberColumn;
    }

    private void selectAllRows() {
        TableColumn<List<String>, ?> firstColumn = getFirstDataColumn();
        if (firstColumn == null || resultTableView.getItems().isEmpty()) return;
        resultTableView.getSelectionModel().clearSelection();
        resultTableView.getSelectionModel().selectRange(0, firstColumn, resultTableView.getItems().size() - 1, getLastDataColumn());
        rowSelectionAnchor = 0;
        resultTableView.requestFocus();
    }

    /**
     * Menu de la esquina: todas las filas que se ven, en el formato elegido.
     */
    private ContextMenu getCornerContextMenu() {
        MenuItem selectAllMenuItem = new MenuItem(__("Seleccionar todo"));
        selectAllMenuItem.setOnAction(actionEvent -> selectAllRows());
        MenuItem csvMenuItem = new MenuItem(__("Exportar como CSV..."));
        csvMenuItem.setOnAction(actionEvent -> exportRows(ResultExportFormatEnum.CSV));
        MenuItem sqlMenuItem = new MenuItem(__("Exportar como INSERTs (SQL)..."));
        sqlMenuItem.setOnAction(actionEvent -> exportRows(ResultExportFormatEnum.SQL));
        MenuItem jsonMenuItem = new MenuItem(__("Exportar como JSON..."));
        jsonMenuItem.setOnAction(actionEvent -> exportRows(ResultExportFormatEnum.JSON));
        MenuItem xmlMenuItem = new MenuItem(__("Exportar como XML..."));
        xmlMenuItem.setOnAction(actionEvent -> exportRows(ResultExportFormatEnum.XML));
        MenuItem htmlMenuItem = new MenuItem(__("Exportar como HTML..."));
        htmlMenuItem.setOnAction(actionEvent -> exportRows(ResultExportFormatEnum.HTML));
        MenuItem excelMenuItem = new MenuItem(__("Exportar como Excel XML..."));
        excelMenuItem.setOnAction(actionEvent -> exportRows(ResultExportFormatEnum.EXCEL_XML));
        MenuItem copyAllMenuItem = new MenuItem(__("Copiar todo con tabuladores y cabecera"));
        copyAllMenuItem.setOnAction(actionEvent -> copyAllRows());
        return new ContextMenu(
            selectAllMenuItem, new SeparatorMenuItem(),
            csvMenuItem, sqlMenuItem, jsonMenuItem, xmlMenuItem, htmlMenuItem, excelMenuItem, new SeparatorMenuItem(),
            copyAllMenuItem
        );
    }

    private TableColumn<List<String>, ?> getFirstDataColumn() {
        for (TableColumn<List<String>, ?> tableColumn : resultTableView.getVisibleLeafColumns()) {
            if (getDataIndex(tableColumn) >= 0) return tableColumn;
        }
        return null;
    }

    private TableColumn<List<String>, ?> getLastDataColumn() {
        List<TableColumn<List<String>, ?>> visibleColumns = resultTableView.getVisibleLeafColumns();
        for (int i = visibleColumns.size() - 1; i >= 0; i--) {
            if (getDataIndex(visibleColumns.get(i)) >= 0) return visibleColumns.get(i);
        }
        return null;
    }

    /**
     * Clic en el numero de fila: la fila entera (todas sus celdas de datos visibles). Mayusculas extiende desde la
     * ultima fila elegida asi; Ctrl la añade o la quita.
     */
    private void selectRows(int rowIndex, boolean isRangeExtended, boolean isToggled) {
        TableColumn<List<String>, ?> firstColumn = getFirstDataColumn();
        TableColumn<List<String>, ?> lastColumn = getLastDataColumn();
        if (firstColumn == null) return;
        var selectionModel = resultTableView.getSelectionModel();
        if (isToggled && selectionModel.isSelected(rowIndex, firstColumn)) {
            for (TableColumn<List<String>, ?> tableColumn : resultTableView.getVisibleLeafColumns()) {
                selectionModel.clearSelection(rowIndex, tableColumn);
            }
            return;
        }
        int fromRow = isRangeExtended && rowSelectionAnchor >= 0 ? Math.min(rowSelectionAnchor, rowIndex) : rowIndex;
        int toRow = isRangeExtended && rowSelectionAnchor >= 0 ? Math.max(rowSelectionAnchor, rowIndex) : rowIndex;
        if (!isToggled) selectionModel.clearSelection();
        selectionModel.selectRange(fromRow, firstColumn, toRow, lastColumn);
        resultTableView.getFocusModel().focus(rowIndex, firstColumn);
        if (!isRangeExtended) rowSelectionAnchor = rowIndex;
        resultTableView.requestFocus();
    }

    private boolean isRowSelected(int rowIndex) {
        // lo normal (fila elegida por su casilla): su primera celda de datos, sin recorrer toda la seleccion
        TableColumn<List<String>, ?> firstColumn = getFirstDataColumn();
        if (firstColumn != null && resultTableView.getSelectionModel().isSelected(rowIndex, firstColumn)) return true;
        for (TablePosition<?, ?> tablePosition : resultTableView.getSelectionModel().getSelectedCells()) {
            if (tablePosition.getRow() == rowIndex) return true;
        }
        return false;
    }

    /**
     * En una rejilla de solo lectura, en lugar de borrar, duplicar o editar: por que no se edita y, si no hay motivo
     * concreto (p. ej. un JOIN), donde se puede editar.
     */
    private MenuItem getReadOnlyMenuItem() {
        MenuItem readOnlyMenuItem = new MenuItem(getReadOnlyText());
        readOnlyMenuItem.setDisable(true);
        return readOnlyMenuItem;
    }

    private String getReadOnlyText() {
        return readOnlyReason.isEmpty() ? __("Solo lectura: para editar, Ver datos de la tabla (F11)") : readOnlyReason;
    }

    /**
     * Menu del numero de fila: lo que se hace con filas enteras.
     */
    private ContextMenu getRowNumberContextMenu() {
        MenuItem deleteMenuItem = new MenuItem(__("Borrar / recuperar fila(s) (Supr)"), AppIcon.of(AppIconEnum.DELETE));
        deleteMenuItem.setOnAction(actionEvent -> deleteSelectedRows());
        MenuItem duplicateMenuItem = new MenuItem(__("Duplicar fila"), AppIcon.of(AppIconEnum.COPY));
        duplicateMenuItem.setOnAction(actionEvent -> duplicateSelectedRow());
        MenuItem copyMenuItem = new MenuItem(__("Copiar fila(s)"));
        copyMenuItem.setOnAction(actionEvent -> copySelectedRows());
        MenuItem readOnlyMenuItem = getReadOnlyMenuItem();
        SeparatorMenuItem editSeparator = new SeparatorMenuItem();
        ContextMenu contextMenu = new ContextMenu(readOnlyMenuItem, deleteMenuItem, duplicateMenuItem, editSeparator, copyMenuItem);
        contextMenu.setOnShowing(windowEvent -> {
            boolean isEditable = editSession != null;
            readOnlyMenuItem.setText(getReadOnlyText());
            readOnlyMenuItem.setVisible(!isEditable);
            deleteMenuItem.setVisible(isEditable);
            duplicateMenuItem.setVisible(isEditable);
        });
        return contextMenu;
    }

    /**
     * Orden de una columna: NULL primero, dos numeros por su valor ("9" antes que "10") y el resto como texto sin
     * distinguir mayusculas (las fechas de MySQL ya ordenan bien como texto).
     */
    private int compareCellValues(String leftValue, String rightValue) {
        if (leftValue == null || rightValue == null) {
            return leftValue == null ? (rightValue == null ? 0 : -1) : 1;
        }
        if (NUMBER_PATTERN.matcher(leftValue).matches() && NUMBER_PATTERN.matcher(rightValue).matches()) {
            return new BigDecimal(leftValue).compareTo(new BigDecimal(rightValue));
        }
        return String.CASE_INSENSITIVE_ORDER.compare(leftValue, rightValue);
    }

    public Set<String> getHiddenColumnNames() {
        Set<String> hiddenColumnNames = new LinkedHashSet<>();
        for (TableColumn<List<String>, ?> tableColumn : resultTableView.getColumns()) {
            if (!tableColumn.isVisible() && getDataIndex(tableColumn) >= 0) hiddenColumnNames.add(String.valueOf(tableColumn.getUserData()));
        }
        return hiddenColumnNames;
    }

    private void copySelectedCells() {
        if (resultTableView.getSelectionModel().getSelectedCells().isEmpty()) return;

        StringBuilder copiedText = new StringBuilder();
        int previousRow = -1;
        for (TablePosition<?, ?> tablePosition : resultTableView.getSelectionModel().getSelectedCells()) {
            int columnIndex = getDataIndex(tablePosition.getTableColumn());
            if (columnIndex < 0) continue;
            if (previousRow != -1) {
                copiedText.append(tablePosition.getRow() == previousRow ? '\t' : '\n');
            }
            List<String> row = resultTableView.getItems().get(tablePosition.getRow());
            String value = columnIndex < row.size() ? row.get(columnIndex) : null;
            copiedText.append(value == null ? "" : value);
            previousRow = tablePosition.getRow();
        }
        putInClipboard(copiedText.toString());
    }

    /**
     * Las filas enteras (columnas visibles) de las celdas seleccionadas, separadas por tabuladores.
     */
    private void copySelectedRows() {
        Set<Integer> rowIndexes = new TreeSet<>();
        for (TablePosition<?, ?> tablePosition : resultTableView.getSelectionModel().getSelectedCells()) {
            rowIndexes.add(tablePosition.getRow());
        }
        List<Integer> columnIndexes = getVisibleDataIndexes();
        StringBuilder copiedText = new StringBuilder();
        for (int rowIndex : rowIndexes) {
            copiedText.append(getTsvLine(getValues(resultTableView.getItems().get(rowIndex), columnIndexes))).append('\n');
        }
        putInClipboard(copiedText.toString());
    }

    /**
     * Todas las filas que se ven, con los nombres de columna en la primera linea, separadas por tabuladores (TSV): la
     * coma choca con los datos y el tabulador casi nunca, y Excel u hojas de calculo lo pegan ya en columnas. Comillas
     * dobles solo si hacen falta (el valor lleva un tabulador, comilla o salto); NULL va vacio.
     */
    private void copyAllRows() {
        List<Integer> columnIndexes = getVisibleDataIndexes();
        List<String> headerNames = new ArrayList<>();
        for (int columnIndex : columnIndexes) {
            headerNames.add(columnNames.get(columnIndex));
        }
        List<List<String>> rows = new ArrayList<>();
        for (List<String> row : resultTableView.getItems()) {
            rows.add(getValues(row, columnIndexes));
        }
        ResultExportOptionsEntity csvOptions = ResultExportOptionsEntity.fromPrimitives(Primitives.of(
            "fieldsTerminatedBy", "\\t",
            "nullReplacedBy", ResultExportOptionsEntity.NONE,
            "isHeaderIncluded", true
        ));
        putInClipboard(ResultExportFormatter.getInstance().getFormatted(ResultExportFormatEnum.CSV, headerNames, rows, csvOptions));
    }

    private String getTsvLine(List<String> values) {
        List<String> cells = new ArrayList<>();
        for (String value : values) {
            cells.add(value == null ? "" : value);
        }
        return String.join("\t", cells);
    }

    private void putInClipboard(String text) {
        ClipboardContent clipboardContent = new ClipboardContent();
        clipboardContent.putString(text);
        Clipboard.getSystemClipboard().setContent(clipboardContent);
    }

    /**
     * El numero de la fila tal como se ve (1, 2, 3... tras buscar y ordenar); "*" en una fila nueva sin guardar.
     */
    private final class RowNumberCell extends TableCell<List<String>, String> {

        private final CheckBox rowCheckBox = new CheckBox();

        private RowNumberCell() {
            getStyleClass().add("row-number-cell");
            setContextMenu(rowNumberContextMenu);
            // la casilla solo pinta: el clic lo trata la celda entera
            rowCheckBox.setMouseTransparent(true);
            rowCheckBox.setFocusTraversable(false);
            resultTableView.getSelectionModel().getSelectedCells().addListener(
                (ListChangeListener<TablePosition>) change -> refreshCheckBox()
            );
            addEventFilter(MouseEvent.MOUSE_PRESSED, mouseEvent -> {
                if (isEmpty()) return;
                // como las casillas de SQLyog: cada clic añade o quita la fila; con Mayusculas, el rango
                if (mouseEvent.getButton() == MouseButton.PRIMARY) {
                    selectRows(getIndex(), mouseEvent.isShiftDown(), true);
                }
                // clic derecho sobre una fila no elegida: pasa a ser la elegida antes de abrir el menu
                else if (mouseEvent.getButton() == MouseButton.SECONDARY && !isRowSelected(getIndex())) {
                    selectRows(getIndex(), false, false);
                }
                mouseEvent.consume();
            });
        }

        @Override
        protected void updateItem(String value, boolean isEmpty) {
            super.updateItem(value, isEmpty);
            List<String> row = isEmpty || getTableRow() == null ? null : getTableRow().getItem();
            if (row == null) {
                setText(null);
                setGraphic(null);
                return;
            }
            boolean isNewRow = editSession != null && TableEditRowStateEnum.STATE_NEW.equals(editSession.getState(row));
            setText(isNewRow ? "*" : String.valueOf(getIndex() + 1));
            setGraphic(rowCheckBox);
            refreshCheckBox();
        }

        private void refreshCheckBox() {
            rowCheckBox.setSelected(!isEmpty() && getTableRow() != null && getTableRow().getItem() != null && isRowSelected(getIndex()));
        }
    }

    private final class EditableRow extends TableRow<List<String>> {

        @Override
        protected void updateItem(List<String> row, boolean isEmpty) {
            super.updateItem(row, isEmpty);
            getStyleClass().removeAll("row-new", "row-modified", "row-deleted");
            if (isEmpty || row == null || editSession == null) return;
            String state = editSession.getState(row);
            if (state != null) getStyleClass().add("row-" + state);
        }
    }

    /**
     * Una celda: NULL se ve como (NULL) en gris, distinto de la cadena vacia; los textos largos y multilinea se ven
     * recortados en una linea (el valor entero, en el visor). En solo lectura el doble clic abre el visor; en la rejilla
     * editable, edita (Enter confirma, Esc cancela; vaciar una celda NULL la deja NULL).
     */
    private final class ResultCell extends TableCell<List<String>, String> {

        private TextField editField;

        private ResultCell() {
            setOnMouseClicked(mouseEvent -> {
                if (mouseEvent.getButton() != MouseButton.PRIMARY || mouseEvent.getClickCount() != 2 || isEmpty()) return;
                if (editSession != null && getTableColumn().isEditable()) {
                    getTableView().edit(getIndex(), getTableColumn());
                    return;
                }
                onCellOpened.open(String.valueOf(getTableColumn().getUserData()), getItem());
            });
        }

        @Override
        public void startEdit() {
            if (editSession == null || !getTableColumn().isEditable()) return;
            List<String> row = getTableRow() == null ? null : getTableRow().getItem();
            if (row != null && TableEditRowStateEnum.STATE_DELETED.equals(editSession.getState(row))) return;
            super.startEdit();
            if (!isEditing()) return;
            String originalValue = getItem();
            editField = new TextField(originalValue == null ? "" : originalValue);
            editField.setOnAction(actionEvent -> commitEdit(originalValue == null && editField.getText().isEmpty() ? null : editField.getText()));
            editField.setOnKeyPressed(keyEvent -> {
                if (keyEvent.getCode() == KeyCode.ESCAPE) cancelEdit();
            });
            setText(null);
            setGraphic(editField);
            editField.selectAll();
            editField.requestFocus();
        }

        @Override
        public void cancelEdit() {
            super.cancelEdit();
            setGraphic(null);
            showValue(getItem());
        }

        @Override
        public void commitEdit(String newValue) {
            super.commitEdit(newValue);
            setGraphic(null);
        }

        @Override
        protected void updateItem(String value, boolean isEmpty) {
            super.updateItem(value, isEmpty);
            if (isEmpty) {
                getStyleClass().removeAll("null-cell", "json-cell");
                setText(null);
                setGraphic(null);
                return;
            }
            if (isEditing()) return;
            setGraphic(null);
            showValue(value);
        }

        private void showValue(String value) {
            getStyleClass().removeAll("null-cell", "json-cell");
            List<String> row = getTableRow() == null ? null : getTableRow().getItem();
            if (editSession != null && row != null && editSession.isDefault(row, getDataIndex(getTableColumn()))) {
                setText(DEFAULT_TEXT);
                getStyleClass().add("null-cell");
                return;
            }
            if (value == null) {
                setText(ResultTextFormatter.NULL_TEXT);
                getStyleClass().add("null-cell");
                return;
            }
            String trimmed = value.trim();
            if (trimmed.startsWith("{") || trimmed.startsWith("[")) getStyleClass().add("json-cell");
            String oneLine = value.replace('\n', ' ').replace('\r', ' ');
            setText(oneLine.length() > MAX_CELL_TEXT ? oneLine.substring(0, MAX_CELL_TEXT) + "..." : oneLine);
        }
    }
}
