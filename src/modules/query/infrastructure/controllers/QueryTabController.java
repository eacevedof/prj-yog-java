package modules.query.infrastructure.controllers;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.StackPane;

import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;

import modules.shared.domain.enums.ExceptionEnum;
import modules.shared.domain.enums.SessionKeyEnum;
import modules.shared.domain.services.SqlDialect;
import modules.shared.application.Primitives;
import modules.shared.infrastructure.components.icons.AppIconEnum;
import modules.shared.infrastructure.components.fonts.FontsComponent;
import modules.shared.infrastructure.components.icons.AppIcon;
import modules.shared.infrastructure.components.theme.ThemeComponent;
import modules.shared.infrastructure.controllers.AbstractFxController;
import static modules.shared.application.Primitives.getBool;
import static modules.shared.application.Primitives.getInt;
import static modules.shared.application.Primitives.getTrimmedString;

import static core.config.Global.__;

import modules.connections.infrastructure.factories.TargetSessionFactory;

import modules.query.domain.enums.QueryKeyEnum;
import modules.query.domain.entities.SelectSourceTableEntity;
import modules.query.domain.entities.SqlStatementEntity;
import modules.query.domain.services.EditableSelectDetector;
import modules.query.domain.services.SqlStatementSplitter;
import modules.query.application.cancelquery.CancelQueryDto;
import modules.query.application.executequery.ExecuteQueryDto;
import modules.query.application.executequery.ExecuteQueryResultDto;
import modules.query.application.cancelquery.CancelQueryService;
import modules.query.application.executequery.ExecuteQueryService;
import modules.query.infrastructure.components.CompletionPopupComponent;
import modules.query.infrastructure.components.ResultGridPane;
import modules.query.infrastructure.components.ResultTextFormatter;
import modules.query.infrastructure.components.SchemaCacheComponent;
import modules.query.infrastructure.components.SqlHighlighter;
import modules.query.domain.exceptions.QueryException;

/**
 * Una pestaña de query: editor con resaltado, ejecutar la sentencia actual (F9, Ctrl+Enter) o todas (Ctrl+F9) en
 * segundo plano con Cancelar, y el resultado en rejillas (una por result set), en texto plano y en Mensajes.
 * La pestaña de conexion le da su sesion, la BD activa y que hacer con Ctrl+T / Ctrl+W.
 */
public final class QueryTabController extends AbstractFxController {

    private static final int DEFAULT_MAX_ROWS = 1000;
    private static final int MAX_SQL_IN_MESSAGES = 300;

    @FXML
    private Button executeButton;
    @FXML
    private Button executeAllButton;
    @FXML
    private Button cancelButton;
    @FXML
    private TextField maxRowsField;
    @FXML
    private Label statusLabel;
    @FXML
    private StackPane editorContainer;
    @FXML
    private TabPane resultTabPane;
    @FXML
    private Tab textTab;
    @FXML
    private Tab messagesTab;
    @FXML
    private Tab historyTab;
    @FXML
    private QueryHistoryController queryHistoryController;
    @FXML
    private TextArea resultTextArea;
    @FXML
    private TextArea messagesTextArea;

    private final CodeArea sqlCodeArea = new CodeArea();

    private String connectionUuid = "";
    private String dbEngine = SqlDialect.MYSQL;
    private String sessionKey = "";
    private Supplier<String> activeDatabaseSupplier = () -> "";
    private Consumer<String> onActiveDatabaseChanged = databaseName -> { };
    private Runnable onNewTab = () -> { };
    private Runnable onCloseTab = () -> { };
    private String openedSql = "";
    private boolean isRunning;
    private SchemaCacheComponent schemaCache;
    private String gridSearchText = "";
    private final Set<String> hiddenColumnNames = new HashSet<>();
    // tras guardar los cambios de la rejilla se vuelve a ejecutar el SELECT: esto se ensena al acabar
    private String statusAfterReload = "";

