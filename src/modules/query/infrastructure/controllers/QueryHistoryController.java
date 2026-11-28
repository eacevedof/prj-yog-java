package modules.query.infrastructure.controllers;

import java.io.File;
import java.io.IOException;
import java.util.Comparator;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.stage.FileChooser;

import modules.shared.application.Primitives;
import modules.shared.infrastructure.components.date.DateTimer;
import modules.shared.infrastructure.controllers.AbstractFxController;
import static modules.shared.application.Primitives.getBool;
import static modules.shared.application.Primitives.getTrimmedString;

import static core.config.Global.__;

import modules.history.domain.enums.QueryHistoryKeyEnum;
import modules.history.application.clearqueryhistory.ClearQueryHistoryDto;
import modules.history.application.exportqueryhistory.ExportQueryHistoryDto;
import modules.history.application.exportqueryhistory.ExportQueryHistoryResultDto;
import modules.history.application.listqueryhistory.ListQueryHistoryDto;
import modules.history.application.clearqueryhistory.ClearQueryHistoryService;
import modules.history.application.exportqueryhistory.ExportQueryHistoryService;
import modules.history.application.listqueryhistory.ListQueryHistoryService;

import modules.workspace.domain.enums.WorkspaceKeyEnum;
import modules.workspace.infrastructure.components.LastDirectoryComponent;

import modules.query.domain.enums.QueryConfigEnum;

/**
 * El historial (feature 8), como la pestaña History de SQLyog: una pestaña mas del resultado, no un dialogo. Rejilla
 * por id descendente (lo ultimo arriba) y ordenable por cualquier columna; busca mientras se escribe, de esta conexion
 * o de todas. Doble clic o "Pegar en el editor" lleva el SQL al editor de la pestaña. Las ejecuciones con error salen
 * en rojo. "Limpiar historial" borra lo que se ve: el de esta conexion o, sin la casilla, el de todas.
 */
public final class QueryHistoryController extends AbstractFxController {

    @FXML
    private TextField searchField;
    @FXML
    private CheckBox onlyThisConnectionCheckBox;
    @FXML
    private TableView<Map<String, Object>> historyTableView;
    @FXML
    private TableColumn<Map<String, Object>, String> idColumn;
    @FXML
    private TableColumn<Map<String, Object>, String> executedAtColumn;
    @FXML
    private TableColumn<Map<String, Object>, String> connectionColumn;
    @FXML
    private TableColumn<Map<String, Object>, String> databaseColumn;
    @FXML
    private TableColumn<Map<String, Object>, String> elapsedColumn;
    @FXML
    private TableColumn<Map<String, Object>, String> rowsColumn;
    @FXML
    private TableColumn<Map<String, Object>, String> sqlColumn;
    @FXML
    private TextArea sqlTextArea;
    @FXML
    private Label countLabel;
    @FXML
    private Button pasteButton;
    @FXML
    private Button clearButton;

    private final ObservableList<Map<String, Object>> historyRows = FXCollections.observableArrayList();
    private String connectionUuid = "";
    private String dbEngine = "";
    private Consumer<String> onPasteSql = sql -> { };

