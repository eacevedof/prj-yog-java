package modules.query.infrastructure.controllers;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.Cursor;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.RadioButton;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import modules.shared.application.Primitives;
import modules.shared.infrastructure.components.files.FolderOpenerComponent;
import modules.shared.infrastructure.components.theme.ThemeComponent;
import modules.shared.infrastructure.controllers.AbstractFxController;

import static core.config.Global.__;

import modules.workspace.domain.enums.WorkspaceKeyEnum;
import modules.workspace.infrastructure.components.LastDirectoryComponent;

import modules.query.domain.enums.ResultExportFormatEnum;
import modules.query.domain.entities.ResultExportOptionsEntity;
import modules.query.infrastructure.components.ResultExportComponent;

/**
 * El dialogo "Exportar como" de la rejilla, como el Export As de SQLyog: formato, opciones de CSV (separadores con la
 * notacion \t \n &lt;NONE&gt;), de Excel XML y de SQL, los campos a exportar, todas las filas o solo las marcadas, el
 * juego de caracteres y el fichero. Lo elegido se recuerda mientras la app sigue abierta.
 */
public final class ResultExportDialogController extends AbstractFxController {

    private static final Map<String, Object> lastChoices = new HashMap<>();
    private static final String STYLE_MESSAGE_OK = "form-message-ok";
    private static final String STYLE_MESSAGE_ERROR = "form-message-error";

    @FXML
    private ToggleGroup formatToggleGroup;
    @FXML
    private HBox formatBox;
    @FXML
    private VBox csvOptionsBox;
    @FXML
    private TextField fieldsTerminatedByField;
    @FXML
    private TextField enclosedByField;
    @FXML
    private TextField escapedByField;
    @FXML
    private TextField linesTerminatedByField;
    @FXML
    private TextField nullReplacedByField;
    @FXML
    private VBox excelOptionsBox;
    @FXML
    private TextField textSizeLimitField;
    @FXML
    private TextField decimalPlacesField;
    @FXML
    private VBox sqlOptionsBox;
    @FXML
    private TextField tableNameField;
    @FXML
    private TextField rowsPerInsertField;
    @FXML
    private CheckBox createTableCheckBox;
    @FXML
    private CheckBox headerCheckBox;
    @FXML
    private ListView<String> columnsListView;
    @FXML
    private RadioButton allRowsRadio;
    @FXML
    private RadioButton selectedRowsRadio;
    @FXML
    private ComboBox<String> charsetComboBox;
    @FXML
    private TextField filePathField;
    @FXML
    private Button openFolderButton;
    @FXML
    private ProgressBar progressBar;
    @FXML
    private Label statusLabel;
    @FXML
    private Button exportButton;

    private List<String> columnNames = List.of();
    private List<List<String>> allRows = List.of();
    private List<List<String>> markedRows = List.of();
    private Callable<String> createTableSqlLoader;
    private String mappedTableName = "";

