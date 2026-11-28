package modules.query.infrastructure.controllers;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import javafx.concurrent.Task;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import modules.shared.application.Primitives;
import modules.shared.infrastructure.components.icons.AppIconEnum;
import modules.shared.infrastructure.components.icons.AppIcon;

import static core.config.Global.__;

import modules.explorer.application.listtablecolumns.ListTableColumnsDto;
import modules.explorer.application.listtablecolumns.ListTableColumnsService;

import modules.query.infrastructure.components.ResultGridPane;

/**
 * La rejilla del resultado de un SELECT de una sola tabla, editable como en SQLyog: encima, una barra con la tabla y
 * los botones de insertar, duplicar, borrar, poner NULL, guardar (Ctrl+S) y cancelar. Lee las columnas de la tabla en
 * segundo plano (clave primaria, tipos) y, si no se puede editar, lo dice en la barra. Guardar y cancelar vuelven a
 * ejecutar el SELECT (onReload) para ver lo que de verdad quedo en la tabla. Es nodo y controller a la vez: la pestaña
 * del resultado lo lleva como contenido.
 */
public final class EditableResultController extends VBox {

    // separacion en pixeles entre los botones de la barra de edicion del resultado
    private static final int TOOLBAR_SPACING = 6;

    private final ResultGridPane resultGridPane;
    private final HBox editBar = new HBox(TOOLBAR_SPACING);
    private final Label tableLabel = new Label();
    private final Label editStatusLabel = new Label();
    private final Button saveButton = getButton(__("Guardar (Ctrl+S)"), AppIconEnum.SAVE, this::onSave);
    private final Button discardButton = getButton(__("Cancelar cambios"), AppIconEnum.DISCARD, this::onDiscard);
    private final List<Button> editButtons;
    private TableGridEditController tableGridEditController;
    private Consumer<String> onReload = statusMessage -> { };

    private EditableResultController(ResultGridPane resultGridPane) {
        this.resultGridPane = resultGridPane;
        editButtons = List.of(
            getButton(__("Insertar fila"), AppIconEnum.ADD_ROW, resultGridPane::addNewRow),
            getButton(__("Duplicar"), AppIconEnum.COPY, resultGridPane::duplicateSelectedRow),
            getButton(__("Borrar (Supr)"), AppIconEnum.DELETE, resultGridPane::deleteSelectedRows),
            getButton(__("Poner NULL"), AppIconEnum.SET_NULL, resultGridPane::setSelectedCellsNull)
        );
        tableLabel.getStyleClass().add("active-database");
        editStatusLabel.getStyleClass().add("query-status");
        editBar.getStyleClass().add("toolbar-buttons");
        editBar.setAlignment(Pos.CENTER_LEFT);
        editBar.getChildren().add(tableLabel);
        editBar.getChildren().addAll(editButtons);
        editBar.getChildren().addAll(saveButton, discardButton, editStatusLabel);
        setEditBarState(false, "");

        resultGridPane.setOnSaveRequested(this::onSave);
        resultGridPane.setOnDiscardRequested(this::onDiscard);
        resultGridPane.setReadOnlyReason(__("Solo lectura: el resultado no es de una sola tabla (sin JOIN, GROUP BY ni expresiones)"));
        setOnKeyPressed(keyEvent -> {
            if (keyEvent.getCode() == KeyCode.S && keyEvent.isShortcutDown()) {
                onSave();
                keyEvent.consume();
            }
        });
        VBox.setVgrow(resultGridPane, Priority.ALWAYS);
        getChildren().addAll(editBar, resultGridPane);
    }

    public static EditableResultController getInstance(ResultGridPane resultGridPane) {
        return new EditableResultController(resultGridPane);
    }

    public ResultGridPane getResultGridPane() {
        return resultGridPane;
    }

    /**
     * Despues de guardar o cancelar: vuelve a ejecutar el SELECT; el texto es lo que se ensena al acabar (p. ej.
     * "Guardado: 2 fila(s)"), vacio al cancelar.
     */
    public void setOnReload(Consumer<String> onReload) {
        this.onReload = onReload;
    }

    public int getChangeCount() {
        return tableGridEditController == null ? 0 : tableGridEditController.getChangeCount();
    }

    public boolean isEditable() {
        return tableGridEditController != null;
    }

    /**
     * Lo que dice la barra: el motivo si no se edita, o los cambios pendientes.
     */
    public String getEditStatusText() {
        return editStatusLabel.getText();
    }