    @FXML
    private void initialize() {
        setCellValue(idColumn, row -> getTrimmedString(row.get(QueryHistoryKeyEnum.ID)));
        setCellValue(executedAtColumn, row -> getTrimmedString(row.get(QueryHistoryKeyEnum.EXECUTED_AT)));
        setCellValue(connectionColumn, row -> getTrimmedString(row.get(QueryHistoryKeyEnum.CONNECTION_NAME)));
        setCellValue(databaseColumn, row -> getTrimmedString(row.get(QueryHistoryKeyEnum.DATABASE_NAME)));
        setCellValue(elapsedColumn, row -> getTrimmedString(row.get(QueryHistoryKeyEnum.ELAPSED_MS)));
        setCellValue(rowsColumn, row -> getTrimmedString(row.get(QueryHistoryKeyEnum.ROWS_COUNT)));
        setCellValue(sqlColumn, row -> getTrimmedString(row.get(QueryHistoryKeyEnum.SQL_QUERY)).replaceAll("\\s+", " "));
        Comparator<String> numericComparator = Comparator.comparingInt(Primitives::getInt);
        idColumn.setComparator(numericComparator);
        elapsedColumn.setComparator(numericComparator);
        rowsColumn.setComparator(numericComparator);

        SortedList<Map<String, Object>> sortedHistoryRows = new SortedList<>(historyRows);
        sortedHistoryRows.comparatorProperty().bind(historyTableView.comparatorProperty());
        historyTableView.setItems(sortedHistoryRows);
        idColumn.setSortType(TableColumn.SortType.DESCENDING);
        historyTableView.getSortOrder().setAll(idColumn);

        historyTableView.setRowFactory(tableView -> {
            TableRow<Map<String, Object>> tableRow = new TableRow<>() {
                @Override
                protected void updateItem(Map<String, Object> row, boolean isEmpty) {
                    super.updateItem(row, isEmpty);
                    getStyleClass().remove("history-error");
                    if (!isEmpty && row != null && getBool(row.get(QueryHistoryKeyEnum.HAS_ERROR))) {
                        getStyleClass().add("history-error");
                    }
                }
            };
            tableRow.setOnMouseClicked(mouseEvent -> {
                if (mouseEvent.getButton() == MouseButton.PRIMARY && mouseEvent.getClickCount() == 2 && !tableRow.isEmpty()) {
                    onPaste();
                }
            });
            return tableRow;
        });
        historyTableView.getSelectionModel().selectedItemProperty().addListener((observable, oldRow, newRow) -> {
            sqlTextArea.setText(newRow == null ? "" : getTrimmedString(newRow.get(QueryHistoryKeyEnum.SQL_QUERY)));
            pasteButton.setDisable(newRow == null);
        });
        searchField.textProperty().addListener((observable, oldText, newText) -> refresh());
        onlyThisConnectionCheckBox.selectedProperty().addListener((observable, wasSelected, isSelected) -> refresh());
        pasteButton.setDisable(true);
        // F5 desde el buscador o la rejilla: lo lanzado desde el arbol (DROP, duplicar...) no refresca solo
        searchField.addEventFilter(KeyEvent.KEY_PRESSED, this::onF5Pressed);
        historyTableView.addEventFilter(KeyEvent.KEY_PRESSED, this::onF5Pressed);
    }

    private void onF5Pressed(KeyEvent keyEvent) {
        if (keyEvent.getCode() != KeyCode.F5) return;
        refresh();
        keyEvent.consume();
    }

    @FXML
    private void onRefresh() {
        refresh();
    }

    /**
     * La conexion de la pestaña y que hacer con el SQL elegido. No carga nada: se carga al ensenar la pestaña (refresh).
     */
    public void setup(String connectionUuid, String dbEngine, Consumer<String> onPasteSql) {
        this.connectionUuid = connectionUuid;
        this.dbEngine = dbEngine;
        this.onPasteSql = onPasteSql;
    }

    /**
     * Ctrl+H: el cursor al buscador.
     */
    public void focusSearch() {
        searchField.requestFocus();
    }

    /**
     * Vuelve a leer el historial (al abrir la pestaña y tras cada ejecucion con ella delante); el orden elegido se
     * conserva.
     */
    public void refresh() {
        try {
            historyRows.setAll(ListQueryHistoryService.getInstance().invoke(ListQueryHistoryDto.fromPrimitives(Primitives.of(
                "connectionUuid", getFilterConnectionUuid(),
                "searchText", searchField.getText()
            ))).getHistory());
            countLabel.setText(__("{0} ejecución(es)", historyRows.size()));
            clearButton.setDisable(historyRows.isEmpty() && searchField.getText().isBlank());
        }
        catch (RuntimeException e) {
            showError(e);
        }
    }