    public void setup(String preselectedFormat, String tableName, List<String> columnNames, List<List<String>> allRows, List<List<String>> markedRows, Callable<String> createTableSqlLoader) {
        this.columnNames = columnNames;
        this.allRows = allRows;
        this.markedRows = markedRows;
        this.createTableSqlLoader = createTableSqlLoader;
        this.mappedTableName = createTableSqlLoader == null ? "" : tableName;

        String initialFormat = preselectedFormat != null ? preselectedFormat : getLastChoice("format", ResultExportFormatEnum.CSV);
        for (String format : ResultExportFormatEnum.VALUES) {
            RadioButton formatRadio = new RadioButton(ResultExportFormatEnum.LABELS.get(format));
            formatRadio.setUserData(format);
            formatRadio.setToggleGroup(formatToggleGroup);
            formatRadio.setSelected(format.equals(initialFormat));
            formatBox.getChildren().add(formatRadio);
        }
        formatToggleGroup.selectedToggleProperty().addListener((observable, oldToggle, newToggle) -> {
            if (newToggle == null) return;
            onFormatChanged();
        });

        fieldsTerminatedByField.setText(getLastChoice("fieldsTerminatedBy", ResultExportOptionsEntity.getEscaped(ResultExportOptionsEntity.DEFAULT_FIELDS_TERMINATED_BY)));
        enclosedByField.setText(getLastChoice("enclosedBy", ResultExportOptionsEntity.getEscaped(ResultExportOptionsEntity.DEFAULT_ENCLOSED_BY)));
        escapedByField.setText(getLastChoice("escapedBy", ResultExportOptionsEntity.getEscaped(ResultExportOptionsEntity.DEFAULT_ESCAPED_BY)));
        linesTerminatedByField.setText(getLastChoice("linesTerminatedBy", ResultExportOptionsEntity.getEscaped(ResultExportOptionsEntity.DEFAULT_LINES_TERMINATED_BY)));
        nullReplacedByField.setText(getLastChoice("nullReplacedBy", ResultExportOptionsEntity.DEFAULT_NULL_REPLACED_BY));
        textSizeLimitField.setText(getLastChoice("textSizeLimit", String.valueOf(ResultExportOptionsEntity.DEFAULT_TEXT_SIZE_LIMIT)));
        decimalPlacesField.setText(getLastChoice("decimalPlaces", String.valueOf(ResultExportOptionsEntity.DEFAULT_DECIMAL_PLACES)));
        rowsPerInsertField.setText(getLastChoice("rowsPerInsert", String.valueOf(ResultExportOptionsEntity.DEFAULT_ROWS_PER_INSERT)));
        tableNameField.setText(tableName);
        headerCheckBox.setSelected(!"false".equals(getLastChoice("isHeaderIncluded", "true")));
        createTableCheckBox.setSelected(createTableSqlLoader != null && "true".equals(getLastChoice("isCreateTableIncluded", "false")));
        tableNameField.textProperty().addListener((observable, oldText, newText) -> updateCreateTableCheckBox());

        columnsListView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        columnsListView.getItems().setAll(columnNames);
        columnsListView.getSelectionModel().selectAll();

        allRowsRadio.setText(__("Todas ({0})", allRows.size()));
        selectedRowsRadio.setText(__("Solo las marcadas ({0})", markedRows.size()));
        selectedRowsRadio.setDisable(markedRows.isEmpty());

        charsetComboBox.getItems().setAll(ResultExportComponent.CHARSET_LABELS);
        charsetComboBox.setValue(getLastChoice("charset", ResultExportComponent.CHARSET_LABELS.getFirst()));

        File initialDirectory = LastDirectoryComponent.getInstance(WorkspaceKeyEnum.RESULT_EXPORT_DIRECTORY).getDirectory();
        filePathField.textProperty().addListener((observable, oldPath, newPath) -> updateOpenFolderButton());
        String baseName = tableName.isBlank() ? "export" : tableName;
        filePathField.setText(new File(initialDirectory, baseName + "." + ResultExportFormatEnum.getExtension(getFormat())).getPath());
        updateCreateTableCheckBox();
        onFormatChanged();
    }

    @FXML
    private void onSelectAllColumns() {
        columnsListView.getSelectionModel().selectAll();
    }

    @FXML
    private void onDeselectAllColumns() {
        columnsListView.getSelectionModel().clearSelection();
    }

