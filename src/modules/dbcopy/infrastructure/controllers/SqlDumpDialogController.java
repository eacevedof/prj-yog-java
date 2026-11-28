package modules.dbcopy.infrastructure.controllers;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.CheckBoxTreeItem;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.control.cell.CheckBoxTreeCell;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import modules.shared.domain.enums.SessionKeyEnum;
import modules.shared.application.Primitives;
import modules.shared.infrastructure.components.icons.AppIconEnum;
import modules.shared.infrastructure.components.files.FolderOpenerComponent;
import modules.shared.infrastructure.components.icons.AppIcon;
import modules.shared.infrastructure.components.theme.ThemeComponent;
import modules.shared.infrastructure.components.uuid.UuidGenerator;
import modules.shared.infrastructure.controllers.AbstractFxController;
import static modules.shared.application.Primitives.getBool;
import static modules.shared.application.Primitives.getTrimmedString;

import core.config.TranslatorResourceBundle;
import static core.config.Global.__;

import modules.connections.infrastructure.factories.TargetSessionFactory;

import modules.explorer.application.listdatabases.ListDatabasesDto;
import modules.explorer.application.listdatabases.ListDatabasesService;

import modules.workspace.domain.enums.WorkspaceKeyEnum;
import modules.workspace.infrastructure.components.LastDirectoryComponent;

import modules.dbcopy.domain.enums.DbCopyConfigEnum;
import modules.dbcopy.domain.enums.DbCopyKeyEnum;
import modules.dbcopy.domain.enums.DbCopyObjectTypeEnum;
import modules.dbcopy.domain.enums.SqlDumpModeEnum;
import modules.dbcopy.domain.services.DbCopyProgressListener;
import modules.dbcopy.application.exportsqldump.ExportSqlDumpDto;
import modules.dbcopy.application.exportsqldump.ExportSqlDumpResultDto;
import modules.dbcopy.application.listcopyobjects.ListCopyObjectsDto;
import modules.dbcopy.application.listcopyobjects.ListCopyObjectsResultDto;
import modules.dbcopy.application.exportsqldump.ExportSqlDumpService;
import modules.dbcopy.application.listcopyobjects.ListCopyObjectsService;
import modules.dbcopy.infrastructure.components.DbCopyProgressTracker;

/**
 * El "SQL Dump" de SQLyog (MySQL/MariaDB): elige BD, objetos, que va (estructura, datos o ambos), el fichero y las
 * opciones, y vuelca en segundo plano con su progreso; "Exportar" pasa a "Parar" mientras corre. Si algun objeto falla,
 * se abre el resumen. La carpeta del ultimo volcado se recuerda mientras la app sigue abierta.
 */
public final class SqlDumpDialogController extends AbstractFxController {

    private static final String PATH_DIALOG = "/modules/dbcopy/infrastructure/views/sql-dump-dialog.fxml";
    private static final String DUMP_ID_PREFIX = "sqldump-run-";

    @FXML
    private RadioButton structureOnlyRadio;
    @FXML
    private RadioButton dataOnlyRadio;
    @FXML
    private ComboBox<String> databaseComboBox;
    @FXML
    private TreeView<String> objectsTreeView;
    @FXML
    private TextField filePathField;
    @FXML
    private Button openFolderButton;
    @FXML
    private CheckBox singleTransactionCheckBox;
    @FXML
    private CheckBox useCheckBox;
    @FXML
    private CheckBox createDatabaseCheckBox;
    @FXML
    private CheckBox foreignKeyChecksCheckBox;
    @FXML
    private CheckBox bulkInsertCheckBox;
    @FXML
    private CheckBox dropCheckBox;
    @FXML
    private CheckBox ignoreDefinerCheckBox;
    @FXML
    private CheckBox versionCheckBox;
    @FXML
    private CheckBox timestampPrefixCheckBox;
    @FXML
    private ProgressBar progressBar;
    @FXML
    private Label statusLabel;
    @FXML
    private Button summaryButton;
    @FXML
    private Button exportButton;
    @FXML
    private Button closeButton;

    private final Map<String, CheckBoxTreeItem<String>> folderItemsByType = new LinkedHashMap<>();
    private String connectionUuid = "";
    private String dialogSessionPrefix = "";
    private boolean isRunning;
    private volatile boolean isCancelRequested;
    private ExportSqlDumpResultDto exportSqlDumpResultDto;

