package modules.explorer.infrastructure.controllers;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.control.cell.ComboBoxTableCell;
import javafx.scene.input.KeyCode;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import modules.shared.domain.enums.ExceptionEnum;
import modules.shared.domain.services.SqlDialect;
import modules.shared.application.Primitives;
import modules.shared.infrastructure.components.theme.ThemeComponent;
import modules.shared.infrastructure.controllers.AbstractFxController;
import modules.shared.infrastructure.exceptions.InterfaceException;

import core.config.TranslatorResourceBundle;
import static core.config.Global.__;

import modules.explorer.domain.enums.ForeignKeyActionEnum;
import modules.explorer.domain.enums.TableIndexTypeEnum;
import modules.explorer.domain.entities.TableColumnEntity;
import modules.explorer.domain.entities.TableDefinitionEntity;
import modules.explorer.domain.entities.TableForeignKeyEntity;
import modules.explorer.domain.entities.TableIndexEntity;
import modules.explorer.domain.services.TableAlterSqlBuilder;
import modules.explorer.domain.services.TableCreateSqlBuilder;
import modules.explorer.application.altertable.AlterTableDto;
import modules.explorer.application.createtable.CreateTableDto;
import modules.explorer.application.getcreatetableoptions.GetCreateTableOptionsDto;
import modules.explorer.application.getcreatetableoptions.GetCreateTableOptionsResultDto;
import modules.explorer.application.gettabledefinition.GetTableDefinitionDto;
import modules.explorer.application.gettabledefinition.GetTableDefinitionResultDto;
import modules.explorer.application.altertable.AlterTableService;
import modules.explorer.application.createtable.CreateTableService;
import modules.explorer.application.getcreatetableoptions.GetCreateTableOptionsService;
import modules.explorer.application.gettabledefinition.GetTableDefinitionService;

/**
 * El "Alter Table" de SQLyog (MySQL/MariaDB) en pestañas: columnas (con la PK), indices, claves foraneas, opciones
 * avanzadas y el SQL que se va a ejecutar. Lee la tabla al abrirse, se edita en memoria y al guardar manda la tabla
 * de antes y la de ahora a AlterTableService, que solo cambia lo que cambio. Al guardar, onSaved recibe el nombre con
 * el que queda la tabla (el arbol la recarga).
 */
public final class TableEditorDialogController extends AbstractFxController {

    private static final String PATH_DIALOG = "/modules/explorer/infrastructure/views/table-editor-dialog.fxml";
    private static final List<String> ROW_FORMATS = List.of("DEFAULT", "DYNAMIC", "COMPACT", "COMPRESSED", "REDUNDANT", "FIXED");

    @FXML
    private TextField tableNameField;
    @FXML
    private Label databaseLabel;
    @FXML
    private TabPane editorTabPane;
    @FXML
    private TableView<ColumnRow> columnsTableView;
    @FXML
    private TableView<IndexRow> indexesTableView;
    @FXML
    private TableView<ForeignKeyRow> foreignKeysTableView;
    @FXML
    private ComboBox<String> engineComboBox;
    @FXML
    private ComboBox<String> collationComboBox;
    @FXML
    private TextField commentField;
    @FXML
    private TextField autoIncrementField;
    @FXML
    private ComboBox<String> rowFormatComboBox;
    @FXML
    private Tab sqlTab;
    @FXML
    private TextArea sqlTextArea;
    @FXML
    private Label statusLabel;
    @FXML
    private Button saveButton;
    @FXML
    private Button closeButton;

    private String connectionUuid = "";
    private String sessionKey = "";
    private String databaseName = "";
    private TableDefinitionEntity original;
    private boolean isNewTable;
    private String dbEngine = SqlDialect.MYSQL;
    private TableColumn<ColumnRow, Boolean> unsignedTableColumn;
    private TableColumn<ColumnRow, Boolean> zerofillTableColumn;
    private TableColumn<IndexRow, String> indexTypeTableColumn;
    private Consumer<String> onSaved = tableName -> { };

