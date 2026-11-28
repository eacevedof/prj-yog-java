package modules.mongo.infrastructure.controllers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.KeyCode;

import modules.shared.domain.enums.ExceptionEnum;
import modules.shared.application.Primitives;
import modules.shared.infrastructure.components.icons.AppIconEnum;
import modules.shared.infrastructure.components.icons.AppIcon;
import modules.shared.infrastructure.components.theme.ThemeComponent;
import modules.shared.infrastructure.components.uuid.UuidGenerator;
import modules.shared.infrastructure.controllers.AbstractFxController;
import modules.shared.infrastructure.controllers.ConnectionViewController;
import modules.shared.infrastructure.exceptions.InterfaceException;
import static modules.shared.application.Primitives.getInt;
import static modules.shared.application.Primitives.getTrimmedString;

import static core.config.Global.__;

import modules.connections.domain.enums.ConnectionsKeyEnum;
import modules.connections.infrastructure.factories.MongoSessionFactory;
import modules.connections.infrastructure.factories.TargetSessionFactory;

import modules.mongo.application.executecommand.ExecuteMongoCommandDto;
import modules.mongo.application.finddocuments.FindMongoDocumentsDto;
import modules.mongo.application.finddocuments.FindMongoDocumentsResultDto;
import modules.mongo.application.listcollections.ListMongoCollectionsDto;
import modules.mongo.application.listdatabases.ListMongoDatabasesDto;
import modules.mongo.application.savedocument.SaveMongoDocumentDto;
import modules.mongo.application.executecommand.ExecuteMongoCommandService;
import modules.mongo.application.finddocuments.FindMongoDocumentsService;
import modules.mongo.application.listcollections.ListMongoCollectionsService;
import modules.mongo.application.listdatabases.ListMongoDatabasesService;
import modules.mongo.application.savedocument.SaveMongoDocumentService;

/**
 * La pestaña de una conexion MongoDB: a la izquierda las BDs y sus colecciones (cargadas al desplegar); a la derecha
 * los documentos de la coleccion elegida (filtro, orden, salto y limite en JSON, como en mongosh), el JSON del
 * documento elegido para editarlo, y una consola tipo mongosh. Lo que hace red va en segundo plano.
 */
public final class MongoTabController extends AbstractFxController implements ConnectionViewController {

    private static final String TAB_PREFIX = "ctab-";
    private static final String LOADING = "loading";
    private static final String DEFAULT_CONSOLE_DATABASE = "test";

    @FXML
    private TreeView<MongoNode> databasesTreeView;
    @FXML
    private Label treeStatusLabel;
    @FXML
    private TabPane mongoTabPane;
    @FXML
    private Label collectionLabel;
    @FXML
    private TextField filterField;
    @FXML
    private TextField sortField;
    @FXML
    private TextField skipField;
    @FXML
    private TextField limitField;
    @FXML
    private Button findButton;
    @FXML
    private TableView<List<String>> documentsTableView;
    @FXML
    private TextArea documentArea;
    @FXML
    private Button saveDocumentButton;
    @FXML
    private Button insertDocumentButton;
    @FXML
    private Button deleteDocumentButton;
    @FXML
    private Label documentsStatusLabel;
    @FXML
    private TextArea consoleOutputArea;
    @FXML
    private Label consolePromptLabel;
    @FXML
    private TextField consoleInputField;
    @FXML
    private Button runCommandButton;

    private String connectionUuid = "";
    private String tabUuid = "";
    private String browserSessionKey = "";
    private String consoleSessionKey = "";
    private String currentDatabase = "";
    private String currentCollection = "";
    private String consoleDatabase = DEFAULT_CONSOLE_DATABASE;
    private List<String> documentsJson = List.of();
    private final List<String> consoleHistory = new ArrayList<>();
    private int consoleHistoryIndex;

    @FXML
    private void initialize() {
        databasesTreeView.setRoot(new TreeItem<>());
        databasesTreeView.setCellFactory(treeView -> new MongoTreeCell());
        databasesTreeView.getSelectionModel().selectedItemProperty().addListener((observable, oldItem, newItem) -> {
            if (newItem == null || newItem.getValue() == null) return;
            MongoNode mongoNode = newItem.getValue();
            if (mongoNode.collectionName() != null) openCollection(mongoNode.databaseName(), mongoNode.collectionName());
            else consoleDatabase = mongoNode.databaseName();
            consolePromptLabel.setText(consoleDatabase + ">");
        });
        documentsTableView.getSelectionModel().selectedIndexProperty().addListener((observable, oldIndex, newIndex) -> {
            int index = newIndex.intValue();
            boolean hasDocument = index >= 0 && index < documentsJson.size();
            documentArea.setText(hasDocument ? documentsJson.get(index) : "");
            saveDocumentButton.setDisable(!hasDocument);
            deleteDocumentButton.setDisable(!hasDocument);
        });
        consoleInputField.setOnKeyPressed(keyEvent -> {
            if (keyEvent.getCode() == KeyCode.UP) showConsoleHistory(-1);
            if (keyEvent.getCode() == KeyCode.DOWN) showConsoleHistory(1);
        });
    }