    @FXML
    private void initialize() {
        sqlCodeArea.getStyleClass().add("sql-editor");
        sqlCodeArea.setStyle(FontsComponent.SQL_EDITOR_FONT_STYLE);
        sqlCodeArea.setParagraphGraphicFactory(LineNumberFactory.get(sqlCodeArea));
        sqlCodeArea.textProperty().addListener((observable, oldText, newText) -> {
            sqlCodeArea.setStyleSpans(0, SqlHighlighter.getInstance().getStyleSpans(newText));
        });
        sqlCodeArea.addEventFilter(KeyEvent.KEY_PRESSED, this::onEditorKeyPressed);
        installDropFromTree();
        editorContainer.getChildren().add(new VirtualizedScrollPane<>(sqlCodeArea));
        CompletionPopupComponent.getInstance(sqlCodeArea, () -> schemaCache, () -> activeDatabaseSupplier.get(), () -> dbEngine).install();
        historyTab.selectedProperty().addListener((observable, wasSelected, isSelected) -> {
            if (isSelected) queryHistoryController.refresh();
        });
    }

    /**
     * Lo llama la pestaña de conexion justo despues de cargar el .fxml.
     */
    public void setup(
        String connectionUuid,
        String sessionKey,
        Supplier<String> activeDatabaseSupplier,
        Consumer<String> onActiveDatabaseChanged,
        Runnable onNewTab,
        Runnable onCloseTab
    ) {
        this.connectionUuid = connectionUuid;
        this.sessionKey = sessionKey;
        this.activeDatabaseSupplier = activeDatabaseSupplier;
        this.onActiveDatabaseChanged = onActiveDatabaseChanged;
        this.dbEngine = TargetSessionFactory.getInstance().getDbEngine(connectionUuid);
        this.onNewTab = onNewTab;
        this.onCloseTab = onCloseTab;
        queryHistoryController.setup(connectionUuid, dbEngine, this::pasteFromHistory);
    }

    /**
     * La cache de esquema de la conexion para el autocompletado (una por pestaña de conexion, compartida).
     */
    public void setSchemaCache(SchemaCacheComponent schemaCache) {
        this.schemaCache = schemaCache;
    }

    public String getSessionKey() {
        return sessionKey;
    }

    public boolean isRunning() {
        return isRunning;
    }

    public void focusEditor() {
        sqlCodeArea.requestFocus();
    }

    /**
     * Lo que se arrastra desde el arbol (el nombre de una BD, tabla o columna) se suelta donde esta el raton, con el
     * cursor siguiendolo mientras se arrastra.
     */
    private void installDropFromTree() {
        sqlCodeArea.setOnDragOver(dragEvent -> {
            if (dragEvent.getGestureSource() == sqlCodeArea || !dragEvent.getDragboard().hasString()) return;
            dragEvent.acceptTransferModes(TransferMode.COPY);
            sqlCodeArea.displaceCaret(sqlCodeArea.hit(dragEvent.getX(), dragEvent.getY()).getInsertionIndex());
            dragEvent.consume();
        });
        sqlCodeArea.setOnDragDropped(dragEvent -> {
            if (!dragEvent.getDragboard().hasString()) return;
            int insertionIndex = sqlCodeArea.hit(dragEvent.getX(), dragEvent.getY()).getInsertionIndex();
            sqlCodeArea.insertText(insertionIndex, dragEvent.getDragboard().getString());
            sqlCodeArea.moveTo(insertionIndex + dragEvent.getDragboard().getString().length());
            sqlCodeArea.requestFocus();
            dragEvent.setDropCompleted(true);
            dragEvent.consume();
        });
    }

    public void insertText(String text) {
        sqlCodeArea.replaceSelection(text);
        sqlCodeArea.requestFocus();
    }

    /**
     * Desde el arbol (SELECT de una tabla, SHOW CREATE de un objeto): pone el SQL y lo ejecuta.
     */
    public void setSqlAndExecute(String sql) {
        sqlCodeArea.replaceText(sql);
        sqlCodeArea.moveTo(0);
        execute(sql);
    }

