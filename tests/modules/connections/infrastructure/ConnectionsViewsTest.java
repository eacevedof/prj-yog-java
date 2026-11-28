package modules.connections.infrastructure;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.KeyEvent;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import modules.shared.infrastructure.controllers.ConnectionTreeHandler;
import static modules.shared.application.Primitives.getTrimmedString;

import core.boot.DesktopApplication;

import testcase.AbstractTestCase;

import modules.connections.domain.enums.ConnectionsKeyEnum;
import modules.connections.application.createconnection.CreateConnectionDto;
import modules.connections.application.createconnection.CreateConnectionService;
import modules.connections.application.mothers.ConnectionPrimitivesMother;
import modules.connections.infrastructure.controllers.ConnectionsPanelController;

/**
 * Carga las vistas en una Scene sin mostrar ninguna ventana: falla si un fx:id, un onAction o el initialize() del
 * controller no casan con el .fxml.
 */
final class ConnectionsViewsTest extends AbstractTestCase {

    @BeforeAll
    static void startJavaFx() {
        bootFreshDatabase("ConnectionsViewsTest");
        try {
            Platform.startup(() -> { });
        }
        catch (IllegalStateException e) {
            // ya arrancado por otra clase de test
        }
    }

    @Test
    void testMainWindowLoadsConnectionsPanelWithSavedConnections() throws Exception {
        CreateConnectionService.getInstance().invoke(CreateConnectionDto.fromPrimitives(ConnectionPrimitivesMother.getValidLocal("loc-vista")));

        Parent mainWindow = loadOnFxThread("/modules/workspace/infrastructure/views/main-window.fxml");

        TreeView<?> connectionsTreeView = (TreeView<?>) mainWindow.lookup("#connectionsTreeView");
        assertTrue(connectionsTreeView.getRoot().getChildren().stream()
            .anyMatch(item -> item.getValue() instanceof Map<?, ?> value && "loc-vista".equals(getTrimmedString(value.get(ConnectionsKeyEnum.CONNECTION_NAME)))));
    }

    @Test
    void testOpenConnectionTreeHangsUnderItsConnectionAndGetsTheFilters() throws Exception {
        Map<String, Object> connection = CreateConnectionService.getInstance().invoke(
            CreateConnectionDto.fromPrimitives(ConnectionPrimitivesMother.getValidLocal("loc-arbol"))
        ).getConnection();
        String connectionUuid = getTrimmedString(connection.get(ConnectionsKeyEnum.CONNECTION_UUID));
        FakeTreeHandler fakeTreeHandler = new FakeTreeHandler();

        CompletableFuture<TreeItem<?>> attachedItem = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                FXMLLoader fxmlLoader = new FXMLLoader(DesktopApplication.class.getResource("/modules/workspace/infrastructure/views/main-window.fxml"), core.config.TranslatorResourceBundle.getInstance());
                Parent mainWindow = fxmlLoader.load();
                new Scene(mainWindow);
                mainWindow.applyCss();
                ConnectionsPanelController panel = (ConnectionsPanelController) fxmlLoader.getNamespace().get("connectionsPanelController");
                panel.attachTree(connectionUuid, fakeTreeHandler);
                ((TextField) mainWindow.lookup("#objectFilterField")).setText("cust");
                TreeView<?> connectionsTreeView = (TreeView<?>) mainWindow.lookup("#connectionsTreeView");
                TreeItem<?> connectionItem = connectionsTreeView.getRoot().getChildren().stream()
                    .filter(item -> item.getValue() instanceof Map<?, ?> value && connectionUuid.equals(getTrimmedString(value.get(ConnectionsKeyEnum.CONNECTION_UUID))))
                    .findFirst().orElseThrow();
                panel.detachTree(connectionUuid);
                // se completa despues de desconectar: el hilo del test no debe mirar los hijos antes de que se quiten
                attachedItem.complete(connectionItem);
            }
            catch (Exception e) {
                attachedItem.completeExceptionally(e);
            }
        });

        TreeItem<?> connectionItem = attachedItem.get(20, TimeUnit.SECONDS);
        assertEquals(List.of("db_one", "db_two"), fakeTreeHandler.childrenWhenAttached);
        assertEquals("cust", fakeTreeHandler.objectFilter);
        assertTrue(connectionItem.getChildren().isEmpty());
    }

    @Test
    void testConnectionDialogLoads() throws Exception {
        Parent connectionDialog = loadOnFxThread("/modules/connections/infrastructure/views/connection-dialog.fxml");

        assertTrue(connectionDialog.lookup("#connectionNameField") != null);
    }

    /**
     * Un arbol de mentira: dos BD bajo la conexion y los filtros que le llegan del panel.
     */
    private static final class FakeTreeHandler implements ConnectionTreeHandler {

        private List<Object> childrenWhenAttached = List.of();
        private String objectFilter = "";

        @Override
        public void attach(TreeItem<Object> connectionItem, Runnable onViewChanged, Consumer<String> onStatusChanged) {
            connectionItem.getChildren().setAll(List.of(new TreeItem<>("db_one"), new TreeItem<>("db_two")));
            childrenWhenAttached = connectionItem.getChildren().stream().map(TreeItem::getValue).toList();
            onStatusChanged.accept("2 BD");
        }

        @Override
        public void updateCell(TreeCell<Object> treeCell, TreeItem<Object> treeItem) {
            treeCell.setText(String.valueOf(treeItem.getValue()));
        }

        @Override
        public void onSelected(TreeItem<Object> treeItem) {
        }

        @Override
        public void onActivated(TreeItem<Object> treeItem) {
        }

        @Override
        public void onKeyPressed(TreeItem<Object> treeItem, KeyEvent keyEvent) {
        }

        @Override
        public void refresh(TreeItem<Object> treeItem) {
        }

        @Override
        public void setFilters(String databaseFilter, String objectFilter) {
            this.objectFilter = objectFilter;
        }
    }

    private Parent loadOnFxThread(String fxmlPath) throws Exception {
        CompletableFuture<Parent> loadedRoot = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                Parent root = new FXMLLoader(DesktopApplication.class.getResource(fxmlPath), core.config.TranslatorResourceBundle.getInstance()).load();
                new Scene(root).getStylesheets().add(DesktopApplication.class.getResource(DesktopApplication.PATH_STYLES).toExternalForm());
                root.applyCss();
                loadedRoot.complete(root);
            }
            catch (Exception e) {
                loadedRoot.completeExceptionally(e);
            }
        });
        return loadedRoot.get(20, TimeUnit.SECONDS);
    }
}