    @Override
    public void setup(Map<String, Object> connection) {
        connectionUuid = getTrimmedString(connection.get(ConnectionsKeyEnum.CONNECTION_UUID));
        tabUuid = UuidGenerator.getInstance().getUniqueIdWithPrefix(TAB_PREFIX);
        browserSessionKey = tabUuid + TargetSessionFactory.SESSION_KEY_SEPARATOR + "mongo-browser";
        consoleSessionKey = tabUuid + TargetSessionFactory.SESSION_KEY_SEPARATOR + "mongo-console";
        String databaseName = getTrimmedString(connection.get(ConnectionsKeyEnum.DATABASE_NAME));
        consoleDatabase = databaseName.isEmpty() ? DEFAULT_CONSOLE_DATABASE : databaseName;
        consolePromptLabel.setText(consoleDatabase + ">");
        onRefresh();
    }

    @Override
    public void close() {
        Thread closeThread = new Thread(() -> MongoSessionFactory.getInstance().closeSessions(tabUuid), "close-mongo-sessions");
        closeThread.setDaemon(true);
        closeThread.start();
    }

    @FXML
    private void onRefresh() {
        treeStatusLabel.setText(__("Leyendo BDs..."));
        runInBackground(
            () -> ListMongoDatabasesService.getInstance().invoke(ListMongoDatabasesDto.fromPrimitives(Primitives.of(
                "connectionUuid", connectionUuid, "sessionKey", browserSessionKey
            ))).getDatabaseNames(),
            databaseNames -> {
                TreeItem<MongoNode> root = new TreeItem<>();
                for (String databaseName : databaseNames) root.getChildren().add(getDatabaseItem(databaseName));
                databasesTreeView.setRoot(root);
                treeStatusLabel.setText(__("{0} BD(s)", databaseNames.size()));
            },
            throwable -> treeStatusLabel.setText(getErrorMessage(throwable)),
            "mongo-databases"
        );
    }

    /**
     * Una BD con un hijo "cargando" que, al desplegarla, se cambia por sus colecciones.
     */
    private TreeItem<MongoNode> getDatabaseItem(String databaseName) {
        TreeItem<MongoNode> databaseItem = new TreeItem<>(new MongoNode(databaseName, null, -1, false));
        databaseItem.getChildren().add(new TreeItem<>(new MongoNode(databaseName, LOADING, -1, true)));
        databaseItem.expandedProperty().addListener((observable, wasExpanded, isExpanded) -> {
            if (isExpanded && databaseItem.getChildren().size() == 1 && databaseItem.getChildren().getFirst().getValue().isLoading()) {
                loadCollections(databaseItem, databaseName);
            }
        });
        return databaseItem;
    }

    private void loadCollections(TreeItem<MongoNode> databaseItem, String databaseName) {
        runInBackground(
            () -> ListMongoCollectionsService.getInstance().invoke(ListMongoCollectionsDto.fromPrimitives(Primitives.of(
                "connectionUuid", connectionUuid, "sessionKey", browserSessionKey, "databaseName", databaseName
            ))).getCollections(),
            collections -> {
                List<TreeItem<MongoNode>> collectionItems = new ArrayList<>();
                for (Map<String, Object> collection : collections) {
                    long count = collection.get("count") instanceof Number number ? number.longValue() : -1;
                    collectionItems.add(new TreeItem<>(new MongoNode(databaseName, String.valueOf(collection.get("name")), count, false)));
                }
                databaseItem.getChildren().setAll(collectionItems);
            },
            throwable -> databaseItem.getChildren().setAll(new TreeItem<>(new MongoNode(databaseName, getErrorMessage(throwable), -1, true))),
            "mongo-collections"
        );
    }

    private void openCollection(String databaseName, String collectionName) {
        currentDatabase = databaseName;
        currentCollection = collectionName;
        consoleDatabase = databaseName;
        collectionLabel.setText(databaseName + "." + collectionName);
        findButton.setDisable(false);
        insertDocumentButton.setDisable(false);
        mongoTabPane.getSelectionModel().selectFirst();
        onFind();
    }

