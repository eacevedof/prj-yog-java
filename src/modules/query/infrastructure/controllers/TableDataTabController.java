package modules.query.infrastructure.controllers;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;

import modules.shared.domain.services.SqlDialect;
import modules.shared.application.Primitives;
import modules.shared.infrastructure.controllers.AbstractFxController;
import static modules.shared.application.Primitives.getBool;
import static modules.shared.application.Primitives.getInt;
import static modules.shared.application.Primitives.getTrimmedString;

import static core.config.Global.__;

import modules.connections.infrastructure.factories.TargetSessionFactory;

import modules.explorer.domain.enums.ExplorerKeyEnum;
import modules.explorer.application.gettableinfo.GetTableInfoDto;
import modules.explorer.application.listtablecolumns.ListTableColumnsDto;
import modules.explorer.application.gettableinfo.GetTableInfoService;
import modules.explorer.application.listtablecolumns.ListTableColumnsService;

import modules.query.domain.enums.QueryKeyEnum;
import modules.query.domain.enums.TableColumnMetaEnum;
import modules.query.domain.services.TableDataQueryBuilder;
import modules.query.application.executequery.ExecuteQueryDto;
import modules.query.application.executequery.ExecuteQueryResultDto;
import modules.query.application.executequery.ExecuteQueryService;
import modules.query.infrastructure.components.ResultGridPane;

/**
 * La pestaña Datos de una tabla o vista (feature 7, F11 en SQLyog): SELECT * con filtro WHERE, ORDER BY y limite que
 * se conservan mientras la pestaña este abierta. Enter en cualquier campo o F5 recarga. Lleva su propia sesion.
 * Feature 6: si es una tabla con clave primaria, la rejilla se edita y los cambios se guardan juntos (Ctrl+S) en una
 * transaccion (TableGridEditController, el mismo del resultado de un SELECT); BLOB, BIT y binarios quedan de solo lectura.
 */
public final class TableDataTabController extends AbstractFxController {

    private static final int DEFAULT_LIMIT = 1000;

    @FXML
    private Label tableNameLabel;
    @FXML
    private TextField whereField;
    @FXML
    private TextField orderByField;
    @FXML
    private TextField limitField;
    @FXML
    private Button refreshButton;
    @FXML
    private HBox editBar;
    @FXML
    private Button saveButton;
    @FXML
    private Button discardButton;
    @FXML
    private Label editStatusLabel;
    @FXML
    private StackPane gridContainer;
    @FXML
    private Label sqlLabel;
    @FXML
    private Label statusLabel;

    private String connectionUuid = "";
    private String dbEngine = SqlDialect.MYSQL;
    private String sessionKey = "";
    private String databaseName = "";
    private String tableName = "";
    private String gridSearchText = "";
    private final Set<String> hiddenColumnNames = new HashSet<>();
    private ResultGridPane resultGridPane;
    private TableGridEditController tableGridEditController;

    /**
     * Lo que trae una carga: el resultado del SELECT y las columnas de la tabla (para saber si se puede editar).
     */
    private record LoadedData(String selectSql, ExecuteQueryResultDto executeQueryResultDto, List<Map<String, Object>> tableColumns) {
    }

    @FXML
    private void initialize() {
        for (TextField textField : List.of(whereField, orderByField, limitField)) {
            textField.setOnAction(actionEvent -> onRefresh());
        }
        gridContainer.setOnKeyPressed(keyEvent -> {
            if (keyEvent.getCode() == KeyCode.F5) {
                onRefresh();
                keyEvent.consume();
            }
            else if (keyEvent.getCode() == KeyCode.S && keyEvent.isShortcutDown()) {
                onSave();
                keyEvent.consume();
            }
        });
        setEditBarVisible(false);
    }

    public void setup(String connectionUuid, String sessionKey, String databaseName, String tableName) {
        this.connectionUuid = connectionUuid;
        this.sessionKey = sessionKey;
        this.databaseName = databaseName;
        this.dbEngine = TargetSessionFactory.getInstance().getDbEngine(connectionUuid);
        this.tableName = tableName;
        tableNameLabel.setText(databaseName + "." + tableName);
    }

    public String getSessionKey() {
        return sessionKey;
    }

    public boolean isTable(String databaseName, String tableName) {
        return this.databaseName.equals(databaseName) && this.tableName.equals(tableName);
    }

    public boolean hasPendingChanges() {
        return tableGridEditController != null && tableGridEditController.hasPendingChanges();
    }

