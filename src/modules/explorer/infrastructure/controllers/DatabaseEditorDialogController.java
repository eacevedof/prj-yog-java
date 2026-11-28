package modules.explorer.infrastructure.controllers;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import modules.shared.domain.enums.ExceptionEnum;
import modules.shared.application.Primitives;
import modules.shared.infrastructure.components.theme.ThemeComponent;
import modules.shared.infrastructure.controllers.AbstractFxController;
import modules.shared.infrastructure.exceptions.InterfaceException;

import core.config.TranslatorResourceBundle;
import static core.config.Global.__;

import modules.explorer.domain.entities.DatabaseOptionsEntity;
import modules.explorer.domain.services.DatabaseAlterSqlBuilder;
import modules.explorer.application.alterdatabase.AlterDatabaseDto;
import modules.explorer.application.alterdatabase.AlterDatabaseResultDto;
import modules.explorer.application.getdatabaseoptions.GetDatabaseOptionsDto;
import modules.explorer.application.getdatabaseoptions.GetDatabaseOptionsResultDto;
import modules.explorer.application.alterdatabase.AlterDatabaseService;
import modules.explorer.application.getdatabaseoptions.GetDatabaseOptionsService;

/**
 * El "Alter Database" (MySQL/MariaDB): juego de caracteres y cotejamiento por defecto, cifrado por defecto (MySQL 8),
 * solo lectura (MySQL 8.0.22+) y comentario (MariaDB 10.5+), con el SQL que se va a ejecutar a la vista. Lo que el
 * servidor no da sale desactivado. Al guardar, onSaved recibe el nombre de la BD (el arbol la recarga).
 */
public final class DatabaseEditorDialogController extends AbstractFxController {

    private static final String PATH_DIALOG = "/modules/explorer/infrastructure/views/database-editor-dialog.fxml";

    @FXML
    private Label databaseNameLabel;
    @FXML
    private ComboBox<String> characterSetComboBox;
    @FXML
    private ComboBox<String> collationComboBox;
    @FXML
    private CheckBox encryptionCheckBox;
    @FXML
    private CheckBox readOnlyCheckBox;
    @FXML
    private TextField commentField;
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
    private DatabaseOptionsEntity original;
    private Map<String, List<String>> collationsByCharacterSet = Map.of();
    private Consumer<String> onSaved = databaseName -> { };