    /**
     * Abre el dialogo (no bloquea) y lee la tabla en segundo plano.
     */
    public static void open(Window owner, String connectionUuid, String sessionKey, String databaseName, String tableName, Consumer<String> onSaved) {
        Stage dialogStage = new Stage();
        TableEditorDialogController tableEditorDialogController = getLoadedController(owner, dialogStage, __("Editar tabla {0}", tableName));
        tableEditorDialogController.setup(connectionUuid, sessionKey, databaseName, tableName, onSaved);
        dialogStage.show();
    }

    /**
     * El "Create Table" de SQLyog: el mismo editor, vacio, que al guardar crea la tabla en databaseName.
     */
    public static void openNewTable(Window owner, String connectionUuid, String sessionKey, String databaseName, Consumer<String> onSaved) {
        Stage dialogStage = new Stage();
        TableEditorDialogController tableEditorDialogController = getLoadedController(owner, dialogStage, __("Crear tabla en {0}", databaseName));
        tableEditorDialogController.setupNewTable(connectionUuid, sessionKey, databaseName, onSaved);
        dialogStage.show();
    }

    private static TableEditorDialogController getLoadedController(Window owner, Stage dialogStage, String title) {
        FXMLLoader fxmlLoader = new FXMLLoader(TableEditorDialogController.class.getResource(PATH_DIALOG), TranslatorResourceBundle.getInstance());
        Parent dialogRoot;
        try {
            dialogRoot = fxmlLoader.load();
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        dialogStage.initOwner(owner);
        dialogStage.initModality(Modality.WINDOW_MODAL);
        dialogStage.setTitle(title);
        Scene dialogScene = new Scene(dialogRoot);
        ThemeComponent.getInstance().applyTo(dialogScene);
        dialogStage.setScene(dialogScene);
        return fxmlLoader.getController();
    }

    private void setup(String connectionUuid, String sessionKey, String databaseName, String tableName, Consumer<String> onSaved) {
        loadEditor(connectionUuid, sessionKey, databaseName, onSaved);
        tableNameField.setText(tableName);
        setStatus(__("Leyendo la tabla..."));
        GetTableDefinitionDto getTableDefinitionDto = GetTableDefinitionDto.fromPrimitives(Primitives.of(
            "connectionUuid", connectionUuid, "sessionKey", sessionKey, "databaseName", databaseName, "tableName", tableName
        ));
        Task<GetTableDefinitionResultDto> getTableDefinitionTask = new Task<>() {
            @Override
            protected GetTableDefinitionResultDto call() throws SQLException {
                return GetTableDefinitionService.getInstance().invoke(getTableDefinitionDto);
            }
        };
        getTableDefinitionTask.setOnSucceeded(event -> showDefinition(getTableDefinitionTask.getValue()));
        getTableDefinitionTask.setOnFailed(event -> setStatus(getErrorMessage(getTableDefinitionTask.getException())));
        startDaemon(getTableDefinitionTask, "table-editor-read");
    }

    /**
     * Sin tabla que leer: solo los motores y cotejamientos del servidor, y una primera columna para empezar a escribir.
     */
    private void setupNewTable(String connectionUuid, String sessionKey, String databaseName, Consumer<String> onSaved) {
        loadEditor(connectionUuid, sessionKey, databaseName, onSaved);
        isNewTable = true;
        setStatus(__("Leyendo motores y cotejamientos..."));
        GetCreateTableOptionsDto getCreateTableOptionsDto = GetCreateTableOptionsDto.fromPrimitives(Primitives.of(
            "connectionUuid", connectionUuid, "sessionKey", sessionKey, "databaseName", databaseName
        ));
        Task<GetCreateTableOptionsResultDto> getCreateTableOptionsTask = new Task<>() {
            @Override
            protected GetCreateTableOptionsResultDto call() throws SQLException {
                return GetCreateTableOptionsService.getInstance().invoke(getCreateTableOptionsDto);
            }
        };
        getCreateTableOptionsTask.setOnSucceeded(event -> showNewTable(getCreateTableOptionsTask.getValue()));
        getCreateTableOptionsTask.setOnFailed(event -> setStatus(getErrorMessage(getCreateTableOptionsTask.getException())));
        startDaemon(getCreateTableOptionsTask, "table-editor-options");
    }

    private void loadEditor(String connectionUuid, String sessionKey, String databaseName, Consumer<String> onSaved) {
        this.connectionUuid = connectionUuid;
        this.sessionKey = sessionKey;
        this.databaseName = databaseName;
        this.onSaved = onSaved;
        databaseLabel.setText(__("en {0}", databaseName));
        setupColumnsTable();
        setupIndexesTable();
        setupForeignKeysTable();
        rowFormatComboBox.getItems().setAll(ROW_FORMATS);
        editorTabPane.getSelectionModel().selectedItemProperty().addListener((observable, oldTab, newTab) -> {
            if (newTab == sqlTab) refreshSqlPreview();
        });
        saveButton.setDisable(true);
    }

    /**
     * Fuera de MySQL se esconde lo que solo existe alli (sin signo, ceros, FULLTEXT/SPATIAL, las opciones de Avanzado
     * y, salvo en PostgreSQL, los comentarios): el SQL los daria como error.
     */
    private void showNewTable(GetCreateTableOptionsResultDto getCreateTableOptionsResultDto) {
        original = new TableDefinitionEntity("", List.of(), List.of(), List.of(), List.of(), "", "", "", "", "");
        dbEngine = getCreateTableOptionsResultDto.getDbEngine();
        engineComboBox.getItems().setAll(getCreateTableOptionsResultDto.getEngines());
        collationComboBox.getItems().setAll(getCreateTableOptionsResultDto.getCollations());
        SqlDialect sqlDialect = SqlDialect.getInstance(dbEngine);
        if (!sqlDialect.isMysql()) {
            unsignedTableColumn.setVisible(false);
            zerofillTableColumn.setVisible(false);
            indexTypeTableColumn.setCellFactory(ComboBoxTableCell.forTableColumn(TableIndexTypeEnum.INDEX, TableIndexTypeEnum.UNIQUE));
            engineComboBox.setDisable(true);
            collationComboBox.setDisable(true);
            autoIncrementField.setDisable(true);
            rowFormatComboBox.setDisable(true);
            commentField.setDisable(!sqlDialect.isPgsql());
        }
        columnsTableView.getItems().add(ColumnRow.ofNew());
        saveButton.setDisable(false);
        setStatus("");
        tableNameField.requestFocus();
    }

    private void showDefinition(GetTableDefinitionResultDto resultDto) {
        original = resultDto.getTableDefinition();
        Set<String> primaryKeyColumns = new LinkedHashSet<>(original.primaryKeyColumns());
        for (TableColumnEntity column : original.columns()) {
            columnsTableView.getItems().add(ColumnRow.of(column, primaryKeyColumns.contains(column.name())));
        }
        for (TableIndexEntity index : original.indexes()) indexesTableView.getItems().add(IndexRow.of(index));
        for (TableForeignKeyEntity foreignKey : original.foreignKeys()) foreignKeysTableView.getItems().add(ForeignKeyRow.of(foreignKey));
        engineComboBox.getItems().setAll(resultDto.getEngines());
        engineComboBox.setValue(original.engine());
        collationComboBox.getItems().setAll(resultDto.getCollations());
        collationComboBox.setValue(original.collation());
        commentField.setText(original.comment());
        autoIncrementField.setText(original.autoIncrement());
        rowFormatComboBox.setValue(original.rowFormat());
        saveButton.setDisable(false);
        setStatus("");
    }

    private void setupColumnsTable() {
        Predicate<ColumnRow> isEditable = columnRow -> !columnRow.isGenerated;
        unsignedTableColumn = getCheckColumn(__("Sin signo"), columnRow -> columnRow.isUnsigned);
        zerofillTableColumn = getCheckColumn(__("Ceros"), columnRow -> columnRow.isZerofill);
        columnsTableView.getColumns().setAll(List.of(
            getTextColumn(__("Nombre"), columnRow -> columnRow.name, 150, isEditable),
            getTextColumn(__("Tipo"), columnRow -> columnRow.dataType, 110, isEditable),
            getTextColumn(__("Longitud/valores"), columnRow -> columnRow.length, 120, isEditable),
            getTextColumn(__("Default"), columnRow -> columnRow.defaultSql, 140, isEditable),
            getCheckColumn("PK", columnRow -> columnRow.isPrimaryKey),
            getCheckColumn(__("No nulo"), columnRow -> columnRow.isNotNull),
            unsignedTableColumn,
            getCheckColumn(__("Auto incr."), columnRow -> columnRow.isAutoIncrement),
            zerofillTableColumn,
            getTextColumn(__("Comentario"), columnRow -> columnRow.comment, 200, isEditable)
        ));
        columnsTableView.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
    }

    private void setupIndexesTable() {
        indexTypeTableColumn = new TableColumn<>(__("Tipo"));
        indexTypeTableColumn.setCellValueFactory(cellData -> cellData.getValue().indexType);
        indexTypeTableColumn.setCellFactory(ComboBoxTableCell.forTableColumn(TableIndexTypeEnum.VALUES.toArray(String[]::new)));
        indexTypeTableColumn.setPrefWidth(120);
        indexesTableView.getColumns().setAll(List.of(
            getTextColumn(__("Nombre"), indexRow -> indexRow.name, 220, indexRow -> true),
            indexTypeTableColumn,
            getTextColumn(__("Columnas"), indexRow -> indexRow.columns, 400, indexRow -> true)
        ));
    }

    private void setupForeignKeysTable() {
        foreignKeysTableView.getColumns().setAll(List.of(
            getTextColumn(__("Nombre"), foreignKeyRow -> foreignKeyRow.name, 170, foreignKeyRow -> true),
            getTextColumn(__("Columnas"), foreignKeyRow -> foreignKeyRow.columns, 160, foreignKeyRow -> true),
            getTextColumn(__("Tabla referida"), foreignKeyRow -> foreignKeyRow.referencedTable, 160, foreignKeyRow -> true),
            getTextColumn(__("Columnas referidas"), foreignKeyRow -> foreignKeyRow.referencedColumns, 160, foreignKeyRow -> true),
            getActionColumn("ON DELETE", foreignKeyRow -> foreignKeyRow.onDelete),
            getActionColumn("ON UPDATE", foreignKeyRow -> foreignKeyRow.onUpdate)
        ));
    }

    @FXML
    private void onAddColumn() {
        columnsTableView.getItems().add(ColumnRow.ofNew());
        editLastName(columnsTableView);
    }

    @FXML
    private void onInsertColumn() {
        int selectedIndex = Math.max(0, columnsTableView.getSelectionModel().getSelectedIndex());
        columnsTableView.getItems().add(selectedIndex, ColumnRow.ofNew());
        columnsTableView.getSelectionModel().select(selectedIndex);
        columnsTableView.edit(selectedIndex, columnsTableView.getColumns().getFirst());
    }

    @FXML
    private void onDeleteColumn() {
        ColumnRow columnRow = columnsTableView.getSelectionModel().getSelectedItem();
        if (columnRow == null) return;
        if (!columnRow.originalName.isEmpty() && !isDeleteConfirmed(__("¿Borrar la columna {0}? Se pierden sus datos al guardar.", columnRow.originalName))) return;
        columnsTableView.getItems().remove(columnRow);
    }

    @FXML
    private void onMoveColumnUp() {
        moveSelectedColumn(-1);
    }

    @FXML
    private void onMoveColumnDown() {
        moveSelectedColumn(1);
    }

    private void moveSelectedColumn(int offset) {
        int selectedIndex = columnsTableView.getSelectionModel().getSelectedIndex();
        int targetIndex = selectedIndex + offset;
        if (selectedIndex < 0 || targetIndex < 0 || targetIndex >= columnsTableView.getItems().size()) return;
        ColumnRow columnRow = columnsTableView.getItems().remove(selectedIndex);
        columnsTableView.getItems().add(targetIndex, columnRow);
        columnsTableView.getSelectionModel().select(targetIndex);
    }

    @FXML
    private void onAddIndex() {
        indexesTableView.getItems().add(new IndexRow("", "ix_" + tableNameField.getText().trim(), TableIndexTypeEnum.INDEX, ""));
        editLastName(indexesTableView);
    }

    @FXML
    private void onDeleteIndex() {
        IndexRow indexRow = indexesTableView.getSelectionModel().getSelectedItem();
        if (indexRow != null) indexesTableView.getItems().remove(indexRow);
    }

    @FXML
    private void onAddForeignKey() {
        foreignKeysTableView.getItems().add(new ForeignKeyRow("", "fk_" + tableNameField.getText().trim(), "", "", "", ForeignKeyActionEnum.RESTRICT, ForeignKeyActionEnum.RESTRICT));
        editLastName(foreignKeysTableView);
    }

    @FXML
    private void onDeleteForeignKey() {
        ForeignKeyRow foreignKeyRow = foreignKeysTableView.getSelectionModel().getSelectedItem();
        if (foreignKeyRow != null) foreignKeysTableView.getItems().remove(foreignKeyRow);
    }

    @FXML
    private void onSave() {
        if (original == null) return;
        saveTable(false);
    }

    private void saveTable(boolean isConfirmed) {
        Task<String> saveTableTask = isNewTable ? getCreateTableTask(isConfirmed) : getAlterTableTask(isConfirmed);
        saveButton.setDisable(true);
        setStatus(__("Guardando..."));
        saveTableTask.setOnSucceeded(event -> {
            onSaved.accept(saveTableTask.getValue());
            ((Stage) closeButton.getScene().getWindow()).close();
        });
        saveTableTask.setOnFailed(event -> {
            saveButton.setDisable(false);
            Throwable throwable = saveTableTask.getException();
            if (throwable instanceof InterfaceException interfaceException && interfaceException.getCode() == ExceptionEnum.CODE_PRECONDITION_REQUIRED) {
                setStatus("");
                if (isConfirmed(throwable.getMessage() + ".\n\n" + __("¿Guardar igualmente?"))) saveTable(true);
                return;
            }
            setStatus(getErrorMessage(throwable));
            showError(throwable);
        });
        startDaemon(saveTableTask, "table-editor-save");
    }

    private Task<String> getCreateTableTask(boolean isConfirmed) {
        CreateTableDto createTableDto = CreateTableDto.fromPrimitives(Primitives.of(
            "connectionUuid", connectionUuid, "sessionKey", sessionKey, "databaseName", databaseName,
            "edited", getEditedDefinition(), "isConfirmed", isConfirmed
        ));
        return new Task<>() {
            @Override
            protected String call() throws SQLException {
                return CreateTableService.getInstance().invoke(createTableDto).getTableName();
            }
        };
    }

    private Task<String> getAlterTableTask(boolean isConfirmed) {
        AlterTableDto alterTableDto = AlterTableDto.fromPrimitives(Primitives.of(
            "connectionUuid", connectionUuid, "sessionKey", sessionKey, "databaseName", databaseName,
            "original", original, "edited", getEditedDefinition(), "isConfirmed", isConfirmed
        ));
        return new Task<>() {
            @Override
            protected String call() throws SQLException {
                return AlterTableService.getInstance().invoke(alterTableDto).getTableName();
            }
        };
    }

    @FXML
    private void onClose() {
        ((Stage) closeButton.getScene().getWindow()).close();
    }

    /**
     * La pestaña SQL: los errores que impedirian guardar o las sentencias que se van a ejecutar.
     */
    private void refreshSqlPreview() {
        if (original == null) return;
        TableDefinitionEntity edited = getEditedDefinition();
        TableAlterSqlBuilder tableAlterSqlBuilder = TableAlterSqlBuilder.getInstance();
        TableCreateSqlBuilder tableCreateSqlBuilder = TableCreateSqlBuilder.getInstance(dbEngine);
        List<String> errors = isNewTable ? tableCreateSqlBuilder.getErrors(edited) : tableAlterSqlBuilder.getErrors(edited);
        if (!errors.isEmpty()) {
            sqlTextArea.setText("-- " + __("No se puede guardar:") + "\n-- " + String.join("\n-- ", errors));
            return;
        }
        List<String> statements = isNewTable
            ? tableCreateSqlBuilder.getStatements(databaseName, edited)
            : tableAlterSqlBuilder.getStatements(databaseName, original, edited);
        sqlTextArea.setText(statements.isEmpty() ? "-- " + __("Sin cambios") : String.join(";\n\n", statements) + ";");
    }

    /**
     * La tabla tal como esta en el dialogo. La PK: si son las mismas columnas que antes, en su orden de antes (una PK
     * compuesta (b, a) no cambia por estar a al principio); si no, en el orden de las columnas.
     */
    private TableDefinitionEntity getEditedDefinition() {
        List<TableColumnEntity> columns = new ArrayList<>();
        Map<String, String> newNamesByOriginal = new HashMap<>();
        List<String> checkedPrimaryKey = new ArrayList<>();
        Set<String> checkedOriginalNames = new LinkedHashSet<>();
        for (ColumnRow columnRow : columnsTableView.getItems()) {
            TableColumnEntity column = columnRow.toEntity();
            columns.add(column);
            if (!column.isNew()) newNamesByOriginal.put(column.originalName(), column.name());
            if (columnRow.isPrimaryKey.get()) {
                checkedPrimaryKey.add(column.name());
                checkedOriginalNames.add(column.isNew() ? "\0" + column.name() : column.originalName());
            }
        }
        List<String> primaryKeyColumns = checkedPrimaryKey;
        if (checkedOriginalNames.equals(new HashSet<>(original.primaryKeyColumns()))) {
            primaryKeyColumns = new ArrayList<>();
            for (String originalName : original.primaryKeyColumns()) primaryKeyColumns.add(newNamesByOriginal.getOrDefault(originalName, originalName));
        }
        List<TableIndexEntity> indexes = new ArrayList<>();
        for (IndexRow indexRow : indexesTableView.getItems()) indexes.add(getIndexEntity(indexRow));
        List<TableForeignKeyEntity> foreignKeys = new ArrayList<>();
        for (ForeignKeyRow foreignKeyRow : foreignKeysTableView.getItems()) foreignKeys.add(getForeignKeyEntity(foreignKeyRow));
        return new TableDefinitionEntity(
            tableNameField.getText().trim(),
            columns,
            primaryKeyColumns,
            indexes,
            foreignKeys,
            getComboText(engineComboBox),
            getComboText(collationComboBox),
            commentField.getText(),
            autoIncrementField.getText().trim(),
            getComboText(rowFormatComboBox)
        );
    }

    private String getComboText(ComboBox<String> comboBox) {
        String text = comboBox.isEditable() ? comboBox.getEditor().getText() : comboBox.getValue();
        return text == null ? "" : text.trim();
    }

    private <S> void editLastName(TableView<S> tableView) {
        int lastIndex = tableView.getItems().size() - 1;
        tableView.getSelectionModel().select(lastIndex);
        tableView.scrollTo(lastIndex);
        tableView.edit(lastIndex, tableView.getColumns().getFirst());
    }

    private void setStatus(String status) {
        statusLabel.setText(status);
    }

    private void startDaemon(Task<?> backgroundTask, String threadName) {
        Thread daemonThread = new Thread(backgroundTask, threadName);
        daemonThread.setDaemon(true);
        daemonThread.start();
    }

    private <S> TableColumn<S, String> getTextColumn(String title, Function<S, StringProperty> property, double width, Predicate<S> isEditable) {
        TableColumn<S, String> tableColumn = new TableColumn<>(title);
        tableColumn.setCellValueFactory(cellData -> property.apply(cellData.getValue()));
        tableColumn.setCellFactory(column -> new CommitTextCell<>(isEditable));
        tableColumn.setPrefWidth(width);
        tableColumn.setSortable(false);
        return tableColumn;
    }

    private <S> TableColumn<S, Boolean> getCheckColumn(String title, Function<S, BooleanProperty> property) {
        TableColumn<S, Boolean> tableColumn = new TableColumn<>(title);
        tableColumn.setCellValueFactory(cellData -> property.apply(cellData.getValue()));
        tableColumn.setCellFactory(CheckBoxTableCell.forTableColumn(tableColumn));
        tableColumn.setPrefWidth(70);
        tableColumn.setSortable(false);
        return tableColumn;
    }

    private TableColumn<ForeignKeyRow, String> getActionColumn(String title, Function<ForeignKeyRow, StringProperty> property) {
        TableColumn<ForeignKeyRow, String> tableColumn = new TableColumn<>(title);
        tableColumn.setCellValueFactory(cellData -> property.apply(cellData.getValue()));
        tableColumn.setCellFactory(ComboBoxTableCell.forTableColumn(ForeignKeyActionEnum.VALUES.toArray(String[]::new)));
        tableColumn.setPrefWidth(110);
        tableColumn.setSortable(false);
        return tableColumn;
    }

    private TableIndexEntity getIndexEntity(IndexRow indexRow) {
        return new TableIndexEntity(indexRow.originalName, indexRow.name.get().trim(), indexRow.indexType.get(), getList(indexRow.columns.get()));
    }

    private TableForeignKeyEntity getForeignKeyEntity(ForeignKeyRow foreignKeyRow) {
        return new TableForeignKeyEntity(
            foreignKeyRow.originalName, foreignKeyRow.name.get().trim(), getList(foreignKeyRow.columns.get()),
            foreignKeyRow.referencedTable.get().trim(), getList(foreignKeyRow.referencedColumns.get()),
            foreignKeyRow.onDelete.get(), foreignKeyRow.onUpdate.get()
        );
    }

    private List<String> getList(String commaSeparated) {
        List<String> values = new ArrayList<>();
        for (String value : commaSeparated.split(",")) {
            if (!value.isBlank()) values.add(value.trim());
        }
        return values;
    }

    /**
     * Una celda de texto que guarda lo escrito con Intro y tambien al salir de ella (la de JavaFX lo pierde). Las
     * filas que no se pueden editar (columna calculada) no entran en edicion.
     */
    private static final class CommitTextCell<S> extends TableCell<S, String> {

        private final TextField textField = new TextField();
        private final Predicate<S> isEditable;

        private CommitTextCell(Predicate<S> isEditable) {
            this.isEditable = isEditable;
            textField.setOnAction(event -> commitEdit(textField.getText()));
            textField.focusedProperty().addListener((observable, wasFocused, isFocused) -> {
                if (!isFocused && isEditing()) commitEdit(textField.getText());
            });
            textField.setOnKeyPressed(keyEvent -> {
                if (keyEvent.getCode() == KeyCode.ESCAPE) cancelEdit();
            });
        }

        @Override
        public void startEdit() {
            S rowItem = getTableRow() == null ? null : getTableRow().getItem();
            if (rowItem == null || !isEditable.test(rowItem)) return;
            super.startEdit();
            textField.setText(getItem() == null ? "" : getItem());
            setText(null);
            setGraphic(textField);
            textField.requestFocus();
            textField.selectAll();
        }

        @Override
        public void cancelEdit() {
            super.cancelEdit();
            setText(getItem());
            setGraphic(null);
        }

        @Override
        protected void updateItem(String item, boolean isEmpty) {
            super.updateItem(item, isEmpty);
            if (isEmpty) {
                setText(null);
                setGraphic(null);
                return;
            }
            if (isEditing()) {
                textField.setText(item);
                setText(null);
                setGraphic(textField);
                return;
            }
            setText(item);
            setGraphic(null);
        }
    }

    /**
     * Una fila de la pestaña Columnas. Lo que no se ve (juego de caracteres, ON UPDATE) se conserva de la columna leida.
     */
    private static final class ColumnRow {

        private final String originalName;
        private final StringProperty name;
        private final StringProperty dataType;
        private final StringProperty length;
        private final StringProperty defaultSql;
        private final StringProperty comment;
        private final BooleanProperty isPrimaryKey;
        private final BooleanProperty isNotNull;
        private final BooleanProperty isUnsigned;
        private final BooleanProperty isAutoIncrement;
        private final BooleanProperty isZerofill;
        private final String onUpdateSql;
        private final String characterSet;
        private final String collation;
        private final boolean isGenerated;
        private final TableColumnEntity readColumn;

        private ColumnRow(TableColumnEntity column, boolean isPrimaryKey) {
            this.readColumn = column;
            this.originalName = column.originalName();
            this.name = new SimpleStringProperty(column.name());
            this.dataType = new SimpleStringProperty(column.dataType());
            this.length = new SimpleStringProperty(column.length());
            this.defaultSql = new SimpleStringProperty(column.defaultSql());
            this.comment = new SimpleStringProperty(column.comment());
            this.isPrimaryKey = new SimpleBooleanProperty(isPrimaryKey);
            this.isNotNull = new SimpleBooleanProperty(column.isNotNull());
            this.isUnsigned = new SimpleBooleanProperty(column.isUnsigned());
            this.isAutoIncrement = new SimpleBooleanProperty(column.isAutoIncrement());
            this.isZerofill = new SimpleBooleanProperty(column.isZerofill());
            this.onUpdateSql = column.onUpdateSql();
            this.characterSet = column.characterSet();
            this.collation = column.collation();
            this.isGenerated = column.isGenerated();
        }

        private static ColumnRow of(TableColumnEntity column, boolean isPrimaryKey) {
            return new ColumnRow(column, isPrimaryKey);
        }

        private static ColumnRow ofNew() {
            return new ColumnRow(new TableColumnEntity("", "", "varchar", "255", false, false, false, false, "", "", "", "", "", false), false);
        }

        /**
         * Una columna calculada sale tal como se leyo: su expresion no se rehace.
         */
        private TableColumnEntity toEntity() {
            if (isGenerated) return readColumn;
            return new TableColumnEntity(
                originalName, name.get().trim(), dataType.get().trim().toLowerCase(Locale.ROOT), length.get().trim(),
                isUnsigned.get(), isZerofill.get(), isNotNull.get() || isPrimaryKey.get(), isAutoIncrement.get(),
                defaultSql.get().trim(), onUpdateSql, characterSet, collation, comment.get(), false
            );
        }
    }

    private static final class IndexRow {

        private final String originalName;
        private final StringProperty name;
        private final StringProperty indexType;
        private final StringProperty columns;

        private IndexRow(String originalName, String name, String indexType, String columns) {
            this.originalName = originalName;
            this.name = new SimpleStringProperty(name);
            this.indexType = new SimpleStringProperty(indexType);
            this.columns = new SimpleStringProperty(columns);
        }

        private static IndexRow of(TableIndexEntity index) {
            return new IndexRow(index.originalName(), index.name(), index.indexType(), String.join(", ", index.columns()));
        }
    }

    private static final class ForeignKeyRow {

        private final String originalName;
        private final StringProperty name;
        private final StringProperty columns;
        private final StringProperty referencedTable;
        private final StringProperty referencedColumns;
        private final StringProperty onDelete;
        private final StringProperty onUpdate;

        private ForeignKeyRow(String originalName, String name, String columns, String referencedTable, String referencedColumns, String onDelete, String onUpdate) {
            this.originalName = originalName;
            this.name = new SimpleStringProperty(name);
            this.columns = new SimpleStringProperty(columns);
            this.referencedTable = new SimpleStringProperty(referencedTable);
            this.referencedColumns = new SimpleStringProperty(referencedColumns);
            this.onDelete = new SimpleStringProperty(onDelete);
            this.onUpdate = new SimpleStringProperty(onUpdate);
        }

        private static ForeignKeyRow of(TableForeignKeyEntity foreignKey) {
            return new ForeignKeyRow(
                foreignKey.originalName(), foreignKey.name(), String.join(", ", foreignKey.columns()), foreignKey.referencedTable(),
                String.join(", ", foreignKey.referencedColumns()), foreignKey.onDelete(), foreignKey.onUpdate()
            );
        }
    }
}