    /**
     * Abre el dialogo (no bloquea) con la BD indicada elegida y todos sus objetos marcados.
     */
    public static void open(Window owner, String connectionUuid, String databaseName) {
        FXMLLoader fxmlLoader = new FXMLLoader(SqlDumpDialogController.class.getResource(PATH_DIALOG), TranslatorResourceBundle.getInstance());
        Parent dialogRoot;
        try {
            dialogRoot = fxmlLoader.load();
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        SqlDumpDialogController sqlDumpDialogController = fxmlLoader.getController();
        Stage dialogStage = new Stage();
        dialogStage.initOwner(owner);
        dialogStage.initModality(Modality.WINDOW_MODAL);
        dialogStage.setTitle(__("Volcado SQL"));
        Scene dialogScene = new Scene(dialogRoot);
        ThemeComponent.getInstance().applyTo(dialogScene);
        dialogStage.setScene(dialogScene);
        dialogStage.setOnCloseRequest(event -> {
            if (sqlDumpDialogController.isRunning) {
                // cerrar la ventana a mitad es lo mismo que Parar (como en Copiar BD): se para y el resumen dice hasta donde llego
                sqlDumpDialogController.onExport();
                event.consume();
                return;
            }
            sqlDumpDialogController.closeSessions();
        });
        sqlDumpDialogController.setup(connectionUuid, databaseName);
        dialogStage.show();
    }

    private void setup(String connectionUuid, String databaseName) {
        this.connectionUuid = connectionUuid;
        this.dialogSessionPrefix = UuidGenerator.getInstance().getUniqueIdWithPrefix(DbCopyConfigEnum.DUMP_DIALOG_SESSION_PREFIX);
        filePathField.textProperty().addListener((observable, oldPath, newPath) -> updateOpenFolderButton());
        updateOpenFolderButton();
        objectsTreeView.setCellFactory(CheckBoxTreeCell.forTreeView());
        objectsTreeView.setRoot(new CheckBoxTreeItem<>(""));
        databaseComboBox.valueProperty().addListener((observable, oldDatabase, newDatabase) -> {
            if (newDatabase == null || newDatabase.isEmpty()) return;
            loadObjects(newDatabase);
            setDefaultFilePath(newDatabase);
        });
        loadDatabases(databaseName);
    }

    private void loadDatabases(String databaseName) {
        ListDatabasesDto listDatabasesDto = ListDatabasesDto.fromPrimitives(Primitives.of(
            "connectionUuid", connectionUuid,
            "sessionKey", dialogSessionPrefix + SessionKeyEnum.SEPARATOR + SessionKeyEnum.SOURCE
        ));
        setStatus(__("Leyendo las BD..."));
        runInBackground(
            () -> ListDatabasesService.getInstance().invoke(listDatabasesDto).getDatabaseNames(),
            databaseNames -> {
                databaseComboBox.getItems().setAll(databaseNames);
                setStatus("");
                if (databaseNames.contains(databaseName)) databaseComboBox.setValue(databaseName);
                else if (!databaseNames.isEmpty()) databaseComboBox.setValue(databaseNames.getFirst());
            },
            throwable -> setStatus(getErrorMessage(throwable)),
            "sqldump-list-databases"
        );
    }

    private void loadObjects(String databaseName) {
        setStatus(__("Leyendo los objetos de {0}...", databaseName));
        ListCopyObjectsDto listCopyObjectsDto = ListCopyObjectsDto.fromPrimitives(Primitives.of(
            "connectionUuid", connectionUuid,
            "sessionKey", dialogSessionPrefix + SessionKeyEnum.SEPARATOR + SessionKeyEnum.SOURCE,
            "databaseName", databaseName
        ));
        runInBackground(
            () -> ListCopyObjectsService.getInstance().invoke(listCopyObjectsDto),
            listCopyObjectsResultDto -> {
                if (!databaseName.equals(databaseComboBox.getValue())) return;
                showObjects(listCopyObjectsResultDto);
                setStatus("");
            },
            throwable -> setStatus(getErrorMessage(throwable)),
            "sqldump-list-objects"
        );
    }

    private void showObjects(ListCopyObjectsResultDto listCopyObjectsResultDto) {
        TreeItem<String> rootItem = objectsTreeView.getRoot();
        rootItem.getChildren().clear();
        folderItemsByType.clear();
        for (String objectType : DbCopyObjectTypeEnum.VALUES) {
            List<String> names = listCopyObjectsResultDto.getNames(objectType);
            CheckBoxTreeItem<String> folderItem = new CheckBoxTreeItem<>(getTypeLabel(objectType) + " (" + names.size() + ")", getTypeIcon(objectType));
            for (String name : names) {
                CheckBoxTreeItem<String> objectItem = new CheckBoxTreeItem<>(name, getTypeIcon(objectType));
                folderItem.getChildren().add(objectItem);
                objectItem.setSelected(true);
            }
            folderItemsByType.put(objectType, folderItem);
            rootItem.getChildren().add(folderItem);
        }
    }

    /**
     * &lt;carpeta&gt;/&lt;bd&gt;.sql: la carpeta que ya hay escrita o, al abrir, la ultima guardada (tambien de otras
     * sesiones) o la del usuario.
     */
    private void setDefaultFilePath(String databaseName) {
        File currentDirectory = new File(filePathField.getText().trim()).getAbsoluteFile().getParentFile();
        File directory = !filePathField.getText().isBlank() && currentDirectory != null && currentDirectory.isDirectory()
            ? currentDirectory
            : LastDirectoryComponent.getInstance(WorkspaceKeyEnum.SQL_DUMP_DIRECTORY).getDirectory();
        filePathField.setText(new File(directory, databaseName + ".sql").getPath());
    }

    @FXML
    private void onChooseFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(__("Exportar a"));
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

    /**
     * Solo se puede abrir si existe la carpeta (o alguna de sus padres).
     */
    private void updateOpenFolderButton() {
        openFolderButton.setDisable(FolderOpenerComponent.getInstance().getExistingFolder(filePathField.getText()) == null);
    }

    @FXML
    private void onExport() {
        if (isRunning) {
            isCancelRequested = true;
            exportButton.setDisable(true);
            setStatus(__("Parando en cuanto acabe la página en curso..."));
            return;
        }
        String dumpId = UuidGenerator.getInstance().getUniqueIdWithPrefix(DUMP_ID_PREFIX);
        String dumpMode = structureOnlyRadio.isSelected() ? SqlDumpModeEnum.STRUCTURE_ONLY : dataOnlyRadio.isSelected() ? SqlDumpModeEnum.DATA_ONLY : SqlDumpModeEnum.STRUCTURE_AND_DATA;
        ExportSqlDumpDto exportSqlDumpDto = ExportSqlDumpDto.fromPrimitives(Primitives.of(
            "dumpId", dumpId,
            "connectionUuid", connectionUuid,
            "databaseName", databaseComboBox.getValue(),
            "filePath", filePathField.getText().trim(),
            "dumpMode", dumpMode,
            "objects", getCheckedObjects(),
            "isSingleTransaction", singleTransactionCheckBox.isSelected(),
            "isUseIncluded", useCheckBox.isSelected(),
            "isCreateDatabaseIncluded", createDatabaseCheckBox.isSelected(),
            "isForeignKeyChecksOff", foreignKeyChecksCheckBox.isSelected(),
            "isBulkInsert", bulkInsertCheckBox.isSelected(),
            "isDropIncluded", dropCheckBox.isSelected(),
            "isIgnoreDefiner", ignoreDefinerCheckBox.isSelected(),
            "isVersionIncluded", versionCheckBox.isSelected(),
            "isTimestampPrefix", timestampPrefixCheckBox.isSelected()
        ));
        setRunning(true);
        progressBar.setProgress(0);
        setStatus(__("Exportando..."));
        DbCopyProgressTracker dbCopyProgressTracker = DbCopyProgressTracker.getInstance();
        dbCopyProgressTracker.register(dumpId, new DialogProgressListener());
        runInBackground(
            () -> ExportSqlDumpService.getInstance().invoke(exportSqlDumpDto),
            exportSqlDumpResultDto -> {
                dbCopyProgressTracker.unregister(dumpId);
                showResult(exportSqlDumpResultDto);
            },
            throwable -> {
                dbCopyProgressTracker.unregister(dumpId);
                setRunning(false);
                setStatus(getErrorMessage(throwable));
            },
            "sqldump-export"
        );
    }

    private void showResult(ExportSqlDumpResultDto exportSqlDumpResultDto) {
        setRunning(false);
        this.exportSqlDumpResultDto = exportSqlDumpResultDto;
        summaryButton.setDisable(false);
        progressBar.setProgress(exportSqlDumpResultDto.isCancelled() ? progressBar.getProgress() : 1);
        LastDirectoryComponent.getInstance(WorkspaceKeyEnum.SQL_DUMP_DIRECTORY).saveDirectoryOf(new File(exportSqlDumpResultDto.getFilePath()));
        setStatus((exportSqlDumpResultDto.isCancelled() ? __("Parada.") : __("Hecho.")) + " "
            + __("{0} objetos", exportSqlDumpResultDto.getOkCount()) + ", " + __("{0} filas", String.format("%,d", exportSqlDumpResultDto.getDumpedRows()))
            + (exportSqlDumpResultDto.getErrorCount() > 0 ? ", " + __("{0} con error", exportSqlDumpResultDto.getErrorCount()) : "")
            + " " + __("en {0} s.", String.format("%.1f", exportSqlDumpResultDto.getElapsedMs() / 1000.0))
            + " → " + exportSqlDumpResultDto.getFilePath());
        if (exportSqlDumpResultDto.getErrorCount() > 0) onShowSummary();
    }

    @FXML
    private void onShowSummary() {
        if (exportSqlDumpResultDto == null) return;
        StringBuilder summary = new StringBuilder(exportSqlDumpResultDto.getFilePath()).append("\n\n");
        for (Map<String, Object> object : exportSqlDumpResultDto.getObjects()) {
            boolean isOk = getBool(object.get(DbCopyKeyEnum.IS_OK));
            String objectType = getTrimmedString(object.get(DbCopyKeyEnum.OBJECT_TYPE));
            summary.append(String.format("%-7s", isOk ? __("OK") : __("ERROR"))).append(getTypeName(objectType)).append(" ").append(getTrimmedString(object.get(DbCopyKeyEnum.OBJECT_NAME)));
            if (DbCopyObjectTypeEnum.TABLE.equals(objectType) && object.get(DbCopyKeyEnum.COPIED_ROWS) instanceof Number dumpedRows) {
                summary.append("  ").append(__("({0} filas)", String.format("%,d", dumpedRows.longValue())));
            }
            String message = getTrimmedString(object.get(DbCopyKeyEnum.MESSAGE));
            if (!message.isEmpty()) summary.append("\n       ").append(message.replace("\n", "\n       "));
            summary.append("\n");
        }
        TextArea summaryTextArea = new TextArea(summary.toString());
        summaryTextArea.setEditable(false);
        summaryTextArea.getStyleClass().add("result-text");
        Stage summaryStage = new Stage();
        summaryStage.initOwner(statusLabel.getScene().getWindow());
        summaryStage.setTitle(__("Resumen del volcado"));
        Scene summaryScene = new Scene(summaryTextArea, 640, 400);
        ThemeComponent.getInstance().applyTo(summaryScene);
        summaryStage.setScene(summaryScene);
        summaryStage.show();
    }

    @FXML
    private void onClose() {
        if (isRunning) return;
        closeSessions();
        ((Stage) closeButton.getScene().getWindow()).close();
    }

    private Map<String, Object> getCheckedObjects() {
        Map<String, Object> checkedObjects = new LinkedHashMap<>();
        folderItemsByType.forEach((objectType, folderItem) -> {
            List<String> names = new ArrayList<>();
            for (TreeItem<String> objectItem : folderItem.getChildren()) {
                if (objectItem instanceof CheckBoxTreeItem<String> checkBoxItem && checkBoxItem.isSelected()) names.add(objectItem.getValue());
            }
            checkedObjects.put(objectType, names);
        });
        return checkedObjects;
    }

    private void setRunning(boolean isRunning) {
        this.isRunning = isRunning;
        isCancelRequested = false;
        exportButton.setDisable(false);
        exportButton.setText(isRunning ? __("Parar") : __("Exportar"));
        closeButton.setDisable(isRunning);
        summaryButton.setDisable(isRunning || exportSqlDumpResultDto == null);
        for (Node node : List.of(databaseComboBox, objectsTreeView, filePathField, singleTransactionCheckBox, useCheckBox, createDatabaseCheckBox,
            foreignKeyChecksCheckBox, bulkInsertCheckBox, dropCheckBox, ignoreDefinerCheckBox, versionCheckBox, timestampPrefixCheckBox)) {
            node.setDisable(isRunning);
        }
    }

    private void setStatus(String status) {
        statusLabel.setText(status);
    }

    private void closeSessions() {
        TargetSessionFactory.getInstance().closeSessions(dialogSessionPrefix);
    }

    private AppIcon getTypeIcon(String objectType) {
        return AppIcon.of(switch (objectType) {
            case DbCopyObjectTypeEnum.VIEW -> AppIconEnum.VIEW;
            case DbCopyObjectTypeEnum.PROCEDURE -> AppIconEnum.PROCEDURE;
            case DbCopyObjectTypeEnum.FUNCTION -> AppIconEnum.FUNCTION;
            case DbCopyObjectTypeEnum.TRIGGER -> AppIconEnum.TRIGGER;
            case DbCopyObjectTypeEnum.EVENT -> AppIconEnum.HISTORY;
            default -> AppIconEnum.TABLE;
        });
    }

    private String getTypeLabel(String objectType) {
        return switch (objectType) {
            case DbCopyObjectTypeEnum.VIEW -> __("Vistas");
            case DbCopyObjectTypeEnum.PROCEDURE -> __("Procedimientos");
            case DbCopyObjectTypeEnum.FUNCTION -> __("Funciones");
            case DbCopyObjectTypeEnum.TRIGGER -> __("Triggers");
            case DbCopyObjectTypeEnum.EVENT -> __("Eventos");
            default -> __("Tablas");
        };
    }

    /**
     * El tipo en singular para el resumen: "tabla customers", "vista v_active"...
     */
    private String getTypeName(String objectType) {
        return switch (objectType) {
            case DbCopyObjectTypeEnum.VIEW -> __("vista");
            case DbCopyObjectTypeEnum.PROCEDURE -> __("procedimiento");
            case DbCopyObjectTypeEnum.FUNCTION -> __("función");
            case DbCopyObjectTypeEnum.TRIGGER -> __("trigger");
            case DbCopyObjectTypeEnum.EVENT -> __("evento");
            default -> __("tabla");
        };
    }

    private <T> void runInBackground(Callable<T> work, Consumer<T> onSucceeded, Consumer<Throwable> onFailed, String threadName) {
        Task<T> backgroundTask = new Task<>() {
            @Override
            protected T call() throws Exception {
                return work.call();
            }
        };
        backgroundTask.setOnSucceeded(event -> onSucceeded.accept(backgroundTask.getValue()));
        backgroundTask.setOnFailed(event -> onFailed.accept(backgroundTask.getException()));
        Thread daemonThread = new Thread(backgroundTask, threadName);
        daemonThread.setDaemon(true);
        daemonThread.start();
    }

    /**
     * La barra avanza por objetos y, dentro de una tabla, por filas (sobre la estimacion del servidor).
     */
    private final class DialogProgressListener implements DbCopyProgressListener {

        private int objectIndex;
        private int objectCount;

        @Override
        public void onObjectStarted(String objectType, String objectName, int objectIndex, int objectCount) {
            this.objectIndex = objectIndex;
            this.objectCount = objectCount;
            Platform.runLater(() -> {
                progressBar.setProgress(objectCount == 0 ? 0 : (objectIndex - 1) / (double) objectCount);
                setStatus(__("Exportando {0} ({1}/{2})...", objectName, objectIndex, objectCount));
            });
        }

        @Override
        public void onRowsCopied(String tableName, long copiedRows, long estimatedRows) {
            Platform.runLater(() -> {
                double tableShare = estimatedRows <= 0 ? 0 : Math.min(1, copiedRows / (double) estimatedRows);
                progressBar.setProgress(objectCount == 0 ? 0 : (objectIndex - 1 + tableShare) / objectCount);
                setStatus(__("Exportando {0}: {1} filas", tableName, String.format("%,d", copiedRows)));
            });
        }

        @Override
        public boolean isCancelled() {
            return isCancelRequested;
        }
    }
}