    /**
     * Recarga; si hay cambios sin guardar, pregunta antes de perderlos.
     */
    @FXML
    public void onRefresh() {
        if (hasPendingChanges() && !isConfirmed(__("Hay {0} cambio(s) sin guardar. ¿Descartarlos y recargar?", tableGridEditController.getChangeCount()))) {
            return;
        }
        String whereFilter = whereField.getText();
        String orderBy = orderByField.getText();
        int limit = getLimit();
        ListTableColumnsDto listTableColumnsDto = ListTableColumnsDto.fromPrimitives(Primitives.of(
            "connectionUuid", connectionUuid,
            "sessionKey", sessionKey,
            "databaseName", databaseName,
            "tableName", tableName
        ));
        // las columnas antes que las filas: sin ORDER BY se ordena por la clave primaria (TableDataQueryBuilder)
        Task<LoadedData> loadTask = new Task<>() {
            @Override
            protected LoadedData call() throws SQLException {
                List<Map<String, Object>> tableColumns = ListTableColumnsService.getInstance().invoke(listTableColumnsDto).getColumns();
                String selectSql = TableDataQueryBuilder.getInstance(dbEngine).getSelectSqlOrderedByKey(
                    databaseName, tableName, whereFilter, orderBy, limit, getPrimaryKeyColumns(tableColumns)
                );
                ExecuteQueryDto executeQueryDto = ExecuteQueryDto.fromPrimitives(Primitives.of(
                    "connectionUuid", connectionUuid,
                    "sessionKey", sessionKey,
                    "databaseName", databaseName,
                    "sqlQuery", selectSql,
                    "maxRows", limit
                ));
                return new LoadedData(selectSql, ExecuteQueryService.getInstance().invoke(executeQueryDto), tableColumns);
            }
        };
        loadTask.setOnSucceeded(event -> {
            refreshButton.setDisable(false);
            sqlLabel.setText(loadTask.getValue().selectSql().replace('\n', ' '));
            showResult(loadTask.getValue());
        });
        loadTask.setOnFailed(event -> {
            refreshButton.setDisable(false);
            statusLabel.setText(getErrorMessage(loadTask.getException()).lines().findFirst().orElse(__("Error")));
        });

        refreshButton.setDisable(true);
        statusLabel.setText(__("Cargando..."));
        Thread loadThread = new Thread(loadTask, "table-data");
        loadThread.setDaemon(true);
        loadThread.start();
    }

    @FXML
    private void onAddRow() {
        if (resultGridPane != null) resultGridPane.addNewRow();
    }

    @FXML
    private void onDuplicateRow() {
        if (resultGridPane != null) resultGridPane.duplicateSelectedRow();
    }

    @FXML
    private void onDeleteRows() {
        if (resultGridPane != null) resultGridPane.deleteSelectedRows();
    }

    @FXML
    private void onSetNull() {
        if (resultGridPane != null) resultGridPane.setSelectedCellsNull();
    }

    @FXML
    private void onDiscard() {
        if (!hasPendingChanges()) return;
        tableGridEditController = null;
        onRefresh();
    }

    /**
     * Submenu Filtro de la rejilla: como SQLyog, en el servidor; la condicion pasa al WHERE y se recarga. Con cambios
     * sin guardar no se recarga (se perderian).
     */
    private void applyColumnFilter(String columnName, String operator, String value) {
        if (isReloadBlockedByChanges()) return;
        whereField.setText(TableDataQueryBuilder.getInstance(dbEngine).getColumnCondition(columnName, operator, value));
        onRefresh();
    }

    private void resetColumnFilter() {
        if (isReloadBlockedByChanges()) return;
        whereField.clear();
        onRefresh();
    }

    private boolean isReloadBlockedByChanges() {
        if (!hasPendingChanges()) return false;
        statusLabel.setText(__("Guarda o cancela los cambios antes de filtrar"));
        return true;
    }

    @FXML
    private void onSave() {
        if (!hasPendingChanges()) return;
        saveButton.setDisable(true);
        editStatusLabel.setText(__("Guardando..."));
        tableGridEditController.save(new TableGridEditController.SaveHandler() {
            @Override
            public void onSaved(int affectedRows) {
                tableGridEditController = null;
                onRefresh();
                statusLabel.setText(__("Guardado: {0} fila(s)", affectedRows));
            }

            @Override
            public void onFinished() {
                saveButton.setDisable(false);
                updateEditStatus();
            }
        });
    }

