package modules.dbcopy.infrastructure.controllers;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.CheckBoxTreeItem;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextArea;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.control.cell.CheckBoxTreeCell;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import modules.shared.domain.enums.ExceptionEnum;
import modules.shared.application.Primitives;
import modules.shared.infrastructure.components.icons.AppIconEnum;
import modules.shared.infrastructure.components.date.DateTimer;
import modules.shared.infrastructure.components.icons.AppIcon;
import modules.shared.infrastructure.components.theme.ThemeComponent;
import modules.shared.infrastructure.components.uuid.UuidGenerator;
import modules.shared.infrastructure.controllers.AbstractFxController;
import modules.shared.infrastructure.exceptions.InterfaceException;
import static modules.shared.application.Primitives.getBool;
import static modules.shared.application.Primitives.getTrimmedString;

import core.config.TranslatorResourceBundle;
import static core.config.Global.__;

import modules.connections.domain.enums.ConnectionsKeyEnum;
import modules.connections.domain.enums.DbEngineEnum;
import modules.connections.application.listconnections.ListConnectionsService;
import modules.connections.infrastructure.components.EngineIconComponent;
import modules.connections.infrastructure.factories.TargetSessionFactory;

import modules.explorer.application.listdatabases.ListDatabasesDto;
import modules.explorer.application.listdatabases.ListDatabasesService;

import modules.dbcopy.domain.enums.DbCopyKeyEnum;
import modules.dbcopy.domain.enums.DbCopyObjectTypeEnum;
import modules.dbcopy.domain.services.DatabaseCopyNameBuilder;
import modules.dbcopy.domain.services.DbCopyProgressListener;
import modules.dbcopy.application.copydatabase.CopyDatabaseDto;
import modules.dbcopy.application.copydatabase.CopyDatabaseResultDto;
import modules.dbcopy.application.listcopyobjects.ListCopyObjectsDto;
import modules.dbcopy.application.listcopyobjects.ListCopyObjectsResultDto;
import modules.dbcopy.application.copydatabase.CopyDatabaseService;
import modules.dbcopy.application.listcopyobjects.ListCopyObjectsService;
import modules.dbcopy.infrastructure.components.DbCopyProgressTracker;

/**
 * Feature 29: el dialogo "Copiar BD" de SQLyog. A la izquierda los objetos de la BD origen con casillas (todo
 * marcado, o solo la tabla desde la que se abrio); a la derecha la conexion y la BD destino y las opciones. La copia
 * corre en segundo plano con barra de progreso y se puede parar; al acabar, el resumen por objeto.
 */
public final class CopyDatabaseDialogController extends AbstractFxController {

    private static final String PATH_DIALOG = "/modules/dbcopy/infrastructure/views/copy-database-dialog.fxml";
    private static final String NO_COLOR_HEX = "#9e9e9e";
    private static final String COPY_ID_PREFIX = "dbcopy-run-";
    private static final String STYLE_STATUS_ERROR = "form-message-error";
    private static final String STYLE_STATUS_OK = "form-message-ok";

    @FXML
    private Label sourceNameLabel;
    @FXML
    private TreeView<String> objectsTreeView;
    @FXML
    private ComboBox<Map<String, Object>> targetConnectionComboBox;
    @FXML
    private Label engineHintLabel;
    @FXML
    private ComboBox<String> targetDatabaseComboBox;
    @FXML
    private RadioButton structureOnlyRadio;
    @FXML
    private CheckBox dropIfExistsCheckBox;
    @FXML
    private CheckBox bulkInsertCheckBox;
    @FXML
    private CheckBox ignoreDefinerCheckBox;
    @FXML
    private CheckBox copyTriggersCheckBox;
    @FXML
    private ProgressBar progressBar;
    @FXML
    private Label statusLabel;
    @FXML
    private Button summaryButton;
    @FXML
    private Button copyButton;
    @FXML
    private Button closeButton;

