package modules.dbcopy.infrastructure.controllers;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.function.BiConsumer;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import modules.shared.domain.enums.ExceptionEnum;
import modules.shared.application.Primitives;
import modules.shared.infrastructure.components.date.DateTimer;
import modules.shared.infrastructure.components.files.FolderOpenerComponent;
import modules.shared.infrastructure.components.theme.ThemeComponent;
import modules.shared.infrastructure.components.uuid.UuidGenerator;
import modules.shared.infrastructure.controllers.AbstractFxController;
import modules.shared.infrastructure.exceptions.InterfaceException;

import core.config.TranslatorResourceBundle;
import static core.config.Global.__;

import modules.workspace.domain.enums.WorkspaceKeyEnum;
import modules.workspace.infrastructure.components.LastDirectoryComponent;

import modules.dbcopy.domain.enums.RenameDatabasePhaseEnum;
import modules.dbcopy.domain.services.DbCopyProgressListener;
import modules.dbcopy.application.renamedatabase.RenameDatabaseDto;
import modules.dbcopy.application.renamedatabase.RenameDatabaseResultDto;
import modules.dbcopy.application.renamedatabase.RenameDatabaseService;
import modules.dbcopy.infrastructure.components.DbCopyProgressTracker;

/**
 * "Renombrar BD" (MySQL/MariaDB): nombre nuevo, el .sql donde se vuelca la original (copia de seguridad, en la carpeta
 * de los volcados) y si se borra la original al acabar. Corre en segundo plano con el paso en curso a la vista; no se
 * puede cerrar a medias. Al acabar, onRenamed recibe el nombre de antes y el nuevo (la pestaña recarga el arbol).
 */
public final class RenameDatabaseDialogController extends AbstractFxController {

    private static final String PATH_DIALOG = "/modules/dbcopy/infrastructure/views/rename-database-dialog.fxml";
    private static final String RENAME_ID_PREFIX = "rename-db-";

    @FXML
    private Label databaseNameLabel;
    @FXML
    private TextField newNameField;
    @FXML
    private TextField filePathField;
    @FXML
    private Button chooseFileButton;
    @FXML
    private Button openFolderButton;
    @FXML
    private CheckBox dropOriginalCheckBox;
    @FXML
    private ProgressBar progressBar;
    @FXML
    private Label statusLabel;
    @FXML
    private Button renameButton;
    @FXML
    private Button closeButton;

    private String connectionUuid = "";
    private String databaseName = "";
    private boolean isRunning;
    private BiConsumer<String, String> onRenamed = (oldName, newName) -> { };

