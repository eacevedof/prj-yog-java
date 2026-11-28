package modules.query.infrastructure.controllers;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.sql.SQLException;
import java.util.Map;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import modules.shared.domain.enums.ExceptionEnum;
import modules.shared.application.Primitives;
import modules.shared.infrastructure.components.files.FolderOpenerComponent;
import modules.shared.infrastructure.components.theme.ThemeComponent;
import modules.shared.infrastructure.components.uuid.UuidGenerator;
import modules.shared.infrastructure.controllers.AbstractFxController;
import modules.shared.infrastructure.exceptions.InterfaceException;
import static modules.shared.application.Primitives.getTrimmedString;

import core.config.TranslatorResourceBundle;
import static core.config.Global.__;

import modules.connections.infrastructure.factories.TargetSessionFactory;

import modules.workspace.domain.enums.WorkspaceKeyEnum;
import modules.workspace.infrastructure.components.LastDirectoryComponent;
import modules.workspace.infrastructure.components.LastFileComponent;

import modules.query.domain.services.SqlFileProgressListener;
import modules.query.application.executesqlfile.ExecuteSqlFileDto;
import modules.query.application.executesqlfile.ExecuteSqlFileResultDto;
import modules.query.application.executesqlfile.ExecuteSqlFileService;
import modules.query.infrastructure.components.RunningStatementsComponent;
import modules.query.infrastructure.components.SqlFileProgressTracker;

/**
 * "Execute Query(s) From A File" de SQLyog: elige el .sql y lo ejecuta en la BD activa de la pestaña, con su propia
 * sesion (asi no se mezcla con lo que haya en el editor), en segundo plano y con progreso; "Ejecutar" pasa a "Parar".
 * En produccion pide confirmacion antes. La carpeta del ultimo fichero se recuerda mientras la app sigue abierta.
 */
public final class ExecuteSqlFileDialogController extends AbstractFxController {

    private static final String PATH_DIALOG = "/modules/query/infrastructure/views/execute-sql-file-dialog.fxml";

    @FXML
    private Label databaseLabel;
    @FXML
    private TextField filePathField;
    @FXML
    private Button openFolderButton;
    @FXML
    private ProgressBar progressBar;
    @FXML
    private Label statusLabel;
    @FXML
    private CheckBox abortOnErrorCheckBox;
    @FXML
    private Button errorsButton;
    @FXML
    private Button executeButton;
    @FXML
    private Button closeButton;

    private String connectionUuid = "";
    private String databaseName = "";
    private String sessionKey = "";
    private boolean isRunning;
    private ExecuteSqlFileResultDto executeSqlFileResultDto;