    @FXML
    private void onFind() {
        if (currentCollection.isEmpty()) return;
        documentsStatusLabel.setText(__("Buscando..."));
        String databaseName = currentDatabase;
        String collectionName = currentCollection;
        runInBackground(
            () -> FindMongoDocumentsService.getInstance().invoke(FindMongoDocumentsDto.fromPrimitives(Primitives.of(
                "connectionUuid", connectionUuid, "sessionKey", browserSessionKey, "databaseName", databaseName, "collectionName", collectionName,
                "filterJson", filterField.getText(), "sortJson", sortField.getText(),
                "skip", getInt(skipField.getText().trim()), "limit", getInt(limitField.getText().trim())
            ))),
            this::showDocuments,
            throwable -> documentsStatusLabel.setText(getErrorMessage(throwable)),
            "mongo-find"
        );
    }

    private void showDocuments(FindMongoDocumentsResultDto findMongoDocumentsResultDto) {
        documentsJson = findMongoDocumentsResultDto.getDocumentsJson();
        documentsTableView.getColumns().clear();
        List<String> columns = findMongoDocumentsResultDto.getColumns();
        for (int i = 0; i < columns.size(); i++) {
            int columnIndex = i;
            TableColumn<List<String>, String> tableColumn = new TableColumn<>(columns.get(i));
            tableColumn.setCellValueFactory(cellData -> new ReadOnlyStringWrapper(
                columnIndex < cellData.getValue().size() ? cellData.getValue().get(columnIndex) : ""
            ));
            tableColumn.setPrefWidth(i == 0 ? 230 : 140);
            documentsTableView.getColumns().add(tableColumn);
        }
        documentsTableView.getItems().setAll(findMongoDocumentsResultDto.getRows());
        documentArea.clear();
        saveDocumentButton.setDisable(true);
        deleteDocumentButton.setDisable(true);
        String total = findMongoDocumentsResultDto.getTotalCount() < 0 ? "?" : String.valueOf(findMongoDocumentsResultDto.getTotalCount());
        documentsStatusLabel.setText(__("{0} de {1} documento(s) · {2} ms", findMongoDocumentsResultDto.getRows().size(), total, findMongoDocumentsResultDto.getElapsedMs()));
    }

    @FXML
    private void onSaveDocument() {
        saveDocument(documentArea.getText(), false, false, false);
    }

    @FXML
    private void onInsertDocument() {
        saveDocument(documentArea.getText(), true, false, false);
    }

    @FXML
    private void onDeleteDocument() {
        if (!isDeleteConfirmed(__("¿Borrar este documento de {0}?", currentDatabase + "." + currentCollection))) return;
        saveDocument(documentArea.getText(), false, true, false);
    }

    /**
     * Guardar, insertar o borrar; en produccion pregunta (428) y se repite confirmado.
     */
    private void saveDocument(String documentJson, boolean isNew, boolean isDelete, boolean isConfirmed) {
        documentsStatusLabel.setText(__("Guardando..."));
        String databaseName = currentDatabase;
        String collectionName = currentCollection;
        runInBackground(
            () -> SaveMongoDocumentService.getInstance().invoke(SaveMongoDocumentDto.fromPrimitives(Primitives.of(
                "connectionUuid", connectionUuid, "sessionKey", browserSessionKey, "databaseName", databaseName, "collectionName", collectionName,
                "documentJson", documentJson, "isNew", isNew, "isDelete", isDelete, "isConfirmed", isConfirmed
            ))),
            saveMongoDocumentResultDto -> {
                onFind();
                documentsStatusLabel.setText(isDelete ? __("Borrado {0}", saveMongoDocumentResultDto.getIdJson()) : isNew ? __("Insertado {0}", saveMongoDocumentResultDto.getIdJson()) : __("Guardado {0}", saveMongoDocumentResultDto.getIdJson()));
            },
            throwable -> {
                if (isPreconditionRequired(throwable) && isProductionConfirmed(throwable.getMessage())) {
                    saveDocument(documentJson, isNew, isDelete, true);
                    return;
                }
                documentsStatusLabel.setText(getErrorMessage(throwable));
            },
            "mongo-save"
        );
    }

    @FXML
    private void onRunCommand() {
        runCommand(consoleInputField.getText().trim(), false);
    }