    /**
     * "" = todas las conexiones.
     */
    private String getFilterConnectionUuid() {
        return onlyThisConnectionCheckBox.isSelected() ? connectionUuid : "";
    }

    @FXML
    private void onPaste() {
        Map<String, Object> selectedRow = historyTableView.getSelectionModel().getSelectedItem();
        if (selectedRow == null) return;
        onPasteSql.accept(getTrimmedString(selectedRow.get(QueryHistoryKeyEnum.SQL_QUERY)));
    }

    @FXML
    private void onCopy() {
        ClipboardContent clipboardContent = new ClipboardContent();
        clipboardContent.putString(sqlTextArea.getText());
        Clipboard.getSystemClipboard().setContent(clipboardContent);
    }

    /**
     * Lo que se ve (esta conexion o todas, y lo buscado) a un .sql en orden de ejecucion, para volver a lanzarlo con
     * Importar y llegar a la misma foto: lo que no es una sentencia que se pueda relanzar va comentado.
     */
    @FXML
    private void onExportSql() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(__("Exportar el historial a SQL"));
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(__("Script SQL"), "*.sql"));
        fileChooser.setInitialFileName(QueryConfigEnum.HISTORY_EXPORT_FILE_NAME_X.formatted(DateTimer.getInstance().getNowCompactDt()));
        // la misma carpeta que el Volcado SQL: los .sql exportados quedan juntos
        LastDirectoryComponent lastDirectoryComponent = LastDirectoryComponent.getInstance(WorkspaceKeyEnum.SQL_DUMP_DIRECTORY);
        fileChooser.setInitialDirectory(lastDirectoryComponent.getDirectory());
        File sqlExportFile = fileChooser.showSaveDialog(historyTableView.getScene().getWindow());
        if (sqlExportFile == null) return;
        lastDirectoryComponent.saveDirectoryOf(sqlExportFile);
        try {
            ExportQueryHistoryResultDto exportQueryHistoryResultDto = ExportQueryHistoryService.getInstance().invoke(ExportQueryHistoryDto.fromPrimitives(Primitives.of(
                "connectionUuid", getFilterConnectionUuid(),
                "searchText", searchField.getText(),
                "dbEngine", dbEngine,
                "filePath", sqlExportFile.getAbsolutePath()
            )));
            countLabel.setText(__("Exportadas {0} ejecución(es) a {1}", exportQueryHistoryResultDto.getExportedCount(), exportQueryHistoryResultDto.getFilePath()));
        }
        catch (IOException e) {
            showErrorMessage(__("No se pudo escribir {0}: {1}", sqlExportFile.getAbsolutePath(), e.getMessage()));
        }
        catch (RuntimeException e) {
            showError(e);
        }
    }

    /**
     * Borra todo el historial de esta conexion (o de todas sin la casilla), no solo lo que filtra el buscador. Se
     * pregunta antes y el boton por defecto es No.
     */
    @FXML
    private void onClear() {
        String filterConnectionUuid = getFilterConnectionUuid();
        String question = filterConnectionUuid.isEmpty()
            ? __("¿Borrar el historial de TODAS las conexiones?")
            : __("¿Borrar el historial de esta conexión?");
        if (!isDeleteConfirmed(question)) return;
        try {
            int deletedCount = ClearQueryHistoryService.getInstance().invoke(ClearQueryHistoryDto.fromPrimitives(Primitives.of(
                "connectionUuid", filterConnectionUuid
            ))).getDeletedCount();
            refresh();
            countLabel.setText(__("Historial limpio: {0} ejecución(es) borradas", deletedCount));
        }
        catch (RuntimeException e) {
            showError(e);
        }
    }

    private void setCellValue(TableColumn<Map<String, Object>, String> tableColumn, Function<Map<String, Object>, String> getValue) {
        tableColumn.setCellValueFactory(cellData -> new SimpleStringProperty(getValue.apply(cellData.getValue())));
    }
}
