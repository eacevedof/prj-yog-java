package modules.query.infrastructure.controllers;

import java.sql.SQLException;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javafx.concurrent.Task;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;

import modules.shared.domain.enums.ExceptionEnum;
import modules.shared.application.Primitives;
import modules.shared.infrastructure.components.theme.ThemeComponent;
import modules.shared.infrastructure.controllers.AbstractFxController;
import static modules.shared.application.Primitives.getTrimmedString;

import static core.config.Global.__;

import modules.explorer.domain.enums.ExplorerKeyEnum;

import modules.query.domain.enums.QueryMessageEnum;
import modules.query.domain.enums.TableColumnMetaEnum;
import modules.query.application.applytablechanges.ApplyTableChangesDto;
import modules.query.application.applytablechanges.ApplyTableChangesService;
import modules.query.infrastructure.components.ResultGridPane;
import modules.query.infrastructure.components.TableEditSession;
import modules.query.domain.exceptions.QueryException;

/**
 * Feature 6: la edicion de una rejilla contra su tabla, la misma en la pestaña Datos y en el resultado de un SELECT
 * de una sola tabla (como SQLyog). Decide si se puede editar (clave primaria entera en el resultado, columnas de la
 * tabla, BLOB/BIT/binarias de solo lectura) y guarda los cambios juntos en una transaccion, confirmando antes los
 * borrados y, en una conexion de produccion, el guardado. Si el guardado falla, ensena el error: los cambios se quedan
 * en la rejilla para corregirlos y la transaccion ya se deshizo.
 */
public final class TableGridEditController extends AbstractFxController {

    private final String connectionUuid;
    private final String sessionKey;
    private final String databaseName;
    private final String tableName;
    private TableEditSession tableEditSession;
    private String readOnlyReason;
    private boolean isSaving;

    /**
     * Al acabar de guardar: las filas afectadas y, siempre, onFinished (tambien si falla o el usuario no confirma).
     */
    public interface SaveHandler {
        void onSaved(int affectedRows);
        void onFinished();
    }

    private TableGridEditController(String connectionUuid, String sessionKey, String databaseName, String tableName) {
        this.connectionUuid = connectionUuid;
        this.sessionKey = sessionKey;
        this.databaseName = databaseName;
        this.tableName = tableName;
    }

    public static TableGridEditController getInstance(String connectionUuid, String sessionKey, String databaseName, String tableName) {
        return new TableGridEditController(connectionUuid, sessionKey, databaseName, tableName);
    }

    /**
     * Hace editable la rejilla si se puede; si no, deja el motivo en getReadOnlyReason.
     * tableColumns: las de ListTableColumnsService, con las claves de ExplorerKeyEnum.
     */
    public void enableEditing(ResultGridPane resultGridPane, List<String> columnNames, List<Map<String, Object>> tableColumns, Runnable onEdited) {
        tableEditSession = null;
        Set<String> tableColumnNames = new HashSet<>();
        Set<String> primaryKeyColumns = new LinkedHashSet<>();
        Set<String> autoIncrementColumns = new HashSet<>();
        Set<String> readOnlyColumns = new HashSet<>();
        for (Map<String, Object> tableColumn : tableColumns) {
            String columnName = getTrimmedString(tableColumn.get(ExplorerKeyEnum.COLUMN_NAME));
            String extra = getTrimmedString(tableColumn.get(ExplorerKeyEnum.EXTRA));
            tableColumnNames.add(columnName);
            if (TableColumnMetaEnum.KEY_PRIMARY.equals(getTrimmedString(tableColumn.get(ExplorerKeyEnum.COLUMN_KEY)))) primaryKeyColumns.add(columnName);
            if (extra.contains(TableColumnMetaEnum.EXTRA_AUTO_INCREMENT)) autoIncrementColumns.add(columnName);
            boolean isReadOnlyType = TableColumnMetaEnum.READ_ONLY_DATA_TYPES.contains(getTrimmedString(tableColumn.get(ExplorerKeyEnum.DATA_TYPE)));
            if (isReadOnlyType || extra.contains(TableColumnMetaEnum.EXTRA_GENERATED)) readOnlyColumns.add(columnName);
        }

        readOnlyReason = getReadOnlyReason(columnNames, tableColumnNames, primaryKeyColumns, readOnlyColumns);
        if (readOnlyReason != null) return;

        Set<Integer> readOnlyColumnIndexes = new HashSet<>();
        for (int i = 0; i < columnNames.size(); i++) {
            if (readOnlyColumns.contains(columnNames.get(i))) readOnlyColumnIndexes.add(i);
        }
        tableEditSession = TableEditSession.getInstance(columnNames, primaryKeyColumns, autoIncrementColumns);
        resultGridPane.enableEditing(tableEditSession, readOnlyColumnIndexes, onEdited);
    }

    /**
     * Tras enableEditing: null si la rejilla quedo editable; si no, el motivo (para ensenarlo).
     */
    public String getReadOnlyReason() {
        return readOnlyReason;
    }