    private void runCommand(String commandLine, boolean isConfirmed) {
        if (commandLine.isEmpty()) return;
        if (!isConfirmed) {
            consoleHistory.remove(commandLine);
            consoleHistory.add(commandLine);
            consoleHistoryIndex = consoleHistory.size();
            consoleInputField.clear();
        }
        String databaseName = consoleDatabase;
        runCommandButton.setDisable(true);
        runInBackground(
            () -> ExecuteMongoCommandService.getInstance().invoke(ExecuteMongoCommandDto.fromPrimitives(Primitives.of(
                "connectionUuid", connectionUuid, "sessionKey", consoleSessionKey, "databaseName", databaseName,
                "commandLine", commandLine, "isConfirmed", isConfirmed
            ))),
            executeMongoCommandResultDto -> {
                runCommandButton.setDisable(false);
                appendConsole(databaseName, commandLine, executeMongoCommandResultDto.getOutput() + (executeMongoCommandResultDto.isError() ? "" : "\n(" + executeMongoCommandResultDto.getElapsedMs() + " ms)"));
                consoleDatabase = executeMongoCommandResultDto.getCurrentDatabase();
                consolePromptLabel.setText(consoleDatabase + ">");
            },
            throwable -> {
                runCommandButton.setDisable(false);
                if (isPreconditionRequired(throwable) && isProductionConfirmed(throwable.getMessage())) {
                    runCommand(commandLine, true);
                    return;
                }
                appendConsole(databaseName, commandLine, "(error) " + getErrorMessage(throwable));
            },
            "mongo-command"
        );
    }

    private void appendConsole(String databaseName, String commandLine, String output) {
        consoleOutputArea.appendText((consoleOutputArea.getText().isEmpty() ? "" : "\n\n") + databaseName + "> " + commandLine + "\n" + output);
    }

    private void showConsoleHistory(int step) {
        if (consoleHistory.isEmpty()) return;
        consoleHistoryIndex = Math.max(0, Math.min(consoleHistory.size(), consoleHistoryIndex + step));
        consoleInputField.setText(consoleHistoryIndex < consoleHistory.size() ? consoleHistory.get(consoleHistoryIndex) : "");
        consoleInputField.positionCaret(consoleInputField.getText().length());
    }

    private boolean isPreconditionRequired(Throwable throwable) {
        return throwable instanceof InterfaceException interfaceException && interfaceException.getCode() == ExceptionEnum.CODE_PRECONDITION_REQUIRED;
    }

    /**
     * Aviso de produccion: el boton por defecto es Cancelar, asi un Enter por inercia no lanza el deleteMany.
     */
    private boolean isProductionConfirmed(String message) {
        ButtonType executeButtonType = new ButtonType(__("Ejecutar en producción"), ButtonBar.ButtonData.OK_DONE);
        Alert alert = new Alert(Alert.AlertType.WARNING, message, executeButtonType, ButtonType.CANCEL);
        ThemeComponent.getInstance().applyTo(alert);
        alert.setTitle(__("Conexión de producción"));
        alert.setHeaderText(__("Operación destructiva en PRODUCCIÓN"));
        ((Button) alert.getDialogPane().lookupButton(executeButtonType)).setDefaultButton(false);
        ((Button) alert.getDialogPane().lookupButton(ButtonType.CANCEL)).setDefaultButton(true);
        return alert.showAndWait().filter(executeButtonType::equals).isPresent();
    }

    private <T> void runInBackground(Supplier<T> work, Consumer<T> onSucceeded, Consumer<Throwable> onFailed, String threadName) {
        Task<T> backgroundTask = new Task<>() {
            @Override
            protected T call() {
                return work.get();
            }
        };
        backgroundTask.setOnSucceeded(event -> onSucceeded.accept(backgroundTask.getValue()));
        backgroundTask.setOnFailed(event -> onFailed.accept(backgroundTask.getException()));
        Thread daemonThread = new Thread(backgroundTask, threadName);
        daemonThread.setDaemon(true);
        daemonThread.start();
    }

    /**
     * Un nodo del arbol: una BD (collectionName null), una coleccion con su recuento, o un aviso (cargando, error).
     */
    private record MongoNode(String databaseName, String collectionName, long count, boolean isLoading) {
    }

    private static final class MongoTreeCell extends TreeCell<MongoNode> {

        @Override
        protected void updateItem(MongoNode mongoNode, boolean isEmpty) {
            super.updateItem(mongoNode, isEmpty);
            if (isEmpty || mongoNode == null) {
                setText(null);
                setGraphic(null);
                return;
            }
            setMnemonicParsing(false);
            if (mongoNode.isLoading()) {
                setText(LOADING.equals(mongoNode.collectionName()) ? __("Cargando...") : mongoNode.collectionName());
                setGraphic(null);
                return;
            }
            if (mongoNode.collectionName() == null) {
                setText(mongoNode.databaseName());
                setGraphic(AppIcon.of(AppIconEnum.DATABASE));
                return;
            }
            setText(mongoNode.collectionName() + (mongoNode.count() >= 0 ? " (" + mongoNode.count() + ")" : ""));
            setGraphic(AppIcon.of(AppIconEnum.TABLE));
        }
    }
}