    @SuppressWarnings("unchecked")
    private void showResult(LoadedData loadedData) {
        ExecuteQueryResultDto executeQueryResultDto = loadedData.executeQueryResultDto();
        Map<String, Object> result = executeQueryResultDto.getResults().isEmpty() ? Map.of() : executeQueryResultDto.getResults().getFirst();
        String errorMessage = getTrimmedString(result.get(QueryKeyEnum.ERROR_MESSAGE));
        tableGridEditController = null;
        if (!errorMessage.isEmpty() || !getBool(result.get(QueryKeyEnum.HAS_RESULT_SET))) {
            gridContainer.getChildren().clear();
            resultGridPane = null;
            setEditBarVisible(false);
            statusLabel.setText(errorMessage.isEmpty() ? __("Sin resultado") : errorMessage);
            return;
        }

        List<String> columnNames = (List<String>) result.get(QueryKeyEnum.COLUMN_NAMES);
        // copias mutables: la rejilla editable cambia las filas en sitio
        List<List<String>> rows = new ArrayList<>();
        for (List<String> row : (List<List<String>>) result.get(QueryKeyEnum.ROWS)) {
            rows.add(new ArrayList<>(row));
        }
        resultGridPane = ResultGridPane.getInstance(columnNames, rows, gridSearchText, hiddenColumnNames);
        resultGridPane.setOnSearchChanged(searchText -> gridSearchText = searchText);
        resultGridPane.setOnSaveRequested(this::onSave);
        resultGridPane.setOnDiscardRequested(this::onDiscard);
        resultGridPane.setOnColumnFilter(this::applyColumnFilter, this::resetColumnFilter);
        resultGridPane.setExportTableName(tableName);
        resultGridPane.setCreateTableSqlLoader(this::getCreateTableSql);
        resultGridPane.setDefaultSupported(!SqlDialect.SQLITE.equals(dbEngine));
        resultGridPane.setOnHiddenColumnsChanged(gridHiddenColumnNames -> {
            hiddenColumnNames.clear();
            hiddenColumnNames.addAll(gridHiddenColumnNames);
        });
        String truncatedText = getBool(result.get(QueryKeyEnum.IS_TRUNCATED)) ? " " + __("(límite alcanzado)") : "";
        statusLabel.setText(__("{0} fila(s)", rows.size()) + truncatedText + " · " + executeQueryResultDto.getElapsedMs() + " ms");

        tableGridEditController = TableGridEditController.getInstance(connectionUuid, sessionKey, databaseName, tableName);
        tableGridEditController.enableEditing(resultGridPane, columnNames, loadedData.tableColumns(), this::updateEditStatus);
        String readOnlyReason = tableGridEditController.getReadOnlyReason();
        gridContainer.getChildren().setAll(resultGridPane);
        if (readOnlyReason != null) {
            tableGridEditController = null;
            resultGridPane.setReadOnlyReason(readOnlyReason);
            setEditBarVisible(false);
            statusLabel.setText(readOnlyReason);
            return;
        }
        setEditBarVisible(true);
        updateEditStatus();
    }

    private void updateEditStatus() {
        int changeCount = tableGridEditController == null ? 0 : tableGridEditController.getChangeCount();
        editStatusLabel.setText(changeCount == 0 ? __("Editable: doble clic, F2 o Enter") : __("{0} cambio(s) sin guardar", changeCount));
        saveButton.setDisable(changeCount == 0);
        discardButton.setDisable(changeCount == 0);
    }

    private void setEditBarVisible(boolean isVisible) {
        editBar.setVisible(isVisible);
        editBar.setManaged(isVisible);
    }

    /**
     * El CREATE TABLE de esta tabla para "Exportar como" SQL (se llama en segundo plano, desde el dialogo).
     * @throws SQLException
     */
    private String getCreateTableSql() throws SQLException {
        return GetTableInfoService.getInstance().invoke(GetTableInfoDto.fromPrimitives(Primitives.of(
            "connectionUuid", connectionUuid,
            "sessionKey", sessionKey,
            "databaseName", databaseName,
            "tableName", tableName
        ))).getCreateStatement();
    }

    /**
     * Las columnas de la clave primaria, en su orden (ListTableColumnsService las marca con COLUMN_KEY = PRI).
     */
    private List<String> getPrimaryKeyColumns(List<Map<String, Object>> tableColumns) {
        List<String> primaryKeyColumns = new ArrayList<>();
        for (Map<String, Object> tableColumn : tableColumns) {
            if (!TableColumnMetaEnum.KEY_PRIMARY.equals(getTrimmedString(tableColumn.get(ExplorerKeyEnum.COLUMN_KEY)))) continue;
            primaryKeyColumns.add(getTrimmedString(tableColumn.get(ExplorerKeyEnum.COLUMN_NAME)));
        }
        return primaryKeyColumns;
    }

    private int getLimit() {
        String limitText = limitField.getText().trim();
        int limit = getInt(limitText);
        if (limitText.isEmpty() || limit < 0) {
            limitField.setText(String.valueOf(DEFAULT_LIMIT));
            return DEFAULT_LIMIT;
        }
        return limit;
    }
}