    /**
     * Abre el dialogo (no bloquea).
     */
    public static void open(Window owner, String connectionUuid, String databaseName, BiConsumer<String, String> onRenamed) {
        FXMLLoader fxmlLoader = new FXMLLoader(RenameDatabaseDialogController.class.getResource(PATH_DIALOG), TranslatorResourceBundle.getInstance());
        Parent dialogRoot;
        try {
            dialogRoot = fxmlLoader.load();
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        RenameDatabaseDialogController renameDatabaseDialogController = fxmlLoader.getController();
        Stage dialogStage = new Stage();
        dialogStage.initOwner(owner);
        dialogStage.initModality(Modality.WINDOW_MODAL);
        dialogStage.setTitle(__("Renombrar BD {0}", databaseName));
        Scene dialogScene = new Scene(dialogRoot);
        ThemeComponent.getInstance().applyTo(dialogScene);
        dialogStage.setScene(dialogScene);
        dialogStage.setOnCloseRequest(event -> {
            if (!renameDatabaseDialogController.isRunning) return;
            // renombrar no se para a medias (dejaria dos BD a medio copiar): se avisa en vez de no hacer nada
            renameDatabaseDialogController.setStatus(__("Renombrando: espera a que acabe, no se puede parar a medias"));
            event.consume();
        });
        renameDatabaseDialogController.setup(connectionUuid, databaseName, onRenamed);
        dialogStage.show();
    }

    private void setup(String connectionUuid, String databaseName, BiConsumer<String, String> onRenamed) {
        this.connectionUuid = connectionUuid;
        this.databaseName = databaseName;
        this.onRenamed = onRenamed;
        databaseNameLabel.setText(databaseName);
        newNameField.setText(databaseName);
        newNameField.selectAll();
        File directory = LastDirectoryComponent.getInstance(WorkspaceKeyEnum.SQL_DUMP_DIRECTORY).getDirectory();
        String timestamp = DateTimer.getInstance().getNowDt().replaceAll("\\D", "");
        filePathField.setText(new File(directory, databaseName + "_before_rename_" + timestamp + ".sql").getPath());
        filePathField.textProperty().addListener((observable, oldPath, newPath) -> updateOpenFolderButton());
        updateOpenFolderButton();
    }

    @FXML
    private void onChooseFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(__("Copia de seguridad (.sql)"));
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("SQL (*.sql)", "*.sql"));
        File currentFile = new File(filePathField.getText().trim());
        if (currentFile.getParentFile() != null && currentFile.getParentFile().isDirectory()) fileChooser.setInitialDirectory(currentFile.getParentFile());
        fileChooser.setInitialFileName(currentFile.getName());
        File chosenFile = fileChooser.showSaveDialog(filePathField.getScene().getWindow());
        if (chosenFile == null) return;
        filePathField.setText(chosenFile.getPath());
        LastDirectoryComponent.getInstance(WorkspaceKeyEnum.SQL_DUMP_DIRECTORY).saveDirectoryOf(chosenFile);
    }

    @FXML
    private void onOpenFolder() {
        try {
            FolderOpenerComponent.getInstance().open(filePathField.getText());
        }
        catch (IllegalArgumentException e) {
            setStatus(__("No existe la carpeta de {0}", filePathField.getText().trim()));
        }
    }

    private void updateOpenFolderButton() {
        openFolderButton.setDisable(FolderOpenerComponent.getInstance().getExistingFolder(filePathField.getText()) == null);
    }

    @FXML
    private void onRename() {
        String newDatabaseName = newNameField.getText().trim();
        String question = dropOriginalCheckBox.isSelected()
            ? __("¿Renombrar {0} a {1}? Al terminar se borra {0}.", databaseName, newDatabaseName)
            : __("¿Copiar {0} a {1}? {0} se queda como está.", databaseName, newDatabaseName);
        if (!isDeleteConfirmed(question)) return;
        runRename(newDatabaseName, false);
    }

    private void runRename(String newDatabaseName, boolean isConfirmed) {
        String renameId = UuidGenerator.getInstance().getUniqueIdWithPrefix(RENAME_ID_PREFIX);
        RenameDatabaseDto renameDatabaseDto = RenameDatabaseDto.fromPrimitives(Primitives.of(
            "renameId", renameId,
            "connectionUuid", connectionUuid,
            "databaseName", databaseName,
            "newDatabaseName", newDatabaseName,
            "filePath", filePathField.getText().trim(),
            "isDropOriginal", dropOriginalCheckBox.isSelected(),
            "isConfirmed", isConfirmed
        ));
        setRunning(true);
        setStatus(__("Empezando..."));
        DbCopyProgressTracker dbCopyProgressTracker = DbCopyProgressTracker.getInstance();
        dbCopyProgressTracker.register(renameId, new DialogProgressListener());
        Task<RenameDatabaseResultDto> renameDatabaseTask = new Task<>() {
            @Override
            protected RenameDatabaseResultDto call() throws Exception {
                return RenameDatabaseService.getInstance().invoke(renameDatabaseDto);
            }
        };
        renameDatabaseTask.setOnSucceeded(event -> {
            dbCopyProgressTracker.unregister(renameId);
            setRunning(false);
            showResult(renameDatabaseTask.getValue());
        });
        renameDatabaseTask.setOnFailed(event -> {
            dbCopyProgressTracker.unregister(renameId);
            setRunning(false);
            Throwable throwable = renameDatabaseTask.getException();
            if (throwable instanceof InterfaceException interfaceException && interfaceException.getCode() == ExceptionEnum.CODE_PRECONDITION_REQUIRED) {
                setStatus("");
                if (isDatabaseNameTyped(throwable.getMessage())) runRename(newDatabaseName, true);
                return;
            }
            setStatus(getErrorMessage(throwable));
            showError(throwable);
        });
        Thread daemonThread = new Thread(renameDatabaseTask, "rename-database");
        daemonThread.setDaemon(true);
        daemonThread.start();
    }

    /**
     * Produccion: solo se sigue si se escribe el nombre exacto de la BD original.
     */
    private boolean isDatabaseNameTyped(String warning) {
        TextInputDialog nameDialog = new TextInputDialog();
        ThemeComponent.getInstance().applyTo(nameDialog);
        nameDialog.setTitle(__("Renombrar BD"));
        nameDialog.setHeaderText(warning);
        nameDialog.setContentText(__("Escribe {0} para confirmar", databaseName));
        nameDialog.initOwner(renameButton.getScene().getWindow());
        return nameDialog.showAndWait().map(String::trim).filter(databaseName::equals).isPresent();
    }

    private void showResult(RenameDatabaseResultDto renameDatabaseResultDto) {
        LastDirectoryComponent.getInstance(WorkspaceKeyEnum.SQL_DUMP_DIRECTORY).saveDirectoryOf(new File(renameDatabaseResultDto.getFilePath()));
        progressBar.setProgress(1);
        String doneMessage = (renameDatabaseResultDto.isOriginalDropped()
            ? __("{0} renombrada a {1}", databaseName, renameDatabaseResultDto.getNewDatabaseName())
            : __("{0} copiada a {1} (la original sigue)", databaseName, renameDatabaseResultDto.getNewDatabaseName()))
            + ": " + __("{0} objetos", renameDatabaseResultDto.getObjectCount()) + ", " + __("{0} filas", String.format("%,d", renameDatabaseResultDto.getRowCount()))
            + " " + __("en {0} s.", String.format("%.1f", renameDatabaseResultDto.getElapsedMs() / 1000.0))
            + "\n" + __("Copia de seguridad: {0}", renameDatabaseResultDto.getFilePath());
        setStatus(doneMessage);
        renameButton.setDisable(true);
        onRenamed.accept(databaseName, renameDatabaseResultDto.getNewDatabaseName());
        Alert alert = new Alert(Alert.AlertType.INFORMATION, doneMessage, ButtonType.OK);
        ThemeComponent.getInstance().applyTo(alert);
        alert.setHeaderText(null);
        alert.initOwner(renameButton.getScene().getWindow());
        alert.show();
    }

    @FXML
    private void onClose() {
        if (isRunning) return;
        ((Stage) closeButton.getScene().getWindow()).close();
    }

    private void setRunning(boolean isRunning) {
        this.isRunning = isRunning;
        progressBar.setVisible(true);
        progressBar.setManaged(true);
        if (isRunning) progressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
        for (Node node : List.of(newNameField, filePathField, chooseFileButton, dropOriginalCheckBox, renameButton, closeButton)) {
            node.setDisable(isRunning);
        }
    }

    private void setStatus(String status) {
        statusLabel.setText(status);
    }

    private String getPhaseLabel(String phase) {
        return switch (phase) {
            case RenameDatabasePhaseEnum.DUMP -> __("1/5 Volcando {0} al .sql...", databaseName);
            case RenameDatabasePhaseEnum.CREATE -> __("2/5 Creando {0}...", newNameField.getText().trim());
            case RenameDatabasePhaseEnum.LOAD -> __("3/5 Cargando el volcado en {0}...", newNameField.getText().trim());
            case RenameDatabasePhaseEnum.VERIFY -> __("4/5 Comprobando objetos y filas...");
            case RenameDatabasePhaseEnum.DROP -> __("5/5 Borrando {0}...", databaseName);
            default -> phase;
        };
    }

    private final class DialogProgressListener implements DbCopyProgressListener {

        @Override
        public void onPhaseStarted(String phase) {
            Platform.runLater(() -> {
                if (!RenameDatabasePhaseEnum.DUMP.equals(phase)) progressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
                setStatus(getPhaseLabel(phase));
            });
        }

        @Override
        public void onObjectStarted(String objectType, String objectName, int objectIndex, int objectCount) {
            Platform.runLater(() -> {
                progressBar.setProgress(objectCount == 0 ? 0 : (objectIndex - 1) / (double) objectCount);
                setStatus(__("1/5 Volcando {0} ({1}/{2})...", objectName, objectIndex, objectCount));
            });
        }

        @Override
        public void onRowsCopied(String tableName, long copiedRows, long estimatedRows) {
            Platform.runLater(() -> setStatus(__("1/5 Volcando {0}: {1} filas", tableName, String.format("%,d", copiedRows))));
        }
    }
}