    /**
     * Abre el dialogo (no bloquea) y lee la BD en segundo plano.
     */
    public static void open(Window owner, String connectionUuid, String sessionKey, String databaseName, Consumer<String> onSaved) {
        FXMLLoader fxmlLoader = new FXMLLoader(DatabaseEditorDialogController.class.getResource(PATH_DIALOG), TranslatorResourceBundle.getInstance());
        Parent dialogRoot;
        try {
            dialogRoot = fxmlLoader.load();
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        DatabaseEditorDialogController databaseEditorDialogController = fxmlLoader.getController();
        Stage dialogStage = new Stage();
        dialogStage.initOwner(owner);
        dialogStage.initModality(Modality.WINDOW_MODAL);
        dialogStage.setTitle(__("Alterar BD {0}", databaseName));
        Scene dialogScene = new Scene(dialogRoot);
        ThemeComponent.getInstance().applyTo(dialogScene);
        dialogStage.setScene(dialogScene);
        databaseEditorDialogController.setup(connectionUuid, sessionKey, databaseName, onSaved);
        dialogStage.show();
    }

    private void setup(String connectionUuid, String sessionKey, String databaseName, Consumer<String> onSaved) {
        this.connectionUuid = connectionUuid;
        this.sessionKey = sessionKey;
        this.onSaved = onSaved;
        databaseNameLabel.setText(databaseName);
        saveButton.setDisable(true);
        setStatus(__("Leyendo la BD..."));
        GetDatabaseOptionsDto getDatabaseOptionsDto = GetDatabaseOptionsDto.fromPrimitives(Primitives.of(
            "connectionUuid", connectionUuid, "sessionKey", sessionKey, "databaseName", databaseName
        ));
        Task<GetDatabaseOptionsResultDto> task = new Task<>() {
            @Override
            protected GetDatabaseOptionsResultDto call() throws SQLException {
                return GetDatabaseOptionsService.getInstance().invoke(getDatabaseOptionsDto);
            }
        };
        task.setOnSucceeded(event -> showOptions(task.getValue()));
        task.setOnFailed(event -> setStatus(getErrorMessage(task.getException())));
        startDaemon(task, "database-editor-read");
    }

    private void showOptions(GetDatabaseOptionsResultDto resultDto) {
        original = resultDto.getDatabaseOptions();
        collationsByCharacterSet = resultDto.getCollationsByCharacterSet();
        characterSetComboBox.getItems().setAll(collationsByCharacterSet.keySet());
        characterSetComboBox.setValue(original.characterSet());
        collationComboBox.getItems().setAll(collationsByCharacterSet.getOrDefault(original.characterSet(), List.of()));
        collationComboBox.setValue(original.collation());
        characterSetComboBox.valueProperty().addListener((observable, oldCharacterSet, newCharacterSet) -> {
            // al cambiar de juego, su cotejamiento por defecto (el primero)
            List<String> collations = collationsByCharacterSet.getOrDefault(newCharacterSet, List.of());
            collationComboBox.getItems().setAll(collations);
            collationComboBox.setValue(collations.isEmpty() ? "" : collations.getFirst());
        });
        setupOption(encryptionCheckBox, original.isEncryptionSupported());
        encryptionCheckBox.setSelected(original.isEncrypted());
        setupOption(readOnlyCheckBox, original.isReadOnlySupported());
        readOnlyCheckBox.setSelected(original.isReadOnly());
        setupOption(commentField, original.isCommentSupported());
        commentField.setText(original.comment());

        collationComboBox.valueProperty().addListener((observable, oldValue, newValue) -> refreshSqlPreview());
        encryptionCheckBox.selectedProperty().addListener((observable, oldValue, newValue) -> refreshSqlPreview());
        readOnlyCheckBox.selectedProperty().addListener((observable, oldValue, newValue) -> refreshSqlPreview());
        commentField.textProperty().addListener((observable, oldValue, newValue) -> refreshSqlPreview());
        refreshSqlPreview();
        setStatus("");
    }

    /**
     * Lo que el servidor no da: desactivado y con el porque en el tooltip.
     */
    private void setupOption(Control control, boolean isSupported) {
        control.setDisable(!isSupported);
        if (!isSupported) control.setTooltip(new Tooltip(__("Este servidor no tiene esta opción")));
    }

    private DatabaseOptionsEntity getEdited() {
        return original.withEdited(
            characterSetComboBox.getValue() == null ? "" : characterSetComboBox.getValue(),
            collationComboBox.getValue() == null ? "" : collationComboBox.getValue(),
            encryptionCheckBox.isSelected(),
            readOnlyCheckBox.isSelected(),
            commentField.getText()
        );
    }

    private void refreshSqlPreview() {
        if (original == null) return;
        String alterSql = DatabaseAlterSqlBuilder.getInstance().getAlterSql(original, getEdited());
        sqlTextArea.setText(alterSql.isEmpty() ? "-- " + __("No hay nada que cambiar") : alterSql + ";");
        saveButton.setDisable(alterSql.isEmpty());
    }

    @FXML
    private void onSave() {
        if (original == null) return;
        saveDatabase(false);
    }

    private void saveDatabase(boolean isConfirmed) {
        AlterDatabaseDto alterDatabaseDto = AlterDatabaseDto.fromPrimitives(Primitives.of(
            "connectionUuid", connectionUuid, "sessionKey", sessionKey, "original", original, "edited", getEdited(), "isConfirmed", isConfirmed
        ));
        saveButton.setDisable(true);
        setStatus(__("Guardando..."));
        Task<AlterDatabaseResultDto> task = new Task<>() {
            @Override
            protected AlterDatabaseResultDto call() throws SQLException {
                return AlterDatabaseService.getInstance().invoke(alterDatabaseDto);
            }
        };
        task.setOnSucceeded(event -> {
            onSaved.accept(original.databaseName());
            ((Stage) closeButton.getScene().getWindow()).close();
        });
        task.setOnFailed(event -> {
            saveButton.setDisable(false);
            Throwable throwable = task.getException();
            if (throwable instanceof InterfaceException interfaceException && interfaceException.getCode() == ExceptionEnum.CODE_PRECONDITION_REQUIRED) {
                setStatus("");
                if (isConfirmed(throwable.getMessage() + ".\n\n" + __("¿Guardar igualmente?"))) saveDatabase(true);
                return;
            }
            setStatus(getErrorMessage(throwable));
            showError(throwable);
        });
        startDaemon(task, "database-editor-save");
    }

    @FXML
    private void onClose() {
        ((Stage) closeButton.getScene().getWindow()).close();
    }

    private void setStatus(String status) {
        statusLabel.setText(status);
    }

    private void startDaemon(Task<?> task, String threadName) {
        Thread thread = new Thread(task, threadName);
        thread.setDaemon(true);
        thread.start();
    }
}