    /**
     * Abre el dialogo (no bloquea) para ejecutar en databaseName (vacio = la BD por defecto de la conexion).
     */
    public static void open(Window owner, String connectionUuid, String databaseName) {
        FXMLLoader fxmlLoader = new FXMLLoader(ExecuteSqlFileDialogController.class.getResource(PATH_DIALOG), TranslatorResourceBundle.getInstance());
        Parent dialogRoot;
        try {
            dialogRoot = fxmlLoader.load();
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        ExecuteSqlFileDialogController executeSqlFileDialogController = fxmlLoader.getController();
        Stage dialogStage = new Stage();
        dialogStage.initOwner(owner);
        dialogStage.initModality(Modality.WINDOW_MODAL);
        dialogStage.setTitle(__("Ejecutar fichero SQL"));
        Scene dialogScene = new Scene(dialogRoot);
        ThemeComponent.getInstance().applyTo(dialogScene);
        dialogStage.setScene(dialogScene);
        dialogStage.setOnCloseRequest(event -> {
            if (executeSqlFileDialogController.isRunning) {
                event.consume();
                return;
            }
            executeSqlFileDialogController.closeSession();
        });
        executeSqlFileDialogController.setup(connectionUuid, databaseName);
        dialogStage.show();
    }

    private void setup(String connectionUuid, String databaseName) {
        this.connectionUuid = connectionUuid;
        this.databaseName = databaseName == null ? "" : databaseName;
        this.sessionKey = UuidGenerator.getInstance().getUniqueIdWithPrefix("sqlfile-") + TargetSessionFactory.SESSION_KEY_SEPARATOR + "run";
        databaseLabel.setText(this.databaseName.isEmpty() ? __("(la de la conexión)") : this.databaseName);
        // el ultimo fichero ejecutado (tambien de otras sesiones), si sigue existiendo
        File lastFile = LastFileComponent.getInstance(WorkspaceKeyEnum.SQL_FILE_LAST_FILE).getFile();
        if (lastFile != null) filePathField.setText(lastFile.getPath());
        filePathField.textProperty().addListener((observable, oldPath, newPath) -> updateOpenFolderButton());
        updateOpenFolderButton();
    }

    @FXML
    private void onChooseFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(__("Fichero a ejecutar"));
        fileChooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("SQL (*.sql)", "*.sql"),
            new FileChooser.ExtensionFilter(__("Todos"), "*.*")
        );
        File currentFile = new File(filePathField.getText().trim());
        File initialDirectory = currentFile.getParentFile() != null && currentFile.getParentFile().isDirectory()
            ? currentFile.getParentFile()
            : LastDirectoryComponent.getInstance(WorkspaceKeyEnum.SQL_FILE_DIRECTORY).getDirectory();
        if (initialDirectory != null && initialDirectory.isDirectory()) fileChooser.setInitialDirectory(initialDirectory);
        File chosenFile = fileChooser.showOpenDialog(filePathField.getScene().getWindow());
        if (chosenFile == null) return;
        filePathField.setText(chosenFile.getPath());
        LastDirectoryComponent.getInstance(WorkspaceKeyEnum.SQL_FILE_DIRECTORY).saveDirectoryOf(chosenFile);
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
    private void onExecute() {
        if (isRunning) {
            executeButton.setDisable(true);
            setStatus(__("Parando..."));
            try {
                RunningStatementsComponent.getInstance().cancel(sessionKey);
            }
            catch (SQLException e) {
                setStatus(getErrorMessage(e));
            }
            return;
        }
        runFile(false);
    }

    private void runFile(boolean isConfirmed) {
        File fileToRun = new File(filePathField.getText().trim());
        if (fileToRun.isFile()) {
            LastFileComponent.getInstance(WorkspaceKeyEnum.SQL_FILE_LAST_FILE).saveFile(fileToRun);
            LastDirectoryComponent.getInstance(WorkspaceKeyEnum.SQL_FILE_DIRECTORY).saveDirectoryOf(fileToRun);
        }
        ExecuteSqlFileDto executeSqlFileDto = ExecuteSqlFileDto.fromPrimitives(Primitives.of(
            "connectionUuid", connectionUuid,
            "sessionKey", sessionKey,
            "databaseName", databaseName,
            "filePath", filePathField.getText().trim(),
            "isAbortOnError", abortOnErrorCheckBox.isSelected(),
            "isConfirmed", isConfirmed
        ));
        setRunning(true);
        progressBar.setProgress(0);
        setStatus(__("Ejecutando..."));
        SqlFileProgressTracker sqlFileProgressTracker = SqlFileProgressTracker.getInstance();
        sqlFileProgressTracker.register(sessionKey, new DialogProgressListener());
        Task<ExecuteSqlFileResultDto> task = new Task<>() {
            @Override
            protected ExecuteSqlFileResultDto call() throws SQLException {
                return ExecuteSqlFileService.getInstance().invoke(executeSqlFileDto);
            }
        };
        task.setOnSucceeded(event -> {
            sqlFileProgressTracker.unregister(sessionKey);
            showResult(task.getValue());
        });
        task.setOnFailed(event -> {
            sqlFileProgressTracker.unregister(sessionKey);
            setRunning(false);
            Throwable throwable = task.getException();
            if (throwable instanceof InterfaceException interfaceException && interfaceException.getCode() == ExceptionEnum.CODE_PRECONDITION_REQUIRED) {
                setStatus("");
                if (isConfirmed(throwable.getMessage() + ".\n\n" + __("¿Ejecutar igualmente?"))) runFile(true);
                return;
            }
            setStatus(getErrorMessage(throwable));
        });
        Thread thread = new Thread(task, "sqlfile-execute");
        thread.setDaemon(true);
        thread.start();
    }