    public String getSql() {
        return sqlCodeArea.getText();
    }

    /**
     * Al recuperar una pestaña guardada: su SQL, sin ejecutarlo y sin que cuente como algo que deshacer.
     */
    public void setSql(String sql) {
        sqlCodeArea.replaceText(sql);
        openedSql = sql;
        sqlCodeArea.moveTo(0);
        sqlCodeArea.getUndoManager().forgetHistory();
    }

    /**
     * Si el SQL ya no es el que tenia al abrirse (vacio en una nueva, el recuperado en una guardada): "Cerrar las no
     * modificadas" deja las que si.
     */
    public boolean isModified() {
        return !getSql().equals(openedSql);
    }

    /**
     * Cada cambio del SQL (para guardar las pestañas, con retardo, quien la crea).
     */
    public void setOnSqlChanged(Runnable onSqlChanged) {
        sqlCodeArea.textProperty().addListener((observable, oldSql, newSql) -> onSqlChanged.run());
    }

    @FXML
    private void onExecuteCurrent() {
        String selectedText = sqlCodeArea.getSelectedText();
        if (!selectedText.isBlank()) {
            execute(selectedText);
            return;
        }
        SqlStatementEntity statementAtCaret = SqlStatementSplitter.getInstance(dbEngine)
            .getStatementAt(sqlCodeArea.getText(), sqlCodeArea.getCaretPosition());
        if (statementAtCaret == null) return;
        sqlCodeArea.selectRange(statementAtCaret.getStartIndex(), statementAtCaret.getStartIndex() + statementAtCaret.getSql().length());
        execute(statementAtCaret.getSql());
    }

    @FXML
    private void onExecuteAll() {
        execute(sqlCodeArea.getText());
    }

    /**
     * Feature 8: el historial es una pestaña del resultado (como History en SQLyog): Ctrl+H o el boton la ensenan.
     */
    @FXML
    private void onShowHistory() {
        if (historyTab.isSelected()) queryHistoryController.refresh();
        resultTabPane.getSelectionModel().select(historyTab);
        queryHistoryController.focusSearch();
    }

    /**
     * Vacia el editor de la pestaña. Pasa por el undo: Ctrl+Z lo recupera.
     */
    @FXML
    private void onClearEditor() {
        sqlCodeArea.replaceText("");
        sqlCodeArea.requestFocus();
    }

    /**
     * El SQL elegido en el historial se inserta donde esta el cursor (en un editor vacio, lo sustituye).
     */
    private void pasteFromHistory(String sql) {
        if (sqlCodeArea.getText().isBlank()) {
            sqlCodeArea.replaceText(sql);
            return;
        }
        insertText(sql);
    }

    @FXML
    private void onCancel() {
        cancel();
    }

    /**
     * Corta lo que este en marcha (tambien al cerrar la pestaña). No hace nada si no hay nada ejecutandose.
     */
    public void cancel() {
        if (!isRunning) return;
        try {
            CancelQueryService.getInstance().invoke(CancelQueryDto.fromPrimitives(Primitives.of("sessionKey", sessionKey)));
            statusLabel.setText(__("Cancelando..."));
        }
        catch (SQLException | RuntimeException e) {
            showError(e);
        }
    }

    private void onEditorKeyPressed(KeyEvent keyEvent) {
        boolean isCtrl = keyEvent.isShortcutDown();
        KeyCode keyCode = keyEvent.getCode();
        if (keyCode == KeyCode.F9 && isCtrl) {
            onExecuteAll();
        }
        else if (keyCode == KeyCode.F9 || (keyCode == KeyCode.ENTER && isCtrl)) {
            onExecuteCurrent();
        }
        else if (keyCode == KeyCode.H && isCtrl) {
            onShowHistory();
        }
        else if (keyCode == KeyCode.T && isCtrl) {
            onNewTab.run();
        }
        else if (keyCode == KeyCode.W && isCtrl) {
            onCloseTab.run();
        }
        else {
            return;
        }
        keyEvent.consume();
    }