    @FXML
    private void onChooseFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(__("Exportar como"));
        String extension = ResultExportFormatEnum.getExtension(getFormat());
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
            ResultExportFormatEnum.LABELS.get(getFormat()) + " (*." + extension + ")", "*." + extension
        ));
        File currentFile = new File(filePathField.getText().trim());
        File currentDirectory = currentFile.getParentFile();
        if (currentDirectory != null && currentDirectory.isDirectory()) fileChooser.setInitialDirectory(currentDirectory);
        fileChooser.setInitialFileName(currentFile.getName());
        File chosenFile = fileChooser.showSaveDialog(filePathField.getScene().getWindow());
        if (chosenFile == null) return;
        filePathField.setText(chosenFile.getPath());
        LastDirectoryComponent.getInstance(WorkspaceKeyEnum.RESULT_EXPORT_DIRECTORY).saveDirectoryOf(chosenFile);
    }

    @FXML
    private void onOpenFolder() {
        try {
            FolderOpenerComponent.getInstance().open(filePathField.getText());
        }
        catch (IllegalArgumentException e) {
            if (!statusLabel.textProperty().isBound()) statusLabel.setText(__("No existe la carpeta de {0}", filePathField.getText().trim()));
        }
    }

    /**
     * Solo se puede abrir si existe la carpeta (o alguna de sus padres).
     */
    private void updateOpenFolderButton() {
        openFolderButton.setDisable(FolderOpenerComponent.getInstance().getExistingFolder(filePathField.getText()) == null);
    }

    @FXML
    private void onExport() {
        List<Integer> columnIndexes = new ArrayList<>(columnsListView.getSelectionModel().getSelectedIndices());
        columnIndexes.sort(Integer::compareTo);
        if (columnIndexes.isEmpty()) {
            statusLabel.setText(__("Elige al menos un campo"));
            return;
        }
        String filePath = filePathField.getText().trim();
        if (filePath.isEmpty()) {
            statusLabel.setText(__("Falta el fichero"));
            return;
        }

        List<String> exportColumnNames = new ArrayList<>();
        for (int columnIndex : columnIndexes) exportColumnNames.add(columnNames.get(columnIndex));
        List<List<String>> exportRows = new ArrayList<>();
        for (List<String> row : selectedRowsRadio.isSelected() ? markedRows : allRows) {
            List<String> values = new ArrayList<>();
            for (int columnIndex : columnIndexes) values.add(columnIndex < row.size() ? row.get(columnIndex) : null);
            exportRows.add(values);
        }

        String format = getFormat();
        String charsetLabel = charsetComboBox.getValue();
        Map<String, Object> choices = getChoices(charsetLabel);
        Map<String, Object> optionPrimitives = Primitives.of(
            "fieldsTerminatedBy", choices.get("fieldsTerminatedBy"),
            "enclosedBy", choices.get("enclosedBy"),
            "escapedBy", choices.get("escapedBy"),
            "linesTerminatedBy", choices.get("linesTerminatedBy"),
            "nullReplacedBy", choices.get("nullReplacedBy"),
            "isHeaderIncluded", headerCheckBox.isSelected(),
            "textSizeLimit", choices.get("textSizeLimit"),
            "decimalPlaces", choices.get("decimalPlaces"),
            "tableName", tableNameField.getText().trim(),
            "rowsPerInsert", choices.get("rowsPerInsert"),
            "charset", ResultExportComponent.getDeclaredCharset(charsetLabel)
        );
        boolean isCreateTableIncluded = ResultExportFormatEnum.SQL.equals(format) && isCreateTableAvailable() && createTableCheckBox.isSelected();
        Path exportPath = Path.of(filePath).toAbsolutePath();
        int rowCount = exportRows.size();
        int columnCount = exportColumnNames.size();

        setExporting(true);
        Task<Void> exportTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                Map<String, Object> primitives = new HashMap<>(optionPrimitives);
                if (isCreateTableIncluded) {
                    updateMessage(__("Leyendo el CREATE TABLE de {0}...", mappedTableName));
                    primitives.put("createTableSql", createTableSqlLoader.call());
                }
                updateMessage(__("Exportando {0} fila(s) a {1}...", rowCount, exportPath));
                ResultExportComponent.getInstance().write(exportPath, format, exportColumnNames, exportRows, ResultExportOptionsEntity.fromPrimitives(primitives), charsetLabel);
                return null;
            }
        };
        statusLabel.textProperty().bind(exportTask.messageProperty());
        exportTask.setOnSucceeded(event -> {
            setExporting(false);
            progressBar.setProgress(1);
            lastChoices.putAll(choices);
            LastDirectoryComponent.getInstance(WorkspaceKeyEnum.RESULT_EXPORT_DIRECTORY).saveDirectoryOf(exportPath.toFile());
            updateOpenFolderButton();
            String doneMessage = __("Exportadas {0} fila(s) y {1} campo(s) a {2}", rowCount, columnCount, exportPath);
            statusLabel.getStyleClass().add(STYLE_MESSAGE_OK);
            statusLabel.setText(doneMessage);
            showDoneAlert(doneMessage);
        });
        exportTask.setOnFailed(event -> {
            setExporting(false);
            progressBar.setProgress(0);
            Throwable exception = exportTask.getException();
            statusLabel.getStyleClass().add(STYLE_MESSAGE_ERROR);
            statusLabel.setText(__("No se pudo escribir {0}: {1}", filePath, exception == null ? "" : getErrorMessage(exception)));
        });
        Thread exportThread = new Thread(exportTask, "result-export");
        exportThread.setDaemon(true);
        exportThread.start();
    }

    /**
     * Mientras se exporta: la barra en marcha y el dialogo sin tocar (no se puede lanzar otra exportacion ni cerrar).
     */
    private void setExporting(boolean isExporting) {
        statusLabel.textProperty().unbind();
        statusLabel.getStyleClass().removeAll(STYLE_MESSAGE_OK, STYLE_MESSAGE_ERROR);
        progressBar.setVisible(true);
        progressBar.setManaged(true);
        if (isExporting) progressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
        exportButton.getParent().setDisable(isExporting);
        exportButton.getScene().getRoot().setCursor(isExporting ? Cursor.WAIT : null);
    }

    private void showDoneAlert(String doneMessage) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, doneMessage);
        alert.setTitle(__("Exportar como"));
        alert.setHeaderText(__("Exportación terminada"));
        alert.initOwner(exportButton.getScene().getWindow());
        ThemeComponent.getInstance().applyTo(alert);
        alert.showAndWait();
    }

    /**
     * "Incluir CREATE TABLE" solo si la rejilla es de una tabla y los INSERT van a esa misma tabla: con otro nombre, el
     * CREATE crearia una tabla y los INSERT irian a otra.
     */
    private boolean isCreateTableAvailable() {
        return createTableSqlLoader != null && mappedTableName.equals(tableNameField.getText().trim());
    }

    private void updateCreateTableCheckBox() {
        boolean isAvailable = isCreateTableAvailable();
        createTableCheckBox.setDisable(!isAvailable);
        if (createTableSqlLoader == null) {
            createTableCheckBox.setTooltip(new Tooltip(__("Solo cuando la rejilla es de una tabla (pestaña Datos)")));
            return;
        }
        createTableCheckBox.setTooltip(new Tooltip(isAvailable
            ? __("Pone delante de los INSERT el CREATE TABLE de {0}", mappedTableName)
            : __("Solo con la tabla {0}: los INSERT van a otra", mappedTableName)));
    }

    @FXML
    private void onClose() {
        ((Stage) exportButton.getScene().getWindow()).close();
    }

    /**
     * Cada formato con sus opciones; la extension del fichero cambia con el.
     */
    private void onFormatChanged() {
        String format = getFormat();
        csvOptionsBox.setDisable(!ResultExportFormatEnum.CSV.equals(format));
        excelOptionsBox.setDisable(!ResultExportFormatEnum.EXCEL_XML.equals(format));
        sqlOptionsBox.setDisable(!ResultExportFormatEnum.SQL.equals(format));
        headerCheckBox.setDisable(!ResultExportFormatEnum.CSV.equals(format) && !ResultExportFormatEnum.EXCEL_XML.equals(format));
        String filePath = filePathField.getText().trim();
        if (filePath.isEmpty()) return;
        int dotIndex = filePath.lastIndexOf('.');
        int separatorIndex = Math.max(filePath.lastIndexOf('/'), filePath.lastIndexOf('\\'));
        String basePath = dotIndex > separatorIndex ? filePath.substring(0, dotIndex) : filePath;
        filePathField.setText(basePath + "." + ResultExportFormatEnum.getExtension(format));
    }

    private String getFormat() {
        if (formatToggleGroup.getSelectedToggle() == null) return ResultExportFormatEnum.CSV;
        return String.valueOf(formatToggleGroup.getSelectedToggle().getUserData());
    }

    private Map<String, Object> getChoices(String charsetLabel) {
        return Primitives.of(
            "format", getFormat(),
            "fieldsTerminatedBy", fieldsTerminatedByField.getText(),
            "enclosedBy", enclosedByField.getText(),
            "escapedBy", escapedByField.getText(),
            "linesTerminatedBy", linesTerminatedByField.getText(),
            "nullReplacedBy", nullReplacedByField.getText(),
            "isHeaderIncluded", String.valueOf(headerCheckBox.isSelected()),
            "textSizeLimit", textSizeLimitField.getText().trim(),
            "decimalPlaces", decimalPlacesField.getText().trim(),
            "rowsPerInsert", rowsPerInsertField.getText().trim(),
            "isCreateTableIncluded", String.valueOf(createTableCheckBox.isSelected()),
            "charset", charsetLabel == null ? ResultExportComponent.CHARSET_LABELS.getFirst() : charsetLabel
        );
    }

    private static String getLastChoice(String key, String defaultValue) {
        Object value = lastChoices.get(key);
        return value == null ? defaultValue : String.valueOf(value);
    }
}