    private void showResult(ExecuteSqlFileResultDto resultDto) {
        setRunning(false);
        this.executeSqlFileResultDto = resultDto;
        errorsButton.setDisable(resultDto.getErrorCount() == 0);
        if (!resultDto.isCancelled() && !resultDto.isAborted()) progressBar.setProgress(1);
        String outcome = resultDto.isCancelled() ? __("Parada.") : resultDto.isAborted() ? __("Parada en un error.") : __("Hecho.");
        setStatus(outcome + " " + __("{0} sentencias ejecutadas", String.format("%,d", resultDto.getExecutedCount()))
            + (resultDto.getErrorCount() > 0 ? ", " + __("{0} con error", resultDto.getErrorCount()) : "")
            + " " + __("en {0} s.", String.format("%.1f", resultDto.getElapsedMs() / 1000.0)));
        if (resultDto.getErrorCount() > 0) onShowErrors();
    }

    @FXML
    private void onShowErrors() {
        if (executeSqlFileResultDto == null) return;
        StringBuilder errors = new StringBuilder();
        for (Map<String, Object> error : executeSqlFileResultDto.getErrors()) {
            errors.append(getTrimmedString(error.get("message"))).append("\n    ")
                .append(getTrimmedString(error.get("sql")).replace("\n", "\n    ")).append("\n\n");
        }
        long notShown = executeSqlFileResultDto.getErrorCount() - executeSqlFileResultDto.getErrors().size();
        if (notShown > 0) errors.append(__("... y {0} error(es) más", notShown)).append("\n");
        TextArea errorsTextArea = new TextArea(errors.toString());
        errorsTextArea.setEditable(false);
        errorsTextArea.getStyleClass().add("result-text");
        Stage errorsStage = new Stage();
        errorsStage.initOwner(statusLabel.getScene().getWindow());
        errorsStage.setTitle(__("Errores al ejecutar el fichero"));
        Scene errorsScene = new Scene(errorsTextArea, 680, 400);
        ThemeComponent.getInstance().applyTo(errorsScene);
        errorsStage.setScene(errorsScene);
        errorsStage.show();
    }

    @FXML
    private void onClose() {
        if (isRunning) return;
        closeSession();
        ((Stage) closeButton.getScene().getWindow()).close();
    }

    private void setRunning(boolean isRunning) {
        this.isRunning = isRunning;
        executeButton.setDisable(false);
        executeButton.setText(isRunning ? __("Parar") : __("Ejecutar"));
        closeButton.setDisable(isRunning);
        filePathField.setDisable(isRunning);
        abortOnErrorCheckBox.setDisable(isRunning);
        if (isRunning) errorsButton.setDisable(true);
    }

    private void setStatus(String status) {
        statusLabel.setText(status);
    }

    private void closeSession() {
        TargetSessionFactory.getInstance().closeSessions(sessionKey);
    }

    /**
     * La barra sigue a lo leido del fichero; el texto, a las sentencias.
     */
    private final class DialogProgressListener implements SqlFileProgressListener {

        @Override
        public void onProgress(long readChars, long totalChars, long executedStatements, long failedStatements) {
            Platform.runLater(() -> {
                progressBar.setProgress(totalChars <= 0 ? 0 : Math.min(1, readChars / (double) totalChars));
                setStatus(__("Ejecutando... {0} sentencias", String.format("%,d", executedStatements))
                    + (failedStatements > 0 ? ", " + __("{0} con error", failedStatements) : ""));
            });
        }
    }
}