    /**
     * Ejecutar de nuevo sustituye las rejillas: si alguna tiene cambios sin guardar, se pregunta antes.
     */
    private void execute(String sql) {
        if (isRunning || sql.isBlank()) return;
        int changeCount = getPendingChangeCount();
        if (changeCount > 0 && !isConfirmed(__("El resultado tiene {0} cambio(s) sin guardar. ¿Descartarlos y ejecutar?", changeCount))) {
            return;
        }
        statusAfterReload = "";
        execute(sql, false);
    }

    /**
     * Los cambios sin guardar de las rejillas editables del resultado (SELECT de una sola tabla).
     */
    public boolean hasPendingChanges() {
        return getPendingChangeCount() > 0;
    }

    private int getPendingChangeCount() {
        return getEditableResultControllers().stream().mapToInt(EditableResultController::getChangeCount).sum();
    }

    private List<EditableResultController> getEditableResultControllers() {
        return resultTabPane.getTabs().stream()
            .filter(tab -> tab.getContent() instanceof EditableResultController)
            .map(tab -> (EditableResultController) tab.getContent())
            .toList();
    }

    /**
     * Despues de guardar o cancelar los cambios de una rejilla: vuelve a lanzar su SELECT, sin preguntar.
     */
    private void reload(String sql, String statusMessage) {
        if (isRunning) return;
        statusAfterReload = statusMessage;
        execute(sql, false);
    }

    /**
     * isConfirmed: el usuario ya acepto las sentencias destructivas de una conexion de produccion (feature 44).
     */
    private void execute(String sql, boolean isConfirmed) {
        if (isRunning || sql.isBlank()) return;

        ExecuteQueryDto executeQueryDto = ExecuteQueryDto.fromPrimitives(Primitives.of(
            "connectionUuid", connectionUuid,
            "sessionKey", sessionKey,
            "databaseName", activeDatabaseSupplier.get(),
            "sqlQuery", sql,
            "maxRows", getMaxRows(),
            "isConfirmed", isConfirmed
        ));
        Task<ExecuteQueryResultDto> executeQueryTask = new Task<>() {
            @Override
            protected ExecuteQueryResultDto call() {
                return ExecuteQueryService.getInstance().invoke(executeQueryDto);
            }
        };
        executeQueryTask.setOnSucceeded(event -> {
            setRunning(false);
            showResults(executeQueryTask.getValue());
        });
        executeQueryTask.setOnFailed(event -> {
            setRunning(false);
            Throwable exception = executeQueryTask.getException();
            if (exception instanceof QueryException queryException && queryException.getCode() == ExceptionEnum.CODE_PRECONDITION_REQUIRED) {
                if (isProductionConfirmed(queryException.getMessage())) {
                    execute(sql, true);
                    return;
                }
                statusLabel.setText(__("No ejecutado"));
                return;
            }
            clearGridTabs();
            resultTextArea.clear();
            messagesTextArea.setText(getErrorMessage(executeQueryTask.getException()));
            resultTabPane.getSelectionModel().select(messagesTab);
            statusLabel.setText(__("Error"));
        });

        setRunning(true);
        statusLabel.setText(__("Ejecutando..."));
        Thread executeQueryThread = new Thread(executeQueryTask, "execute-query");
        executeQueryThread.setDaemon(true);
        executeQueryThread.start();
    }