    private final Map<String, CheckBoxTreeItem<String>> folderItemsByType = new LinkedHashMap<>();
    private String dialogSessionPrefix = "";
    private String sourceConnectionUuid = "";
    private String sourceDatabase = "";
    private String sourceEngine = DbEngineEnum.MYSQL;
    private volatile boolean isCancelRequested;
    private boolean isRunning;
    private CopyDatabaseResultDto copyDatabaseResultDto;
    // "" = otra conexion por defecto (lo normal es copiar entre servidores); al duplicar, la misma
    private String preferredTargetConnectionUuid = "";
    private Runnable onCopied = () -> { };
    // null = sin filtro (Duplicar BD va a la misma conexion)
    private Set<String> openConnectionUuids = null;
    private Stage dialogStage;

    /**
     * Abre el dialogo (no bloquea). preselectedTable vacio = toda la BD marcada; si no, solo esa tabla.
     */
    public static void open(Window owner, String sourceConnectionUuid, String sourceDatabase, String preselectedTable, Set<String> openConnectionUuids) {
        String title = preselectedTable.isEmpty() ? __("Copiar BD a otro servidor/BD") : __("Copiar tabla a otro servidor/BD");
        CopyDatabaseDialogController copyDatabaseDialogController = getLoadedController(owner, title);
        copyDatabaseDialogController.openConnectionUuids = openConnectionUuids;
        copyDatabaseDialogController.setup(sourceConnectionUuid, sourceDatabase, preselectedTable, sourceDatabase);
        copyDatabaseDialogController.dialogStage.show();
    }

    /**
     * "Duplicar BD": el mismo dialogo con el destino ya puesto, la misma conexion y &lt;bd&gt;_copy_&lt;ymdhis&gt;
     * (se crea). onDuplicated corre al acabar la copia, para recargar el arbol.
     */
    public static void openDuplicate(Window owner, String sourceConnectionUuid, String sourceDatabase, Runnable onDuplicated) {
        CopyDatabaseDialogController copyDatabaseDialogController = getLoadedController(owner, __("Duplicar BD"));
        copyDatabaseDialogController.preferredTargetConnectionUuid = sourceConnectionUuid;
        copyDatabaseDialogController.onCopied = onDuplicated;
        String copyDatabaseName = DatabaseCopyNameBuilder.getInstance().getCopyDatabaseName(sourceDatabase, DateTimer.getInstance().getNowDt());
        copyDatabaseDialogController.setup(sourceConnectionUuid, sourceDatabase, "", copyDatabaseName);
        copyDatabaseDialogController.dialogStage.show();
    }