    private String getReadOnlyReason(List<String> columnNames, Set<String> tableColumnNames, Set<String> primaryKeyColumns, Set<String> readOnlyColumns) {
        if (tableColumnNames.isEmpty()) return __("Solo lectura: no se encuentra la tabla {0}", tableName);
        if (primaryKeyColumns.isEmpty()) return __("Solo lectura: vista o tabla sin clave primaria");
        if (new HashSet<>(columnNames).size() != columnNames.size()) return __("Solo lectura: hay columnas repetidas");
        if (!tableColumnNames.containsAll(columnNames)) return __("Solo lectura: hay columnas que no son de la tabla");
        if (!columnNames.containsAll(primaryKeyColumns)) return __("Solo lectura: el resultado no trae toda la clave primaria");
        for (String primaryKeyColumn : primaryKeyColumns) {
            if (readOnlyColumns.contains(primaryKeyColumn)) return __("Solo lectura: la clave primaria es binaria");
        }
        return null;
    }

    public boolean hasPendingChanges() {
        return tableEditSession != null && tableEditSession.hasChanges();
    }

    public int getChangeCount() {
        return tableEditSession == null ? 0 : tableEditSession.getChangeCount();
    }

    /**
     * Guarda (Ctrl+S o el boton): pide confirmar los borrados y, si la conexion es de produccion y el servicio lo pide,
     * el guardado. Mientras se guarda, otro Ctrl+S no lanza un segundo guardado.
     */
    public void save(SaveHandler saveHandler) {
        if (isSaving) return;
        if (!hasPendingChanges()) {
            saveHandler.onFinished();
            return;
        }
        int deletedCount = tableEditSession.getDeletedCount();
        if (deletedCount > 0 && !isRowsDeleteConfirmed(deletedCount)) {
            saveHandler.onFinished();
            return;
        }
        applyChanges(false, saveHandler);
    }

    /**
     * isProductionConfirmed viaja al servicio: el usuario ya acepto guardar en una conexion de produccion.
     */
    private void applyChanges(boolean isProductionConfirmed, SaveHandler saveHandler) {
        isSaving = true;
        ApplyTableChangesDto applyTableChangesDto = ApplyTableChangesDto.fromPrimitives(Primitives.of(
            "connectionUuid", connectionUuid,
            "sessionKey", sessionKey,
            "databaseName", databaseName,
            "tableName", tableName,
            "changes", tableEditSession.getChanges(),
            "isConfirmed", isProductionConfirmed
        ));
        Task<Integer> saveTask = new Task<>() {
            @Override
            protected Integer call() throws SQLException {
                return ApplyTableChangesService.getInstance().invoke(applyTableChangesDto).getAffectedRows();
            }
        };
        saveTask.setOnSucceeded(event -> {
            isSaving = false;
            tableEditSession = null;
            saveHandler.onFinished();
            saveHandler.onSaved(saveTask.getValue());
        });
        saveTask.setOnFailed(event -> {
            isSaving = false;
            Throwable exception = saveTask.getException();
            boolean isProductionPrompt = exception instanceof QueryException queryException
                && queryException.getCode() == ExceptionEnum.CODE_PRECONDITION_REQUIRED;
            if (isProductionPrompt && isProductionSaveConfirmed(exception.getMessage())) {
                applyChanges(true, saveHandler);
                return;
            }
            saveHandler.onFinished();
            if (!isProductionPrompt) showSaveError(exception);
        });
        Thread saveThread = new Thread(saveTask, "save-table-changes");
        saveThread.setDaemon(true);
        saveThread.start();
    }

    /**
     * Como SQLyog ("Do you really want to delete...?"), pero al guardar: hasta entonces el borrado solo tacha la fila.
     * El boton por defecto es No.
     */
    private boolean isRowsDeleteConfirmed(int deletedCount) {
        ButtonType deleteButtonType = new ButtonType(__("Borrar"), ButtonBar.ButtonData.OK_DONE);
        ButtonType keepButtonType = new ButtonType(__("No"), ButtonBar.ButtonData.CANCEL_CLOSE);
        Alert alert = new Alert(Alert.AlertType.WARNING, __("Se borran {0} fila(s) de {1}. ¿Seguir?", deletedCount, tableName), deleteButtonType, keepButtonType);
        ThemeComponent.getInstance().applyTo(alert);
        alert.setTitle(__("Borrar filas"));
        alert.setHeaderText(null);
        ((Button) alert.getDialogPane().lookupButton(deleteButtonType)).setDefaultButton(false);
        ((Button) alert.getDialogPane().lookupButton(keepButtonType)).setDefaultButton(true);
        return alert.showAndWait().filter(deleteButtonType::equals).isPresent();
    }

    private boolean isProductionSaveConfirmed(String message) {
        ButtonType saveButtonType = new ButtonType(__("Guardar en producción"), ButtonBar.ButtonData.OK_DONE);
        Alert alert = new Alert(Alert.AlertType.WARNING, message, saveButtonType, ButtonType.CANCEL);
        ThemeComponent.getInstance().applyTo(alert);
        alert.setTitle(__("Conexión de producción"));
        alert.setHeaderText(__("Cambios en PRODUCCIÓN"));
        ((Button) alert.getDialogPane().lookupButton(saveButtonType)).setDefaultButton(false);
        ((Button) alert.getDialogPane().lookupButton(ButtonType.CANCEL)).setDefaultButton(true);
        return alert.showAndWait().filter(saveButtonType::equals).isPresent();
    }

    private void showSaveError(Throwable exception) {
        if (exception instanceof SQLException) {
            showErrorMessage(__(QueryMessageEnum.CHANGES_NOT_SAVED_X, getErrorMessage(exception)));
            return;
        }
        showError(exception);
    }
}