    /**
     * Aviso de produccion: el boton por defecto es Cancelar, asi un Enter por inercia no lanza el DELETE.
     */
    private boolean isProductionConfirmed(String message) {
        ButtonType executeButtonType = new ButtonType(__("Ejecutar en producción"), ButtonBar.ButtonData.OK_DONE);
        Alert alert = new Alert(Alert.AlertType.WARNING, message, executeButtonType, ButtonType.CANCEL);
        ThemeComponent.getInstance().applyTo(alert);
        alert.setTitle(__("Conexión de producción"));
        alert.setHeaderText(__("Sentencias destructivas en PRODUCCIÓN"));
        ((Button) alert.getDialogPane().lookupButton(executeButtonType)).setDefaultButton(false);
        ((Button) alert.getDialogPane().lookupButton(ButtonType.CANCEL)).setDefaultButton(true);
        alert.getDialogPane().getStyleClass().add("production-alert");
        return alert.showAndWait().filter(executeButtonType::equals).isPresent();
    }

    private int getMaxRows() {
        String maxRowsText = maxRowsField.getText().trim();
        int maxRows = getInt(maxRowsText);
        if (maxRowsText.isEmpty() || maxRows < 0) {
            maxRowsField.setText(String.valueOf(DEFAULT_MAX_ROWS));
            return DEFAULT_MAX_ROWS;
        }
        return maxRows;
    }

    private void setRunning(boolean isRunning) {
        this.isRunning = isRunning;
        executeButton.setDisable(isRunning);
        executeAllButton.setDisable(isRunning);
        cancelButton.setDisable(!isRunning);
    }

    @SuppressWarnings("unchecked")
    private void showResults(ExecuteQueryResultDto executeQueryResultDto) {
        clearGridTabs();
        StringBuilder resultText = new StringBuilder();
        StringBuilder messagesText = new StringBuilder();
        ResultTextFormatter resultTextFormatter = ResultTextFormatter.getInstance();
        boolean hasError = false;
        int resultSetNumber = 0;
        int totalRows = 0;

        for (Map<String, Object> result : executeQueryResultDto.getResults()) {
            String sql = getTrimmedString(result.get(QueryKeyEnum.SQL));
            messagesText.append(__("Query: {0}", getShortSql(sql))).append('\n');

            String errorMessage = getTrimmedString(result.get(QueryKeyEnum.ERROR_MESSAGE));
            if (!errorMessage.isEmpty()) {
                hasError = true;
                messagesText.append(errorMessage).append("\n\n");
                continue;
            }

            if (getBool(result.get(QueryKeyEnum.HAS_RESULT_SET))) {
                resultSetNumber++;
                List<String> columnNames = (List<String>) result.get(QueryKeyEnum.COLUMN_NAMES);
                List<List<String>> rows = (List<List<String>>) result.get(QueryKeyEnum.ROWS);
                totalRows += rows.size();
                addGridTab(resultSetNumber, sql, executeQueryResultDto.getCurrentDatabase(), columnNames, rows);
                resultText.append(sql).append('\n').append(resultTextFormatter.getTable(columnNames, rows))
                    .append(__("{0} fila(s)", rows.size())).append("\n\n");
                messagesText.append(__("{0} fila(s) devueltas", rows.size()));
                if (getBool(result.get(QueryKeyEnum.IS_TRUNCATED))) {
                    messagesText.append(" ").append(__("(cortado en Máx. filas)"));
                }
            }
            else {
                messagesText.append(__("{0} fila(s) afectadas", getInt(result.get(QueryKeyEnum.AFFECTED_ROWS))));
            }
            messagesText.append('\n').append(__("Tiempo: {0} ms", getTrimmedString(result.get(QueryKeyEnum.ELAPSED_MS)))).append("\n\n");
        }
        if (executeQueryResultDto.isCancelled()) {
            messagesText.append(__("Cancelado por el usuario")).append('\n');
        }

        resultTextArea.setText(resultText.toString());
        messagesTextArea.setText(messagesText.toString());
        if (hasError || executeQueryResultDto.isCancelled() || resultSetNumber == 0) {
            resultTabPane.getSelectionModel().select(messagesTab);
        }
        else {
            resultTabPane.getSelectionModel().selectFirst();
        }

        String status = hasError ? __("Error") : executeQueryResultDto.isCancelled() ? __("Cancelado") : __("{0} fila(s)", totalRows);
        String reloadPrefix = statusAfterReload.isEmpty() ? "" : statusAfterReload + " · ";
        statusAfterReload = "";
        statusLabel.setText(reloadPrefix + status + " · " + executeQueryResultDto.getElapsedMs() + " ms");
        onActiveDatabaseChanged.accept(executeQueryResultDto.getCurrentDatabase());
    }