    private static CopyDatabaseDialogController getLoadedController(Window owner, String title) {
        FXMLLoader fxmlLoader = new FXMLLoader(CopyDatabaseDialogController.class.getResource(PATH_DIALOG), TranslatorResourceBundle.getInstance());
        Parent dialogRoot;
        try {
            dialogRoot = fxmlLoader.load();
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        CopyDatabaseDialogController copyDatabaseDialogController = fxmlLoader.getController();
        Stage dialogStage = new Stage();
        dialogStage.initOwner(owner);
        dialogStage.initModality(Modality.WINDOW_MODAL);
        dialogStage.setTitle(title);
        Scene dialogScene = new Scene(dialogRoot);
        ThemeComponent.getInstance().applyTo(dialogScene);
        dialogStage.setScene(dialogScene);
        dialogStage.setOnCloseRequest(event -> {
            if (copyDatabaseDialogController.isRunning) {
                copyDatabaseDialogController.isCancelRequested = true;
                event.consume();
                return;
            }
            copyDatabaseDialogController.closeSessions();
        });
        copyDatabaseDialogController.dialogStage = dialogStage;
        return copyDatabaseDialogController;
    }

    /**
     * targetDatabase: el nombre que aparece escrito en la BD destino (una nueva se escribe a mano).
     */
    private void setup(String sourceConnectionUuid, String sourceDatabase, String preselectedTable, String targetDatabase) {
        this.sourceConnectionUuid = sourceConnectionUuid;
        this.sourceDatabase = sourceDatabase;
        this.sourceEngine = TargetSessionFactory.getInstance().getDbEngine(sourceConnectionUuid);
        this.dialogSessionPrefix = UuidGenerator.getInstance().getUniqueIdWithPrefix("dbcopy-dialog-");
        objectsTreeView.setCellFactory(CheckBoxTreeCell.forTreeView());
        objectsTreeView.setRoot(new CheckBoxTreeItem<>(""));
        targetConnectionComboBox.setCellFactory(listView -> new ConnectionCell());
        targetConnectionComboBox.setButtonCell(new ConnectionCell());
        targetConnectionComboBox.valueProperty().addListener((observable, oldValue, newValue) -> {
            applyEngineMode();
            loadTargetDatabases(newValue);
        });
        targetDatabaseComboBox.getEditor().setText(targetDatabase);

        loadConnections();
        loadSourceObjects(preselectedTable);
    }

    private void loadConnections() {
        List<Map<String, Object>> connections = ListConnectionsService.getInstance().invoke().getConnections();
        Map<String, Object> sourceConnection = null;
        Map<String, Object> firstOtherSameEngine = null;
        Map<String, Object> firstOtherConnection = null;
        for (Map<String, Object> connection : connections) {
            String connectionUuid = getTrimmedString(connection.get(ConnectionsKeyEnum.CONNECTION_UUID));
            String dbEngine = getTrimmedString(connection.get(ConnectionsKeyEnum.DB_ENGINE));
            boolean isSource = connectionUuid.equals(sourceConnectionUuid);
            if (isSource) {
                sourceConnection = connection;
                sourceNameLabel.setText(getTrimmedString(connection.get(ConnectionsKeyEnum.CONNECTION_NAME)) + " - " + sourceDatabase);
            }
            // destino: cualquier motor SQL (Ficheros no: sus tablas nuevas no tendrian fichero)
            if (!DbEngineEnum.isSql(dbEngine)) continue;
            // destino: solo las conexiones abiertas (y la de origen, que lo esta)
            if (!isSource && openConnectionUuids != null && !openConnectionUuids.contains(connectionUuid)) continue;
            targetConnectionComboBox.getItems().add(connection);
            if (isSource) continue;
            if (firstOtherConnection == null) firstOtherConnection = connection;
            if (firstOtherSameEngine == null && dbEngine.equals(sourceEngine)) firstOtherSameEngine = connection;
        }
        if (sourceConnection != null && !targetConnectionComboBox.getItems().contains(sourceConnection)) sourceConnection = null;
        // por defecto, otra conexion del mismo motor (lo normal es copiar entre servidores); si no hay, otra cualquiera;
        // al duplicar, la misma
        Map<String, Object> otherConnection = firstOtherSameEngine != null ? firstOtherSameEngine : firstOtherConnection;
        boolean isSameConnectionPreferred = sourceConnectionUuid.equals(preferredTargetConnectionUuid);
        Map<String, Object> defaultTarget = otherConnection != null && !isSameConnectionPreferred ? otherConnection : sourceConnection;
        if (defaultTarget != null) targetConnectionComboBox.getSelectionModel().select(defaultTarget);
    }

    private void loadSourceObjects(String preselectedTable) {
        setStatus(__("Leyendo los objetos de {0}...", sourceDatabase));
        ListCopyObjectsDto listCopyObjectsDto = ListCopyObjectsDto.fromPrimitives(Primitives.of(
            "connectionUuid", sourceConnectionUuid,
            "sessionKey", dialogSessionPrefix + TargetSessionFactory.SESSION_KEY_SEPARATOR + "source",
            "databaseName", sourceDatabase
        ));
        runInBackground(
            () -> ListCopyObjectsService.getInstance().invoke(listCopyObjectsDto),
            listCopyObjectsResultDto -> {
                showSourceObjects(listCopyObjectsResultDto, preselectedTable);
                setStatus("");
            },
            throwable -> setStatus(getErrorMessage(throwable)),
            "dbcopy-list-objects"
        );
    }

    private void showSourceObjects(ListCopyObjectsResultDto listCopyObjectsResultDto, String preselectedTable) {
        TreeItem<String> rootItem = objectsTreeView.getRoot();
        rootItem.getChildren().clear();
        folderItemsByType.clear();
        boolean isWholeDatabase = preselectedTable.isEmpty();
        for (String objectType : DbCopyObjectTypeEnum.VALUES) {
            List<String> names = listCopyObjectsResultDto.getNames(objectType);
            CheckBoxTreeItem<String> folderItem = new CheckBoxTreeItem<>(
                getTypeLabel(objectType) + " (" + names.size() + ")", getTypeIcon(objectType)
            );
            for (String name : names) {
                CheckBoxTreeItem<String> objectItem = new CheckBoxTreeItem<>(name, getTypeIcon(objectType));
                // se marca despues de colgarlo: asi la carpeta se entera y queda marcada o a medias
                folderItem.getChildren().add(objectItem);
                objectItem.setSelected(isWholeDatabase || (DbCopyObjectTypeEnum.TABLE.equals(objectType) && name.equals(preselectedTable)));
            }
            folderItem.setExpanded(DbCopyObjectTypeEnum.TABLE.equals(objectType) && !isWholeDatabase);
            folderItemsByType.put(objectType, folderItem);
            rootItem.getChildren().add(folderItem);
        }
        if (!isWholeDatabase) copyTriggersCheckBox.setSelected(true);
        applyEngineMode();
    }

    /**
     * MySQL a MySQL copia todo; entre motores (o entre dos que no son MySQL) solo tablas: lo demas se desmarca y las
     * opciones de MySQL (DEFINER, triggers asociados) no aplican.
     */
    private void applyEngineMode() {
        boolean isBetweenEngines = isBetweenEngines();
        engineHintLabel.setVisible(isBetweenEngines);
        engineHintLabel.setManaged(isBetweenEngines);
        ignoreDefinerCheckBox.setDisable(isRunning || isBetweenEngines);
        copyTriggersCheckBox.setDisable(isRunning || isBetweenEngines);
        if (!isBetweenEngines) return;
        folderItemsByType.forEach((objectType, folderItem) -> {
            if (DbCopyObjectTypeEnum.TABLE.equals(objectType)) return;
            folderItem.setSelected(false);
            for (TreeItem<String> objectItem : folderItem.getChildren()) {
                if (objectItem instanceof CheckBoxTreeItem<String> checkBoxItem) checkBoxItem.setSelected(false);
            }
        });
    }

    private boolean isBetweenEngines() {
        Map<String, Object> targetConnection = targetConnectionComboBox.getValue();
        String targetEngine = targetConnection == null ? DbEngineEnum.MYSQL : getTrimmedString(targetConnection.get(ConnectionsKeyEnum.DB_ENGINE));
        return !DbEngineEnum.MYSQL.equals(sourceEngine) || !DbEngineEnum.MYSQL.equals(targetEngine);
    }

    private void loadTargetDatabases(Map<String, Object> targetConnection) {
        if (targetConnection == null) return;
        String typedDatabase = targetDatabaseComboBox.getEditor().getText();
        String connectionUuid = getTrimmedString(targetConnection.get(ConnectionsKeyEnum.CONNECTION_UUID));
        ListDatabasesDto listDatabasesDto = ListDatabasesDto.fromPrimitives(Primitives.of(
            "connectionUuid", connectionUuid,
            "sessionKey", dialogSessionPrefix + TargetSessionFactory.SESSION_KEY_SEPARATOR + "target-" + connectionUuid
        ));
        targetDatabaseComboBox.getItems().clear();
        setStatus(__("Leyendo las BD del destino..."));
        runInBackground(
            () -> ListDatabasesService.getInstance().invoke(listDatabasesDto).getDatabaseNames(),
            databaseNames -> {
                if (!connectionUuid.equals(getTargetConnectionUuid())) return;
                // en la misma conexion, la BD de origen no puede ser destino (la copia la borraria)
                boolean isSourceConnection = connectionUuid.equals(sourceConnectionUuid);
                List<String> targetNames = isSourceConnection
                    ? databaseNames.stream().filter(databaseName -> !databaseName.equals(sourceDatabase)).toList()
                    : databaseNames;
                targetDatabaseComboBox.getItems().setAll(targetNames);
                String targetDatabase = typedDatabase;
                if (isSourceConnection && typedDatabase.equals(sourceDatabase)) targetDatabase = "";
                else if (!isSourceConnection && typedDatabase.isBlank()) targetDatabase = sourceDatabase;
                // SQLite no crea BDs: solo vale la suya (main)
                boolean isSqliteTarget = DbEngineEnum.SQLITE.equals(getTrimmedString(targetConnection.get(ConnectionsKeyEnum.DB_ENGINE)));
                if (isSqliteTarget && !targetNames.contains(targetDatabase) && !targetNames.isEmpty()) targetDatabase = targetNames.getFirst();
                targetDatabaseComboBox.getEditor().setText(targetDatabase);
                setStatus(databaseNames.contains(targetDatabase) || targetDatabase.isBlank() ? "" : __("La BD {0} no existe en el destino: se creará.", targetDatabase));
            },
            throwable -> setStatus(getErrorMessage(throwable)),
            "dbcopy-list-target-databases"
        );
    }

    @FXML
    private void onCopy() {
        if (isRunning) {
            isCancelRequested = true;
            copyButton.setDisable(true);
            setStatus(__("Parando en cuanto acabe la página en curso..."));
            return;
        }
        runCopy(false);
    }

    /**
     * La copia corre en segundo plano; su progreso y el Parar llegan por DbCopyProgressTracker con un id de copia propio.
     */
    private void runCopy(boolean isConfirmed) {
        String copyId = UuidGenerator.getInstance().getUniqueIdWithPrefix(COPY_ID_PREFIX);
        CopyDatabaseDto copyDatabaseDto = CopyDatabaseDto.fromPrimitives(Primitives.of(
            "copyId", copyId,
            "sourceConnectionUuid", sourceConnectionUuid,
            "sourceDatabase", sourceDatabase,
            "targetConnectionUuid", getTargetConnectionUuid(),
            "targetDatabase", targetDatabaseComboBox.getEditor().getText(),
            "objects", getCheckedObjects(),
            "isStructureOnly", structureOnlyRadio.isSelected(),
            "isDropIfExists", dropIfExistsCheckBox.isSelected(),
            "isBulkInsert", bulkInsertCheckBox.isSelected(),
            "isIgnoreDefiner", ignoreDefinerCheckBox.isSelected(),
            "isCopyAssociatedTriggers", copyTriggersCheckBox.isSelected(),
            "isConfirmed", isConfirmed
        ));
        setRunning(true);
        progressBar.setProgress(0);
        setStatus(__("Copiando..."));
        DbCopyProgressTracker dbCopyProgressTracker = DbCopyProgressTracker.getInstance();
        dbCopyProgressTracker.register(copyId, new DialogProgressListener());
        runInBackground(
            () -> CopyDatabaseService.getInstance().invoke(copyDatabaseDto),
            copyDatabaseResultDto -> {
                dbCopyProgressTracker.unregister(copyId);
                showResult(copyDatabaseResultDto);
            },
            throwable -> {
                dbCopyProgressTracker.unregister(copyId);
                setRunning(false);
                if (throwable instanceof InterfaceException interfaceException && interfaceException.getCode() == ExceptionEnum.CODE_PRECONDITION_REQUIRED) {
                    setStatus("");
                    if (isConfirmed(throwable.getMessage() + ".\n\n" + __("¿Copiar igualmente?"))) runCopy(true);
                    return;
                }
                setErrorStatus(getErrorMessage(throwable));
            },
            "dbcopy-copy"
        );
    }

    private void showResult(CopyDatabaseResultDto copyDatabaseResultDto) {
        setRunning(false);
        this.copyDatabaseResultDto = copyDatabaseResultDto;
        summaryButton.setDisable(false);
        progressBar.setProgress(copyDatabaseResultDto.isCancelled() ? progressBar.getProgress() : 1);
        String status = (copyDatabaseResultDto.isCancelled() ? __("Parada.") : __("Hecho.")) + " "
            + __("{0} objetos copiados", copyDatabaseResultDto.getOkCount()) + ", " + __("{0} filas", String.format("%,d", copyDatabaseResultDto.getCopiedRows()))
            + (copyDatabaseResultDto.getErrorCount() > 0 ? ", " + __("{0} con error", copyDatabaseResultDto.getErrorCount()) : "")
            + " " + __("en {0} s.", String.format("%.1f", copyDatabaseResultDto.getElapsedMs() / 1000.0));
        if (copyDatabaseResultDto.getErrorCount() > 0) {
            setErrorStatus(status + "\n" + getFirstError(copyDatabaseResultDto));
            onCopied.run();
            onShowSummary();
            return;
        }
        if (copyDatabaseResultDto.isCancelled()) setStatus(status);
        else setOkStatus(status);
        onCopied.run();
    }

    @FXML
    private void onShowSummary() {
        if (copyDatabaseResultDto == null) return;
        StringBuilder summary = new StringBuilder();
        for (Map<String, Object> object : copyDatabaseResultDto.getObjects()) {
            boolean isOk = getBool(object.get(DbCopyKeyEnum.IS_OK));
            String objectType = getTrimmedString(object.get(DbCopyKeyEnum.OBJECT_TYPE));
            summary.append(isOk ? "OK     " : "ERROR  ")
                .append(getTypeName(objectType)).append(" ")
                .append(getTrimmedString(object.get(DbCopyKeyEnum.OBJECT_NAME)));
            if (DbCopyObjectTypeEnum.TABLE.equals(objectType) && object.get(DbCopyKeyEnum.COPIED_ROWS) instanceof Number copiedRows) {
                summary.append("  (").append(__("{0} filas", String.format("%,d", copiedRows.longValue()))).append(")");
            }
            String message = getTrimmedString(object.get(DbCopyKeyEnum.MESSAGE));
            if (!message.isEmpty()) summary.append("\n       ").append(message.replace("\n", "\n       "));
            summary.append("\n");
        }
        if (copyDatabaseResultDto.isCancelled()) summary.append("\n").append(__("Copia parada antes de acabar.")).append("\n");
        TextArea summaryTextArea = new TextArea(summary.toString());
        summaryTextArea.setEditable(false);
        summaryTextArea.getStyleClass().add("result-text");
        Stage summaryStage = new Stage();
        summaryStage.initOwner(statusLabel.getScene().getWindow());
        summaryStage.setTitle(__("Resumen de la copia"));
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

    /**
     * [tipo => [nombres marcados]].
     */
    private Map<String, Object> getCheckedObjects() {
        Map<String, Object> checkedObjects = new LinkedHashMap<>();
        boolean isBetweenEngines = isBetweenEngines();
        folderItemsByType.forEach((objectType, folderItem) -> {
            List<String> names = new ArrayList<>();
            if (isBetweenEngines && !DbCopyObjectTypeEnum.TABLE.equals(objectType)) {
                checkedObjects.put(objectType, names);
                return;
            }
            for (TreeItem<String> objectItem : folderItem.getChildren()) {
                if (objectItem instanceof CheckBoxTreeItem<String> checkBoxItem && checkBoxItem.isSelected()) names.add(objectItem.getValue());
            }
            checkedObjects.put(objectType, names);
        });
        return checkedObjects;
    }

    private String getTargetConnectionUuid() {
        Map<String, Object> targetConnection = targetConnectionComboBox.getValue();
        return targetConnection == null ? "" : getTrimmedString(targetConnection.get(ConnectionsKeyEnum.CONNECTION_UUID));
    }

    private void setRunning(boolean isRunning) {
        this.isRunning = isRunning;
        isCancelRequested = false;
        copyButton.setDisable(false);
        copyButton.setText(isRunning ? __("Parar") : __("Copiar"));
        closeButton.setDisable(isRunning);
        summaryButton.setDisable(isRunning || copyDatabaseResultDto == null);
        for (javafx.scene.Node node : List.of(objectsTreeView, targetConnectionComboBox, targetDatabaseComboBox, structureOnlyRadio,
            dropIfExistsCheckBox, bulkInsertCheckBox, ignoreDefinerCheckBox, copyTriggersCheckBox)) {
            node.setDisable(isRunning);
        }
        applyEngineMode();
    }

    private void setStatus(String status) {
        statusLabel.getStyleClass().removeAll(STYLE_STATUS_ERROR, STYLE_STATUS_OK);
        statusLabel.setText(status);
    }

    private void setErrorStatus(String status) {
        setStatus(status);
        statusLabel.getStyleClass().add(STYLE_STATUS_ERROR);
    }

    private void setOkStatus(String status) {
        setStatus(status);
        statusLabel.getStyleClass().add(STYLE_STATUS_OK);
    }

    /**
     * El primer objeto que fallo con su mensaje, para verlo sin abrir el resumen.
     */
    private String getFirstError(CopyDatabaseResultDto copyDatabaseResultDto) {
        for (Map<String, Object> object : copyDatabaseResultDto.getObjects()) {
            if (getBool(object.get(DbCopyKeyEnum.IS_OK))) continue;
            return getTypeName(getTrimmedString(object.get(DbCopyKeyEnum.OBJECT_TYPE))) + " "
                + getTrimmedString(object.get(DbCopyKeyEnum.OBJECT_NAME)) + ": " + getTrimmedString(object.get(DbCopyKeyEnum.MESSAGE));
        }
        return "";
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

    /**
     * La carpeta del arbol de objetos: "Tablas", "Vistas"...
     */
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
     * El tipo en singular para el progreso y el resumen: "tabla customers", "vista v_active"...
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
        Task<T> task = new Task<>() {
            @Override
            protected T call() throws Exception {
                return work.call();
            }
        };
        task.setOnSucceeded(event -> onSucceeded.accept(task.getValue()));
        task.setOnFailed(event -> onFailed.accept(task.getException()));
        Thread thread = new Thread(task, threadName);
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Pasa el progreso al hilo de JavaFX: la barra avanza por objetos y, dentro de una tabla, por filas (sobre la
     * estimacion del servidor).
     */
    private final class DialogProgressListener implements DbCopyProgressListener {

        private int objectIndex;
        private int objectCount;
        private String objectLabel = "";

        @Override
        public void onObjectStarted(String objectType, String objectName, int objectIndex, int objectCount) {
            this.objectIndex = objectIndex;
            this.objectCount = objectCount;
            this.objectLabel = getTypeName(objectType) + " " + objectName;
            String status = objectIndex + "/" + objectCount + "  " + objectLabel;
            double progress = (objectIndex - 1) / (double) objectCount;
            Platform.runLater(() -> {
                progressBar.setProgress(progress);
                setStatus(status);
            });
        }

        @Override
        public void onRowsCopied(String tableName, long copiedRows, long estimatedRows) {
            double tableShare = estimatedRows > 0 ? Math.min(1, copiedRows / (double) estimatedRows) : 0;
            double progress = (objectIndex - 1 + tableShare) / Math.max(1, objectCount);
            String status = objectIndex + "/" + objectCount + "  " + objectLabel + "  "
                + __("{0} filas", String.format("%,d", copiedRows) + (estimatedRows > 0 ? String.format(" / ~%,d", estimatedRows) : ""));
            Platform.runLater(() -> {
                progressBar.setProgress(progress);
                setStatus(status);
            });
        }

        @Override
        public boolean isCancelled() {
            return isCancelRequested;
        }
    }

    /**
     * Cuadradito de color + nombre de la conexion (+ PROD), como en el panel.
     */
    private static final class ConnectionCell extends ListCell<Map<String, Object>> {

        @Override
        protected void updateItem(Map<String, Object> connection, boolean isEmpty) {
            super.updateItem(connection, isEmpty);
            if (isEmpty || connection == null) {
                setText(null);
                setGraphic(null);
                return;
            }
            String colorHex = getTrimmedString(connection.get(ConnectionsKeyEnum.COLOR_HEX));
            Rectangle colorRectangle = new Rectangle(12, 12, Color.web(colorHex.isEmpty() ? NO_COLOR_HEX : colorHex));
            Node engineIcon = EngineIconComponent.getInstance().getIcon(getTrimmedString(connection.get(ConnectionsKeyEnum.DB_ENGINE)));
            HBox graphic = new HBox(6, colorRectangle, engineIcon);
            graphic.setAlignment(Pos.CENTER_LEFT);
            if (getBool(connection.get(ConnectionsKeyEnum.IS_PRODUCTION))) {
                Label productionLabel = new Label("PROD");
                productionLabel.getStyleClass().add("production-badge");
                graphic.getChildren().add(productionLabel);
            }
            // el nombre como texto de la celda (no un Label dentro): asi toma el color de la lista y del boton
            setMnemonicParsing(false);
            setText(getTrimmedString(connection.get(ConnectionsKeyEnum.CONNECTION_NAME)));
            setGraphic(graphic);
        }
    }
}