    /**
     * Guardar (Ctrl+S): lo mismo que el boton.
     */
    public void save() {
        onSave();
    }

    /**
     * El SELECT es de una sola tabla: lee sus columnas (en editSessionKey, la sesion de editar de la pestaña) y, si
     * tiene clave primaria y el resultado la trae, la rejilla pasa a editable.
     */
    public void enableEditingFor(String connectionUuid, String editSessionKey, String databaseName, String tableName, List<String> columnNames) {
        String qualifiedName = databaseName.isEmpty() ? tableName : databaseName + "." + tableName;
        resultGridPane.setExportTableName(tableName);
        setEditBarState(false, __("Comprobando si {0} se puede editar...", qualifiedName));
        ListTableColumnsDto listTableColumnsDto = ListTableColumnsDto.fromPrimitives(Primitives.of(
            "connectionUuid", connectionUuid,
            "sessionKey", editSessionKey,
            "databaseName", databaseName,
            "tableName", tableName
        ));
        Task<List<Map<String, Object>>> columnsTask = new Task<>() {
            @Override
            protected List<Map<String, Object>> call() throws SQLException {
                return ListTableColumnsService.getInstance().invoke(listTableColumnsDto).getColumns();
            }
        };
        columnsTask.setOnSucceeded(event -> {
            TableGridEditController candidateTableGridEditController = TableGridEditController.getInstance(connectionUuid, editSessionKey, databaseName, tableName);
            candidateTableGridEditController.enableEditing(resultGridPane, columnNames, columnsTask.getValue(), this::updateEditStatus);
            String readOnlyReason = candidateTableGridEditController.getReadOnlyReason();
            if (readOnlyReason != null) {
                resultGridPane.setReadOnlyReason(readOnlyReason);
                setEditBarState(false, qualifiedName + " · " + readOnlyReason);
                return;
            }
            tableGridEditController = candidateTableGridEditController;
            tableLabel.setText(qualifiedName);
            setEditBarState(true, "");
            updateEditStatus();
        });
        columnsTask.setOnFailed(event -> {
            String readOnlyReason = __("Solo lectura: no se pudieron leer las columnas de {0}", qualifiedName);
            resultGridPane.setReadOnlyReason(readOnlyReason);
            setEditBarState(false, readOnlyReason);
        });
        Thread columnsThread = new Thread(columnsTask, "result-table-columns");
        columnsThread.setDaemon(true);
        columnsThread.start();
    }

    private void onSave() {
        if (getChangeCount() == 0) return;
        saveButton.setDisable(true);
        editStatusLabel.setText(__("Guardando..."));
        tableGridEditController.save(new TableGridEditController.SaveHandler() {
            @Override
            public void onSaved(int affectedRows) {
                onReload.accept(__("Guardado: {0} fila(s)", affectedRows));
            }

            @Override
            public void onFinished() {
                updateEditStatus();
            }
        });
    }

    private void onDiscard() {
        if (getChangeCount() == 0) return;
        tableGridEditController = null;
        onReload.accept("");
    }

    private void updateEditStatus() {
        int changeCount = getChangeCount();
        editStatusLabel.setText(changeCount == 0 ? __("Editable: doble clic, F2 o Enter") : __("{0} cambio(s) sin guardar", changeCount));
        saveButton.setDisable(changeCount == 0);
        discardButton.setDisable(changeCount == 0);
    }

    /**
     * Editable: la tabla y los botones. Si no: solo el texto (el motivo), o la barra oculta si no hay nada que decir.
     */
    private void setEditBarState(boolean isEditable, String message) {
        tableLabel.setVisible(isEditable);
        tableLabel.setManaged(isEditable);
        for (Button button : editButtons) {
            button.setVisible(isEditable);
            button.setManaged(isEditable);
        }
        for (Button button : List.of(saveButton, discardButton)) {
            button.setVisible(isEditable);
            button.setManaged(isEditable);
        }
        if (!isEditable) editStatusLabel.setText(message);
        boolean isBarVisible = isEditable || !message.isEmpty();
        editBar.setVisible(isBarVisible);
        editBar.setManaged(isBarVisible);
    }

    private Button getButton(String text, String iconName, Runnable onAction) {
        Button button = new Button(text, AppIcon.of(iconName));
        button.setMinWidth(Button.USE_PREF_SIZE);
        button.setOnAction(actionEvent -> onAction.run());
        return button;
    }
}