    private String getShortSql(String sql) {
        String oneLine = sql.replaceAll("\\s+", " ");
        return oneLine.length() <= MAX_SQL_IN_MESSAGES ? oneLine : oneLine.substring(0, MAX_SQL_IN_MESSAGES) + "...";
    }

    private void clearGridTabs() {
        resultTabPane.getTabs().removeIf(tab -> tab != textTab && tab != messagesTab && tab != historyTab);
    }

    /**
     * Una rejilla por result set, delante de Texto y Mensajes. El texto buscado y las columnas ocultas se guardan en la
     * pestaña: al volver a ejecutar, la rejilla nueva sale igual filtrada (feature 42).
     * Si el SELECT es de una sola tabla, se edita como en SQLyog (EditableResultController), en la sesion de editar de la
     * pestaña ("<sesion>:edit", se cierra con ella): asi guardar no se mezcla con una transaccion abierta a mano.
     */
    private void addGridTab(int resultSetNumber, String sql, String currentDatabase, List<String> columnNames, List<List<String>> rows) {
        // copias mutables: la rejilla editable cambia las filas en sitio
        List<List<String>> gridRows = new ArrayList<>(rows.size());
        for (List<String> row : rows) {
            gridRows.add(new ArrayList<>(row));
        }
        ResultGridPane resultGridPane = ResultGridPane.getInstance(columnNames, gridRows, gridSearchText, hiddenColumnNames);
        resultGridPane.setOnSearchChanged(searchText -> gridSearchText = searchText);
        resultGridPane.setOnHiddenColumnsChanged(this::updateHiddenColumnNames);
        resultGridPane.setDefaultSupported(!SqlDialect.SQLITE.equals(dbEngine));

        EditableResultController editableResultController = EditableResultController.getInstance(resultGridPane);
        editableResultController.setOnReload(statusMessage -> reload(sql, statusMessage));
        SelectSourceTableEntity selectSourceTableEntity = EditableSelectDetector.getInstance().getSourceTable(sql);
        if (selectSourceTableEntity != null) {
            String databaseName = selectSourceTableEntity.getDatabaseName();
            if (databaseName.isEmpty()) databaseName = getTrimmedString(currentDatabase);
            if (databaseName.isEmpty()) databaseName = getTrimmedString(activeDatabaseSupplier.get());
            String editSessionKey = sessionKey + SessionKeyEnum.SEPARATOR + SessionKeyEnum.EDIT;
            editableResultController.enableEditingFor(connectionUuid, editSessionKey, databaseName, selectSourceTableEntity.getTableName(), columnNames);
        }

        Tab gridTab = new Tab(resultSetNumber == 1 ? __("Resultado") : __("Resultado {0}", resultSetNumber), editableResultController);
        gridTab.setGraphic(AppIcon.of(AppIconEnum.TABLE));
        resultTabPane.getTabs().add(resultSetNumber - 1, gridTab);
    }

    /**
     * Una columna que se muestra de nuevo deja de estar oculta; las de otros resultados que no estan en esta rejilla
     * se conservan.
     */
    private void updateHiddenColumnNames(Set<String> gridHiddenColumnNames) {
        hiddenColumnNames.addAll(gridHiddenColumnNames);
        for (EditableResultController editableResultController : getEditableResultControllers()) {
            editableResultController.getResultGridPane().getTableView().getColumns().stream()
                .filter(column -> column.isVisible())
                .forEach(column -> hiddenColumnNames.remove(String.valueOf(column.getUserData())));
        }
    }
}
