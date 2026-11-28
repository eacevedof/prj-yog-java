package modules.workspace.infrastructure;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.text.Font;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import modules.shared.application.Primitives;
import modules.shared.infrastructure.components.fonts.FontsComponent;

import core.boot.DesktopApplication;
import static core.config.Global.__;

import testcase.AbstractTestCase;

import modules.connections.domain.enums.ConnectionsKeyEnum;
import modules.connections.application.createconnection.CreateConnectionDto;
import modules.connections.application.createconnection.CreateConnectionService;
import modules.connections.application.mothers.ConnectionPrimitivesMother;

import modules.query.infrastructure.components.ResultGridPane;
import modules.query.infrastructure.controllers.QueryTabController;

import modules.workspace.domain.enums.WorkspaceKeyEnum;
import modules.workspace.application.savequerytabs.SaveQueryTabsDto;
import modules.workspace.application.savequerytabs.SaveQueryTabsService;
import modules.workspace.infrastructure.controllers.ConnectionTabController;

/**
 * Carga sin ventana la pestaña de conexion (arbol + editor) y las vistas que la componen: falla si un fx:id, un
 * onAction, un fx:include o el initialize() no casan. El arbol intenta conectar a un puerto cerrado y solo lo dice en
 * su barra de estado.
 */
final class WorkspaceViewsTest extends AbstractTestCase {

    @BeforeAll
    static void startJavaFx() {
        bootFreshDatabase("WorkspaceViewsTest");
        try {
            Platform.startup(() -> { });
        }
        catch (IllegalStateException e) {
            // ya arrancado por otra clase de test
        }
    }

    @Test
    void testQueryTabAndTableDataTabLoad() throws Exception {
        Parent queryTab = loadOnFxThread("/modules/query/infrastructure/views/query-tab.fxml");
        Parent tableDataTab = loadOnFxThread("/modules/query/infrastructure/views/table-data-tab.fxml");
        assertTrue(tableDataTab.lookup("#whereField") != null);
        Parent queryHistory = loadOnFxThread("/modules/query/infrastructure/views/query-history.fxml");
        assertTrue(queryHistory.lookup("#historyTableView") != null);

        assertTrue(queryTab.lookup(".sql-editor") != null);
    }

    @Test
    void testJetBrainsMonoIsBundledForTheSqlEditor() {
        FontsComponent.getInstance().loadAll();
        assertTrue(Font.getFamilies().contains(FontsComponent.FAMILY_JETBRAINS_MONO));
        assertTrue(Font.getFontNames(FontsComponent.FAMILY_JETBRAINS_MONO).size() >= 3);
    }

    @Test
    void testConnectionTabOpensWithOneQueryTabAndTheConnectionDatabaseActive() throws Exception {
        Map<String, Object> connection = CreateConnectionService.getInstance().invoke(CreateConnectionDto.fromPrimitives(
            Primitives.merge(ConnectionPrimitivesMother.getValidLocal("loc-pestana"), Primitives.of("port", "1"))
        )).getConnection();

        CompletableFuture<Object[]> opened = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                FXMLLoader fxmlLoader = new FXMLLoader(DesktopApplication.class.getResource("/modules/workspace/infrastructure/views/connection-tab.fxml"), core.config.TranslatorResourceBundle.getInstance());
                Parent root = fxmlLoader.load();
                new Scene(root);
                ConnectionTabController connectionTabController = fxmlLoader.getController();
                connectionTabController.setup(connection);
                // el SplitPane mete sus paneles en la escena al crear su skin: sin esto lookup() no los ve
                root.applyCss();
                opened.complete(new Object[] {root, connectionTabController});
            }
            catch (Exception e) {
                opened.completeExceptionally(e);
            }
        });
        Object[] rootAndController = opened.get(20, TimeUnit.SECONDS);
        Parent root = (Parent) rootAndController[0];
        ConnectionTabController connectionTabController = (ConnectionTabController) rootAndController[1];

        TabPane queryTabPane = (TabPane) root.lookup("#queryTabPane");
        assertEquals(1, queryTabPane.getTabs().size());
        assertEquals("db_test", connectionTabController.getActiveDatabase());
        Platform.runLater(connectionTabController::close);
    }

    @Test
    void testConnectionTabReopensWithTheQueryTabsItWasClosedWith() throws Exception {
        Map<String, Object> connection = CreateConnectionService.getInstance().invoke(CreateConnectionDto.fromPrimitives(
            Primitives.merge(ConnectionPrimitivesMother.getValidLocal("loc-recupera"), Primitives.of("port", "1"))
        )).getConnection();

        // primera vez: una pestaña vacia; se escribe un SQL y se cierra la conexion (close guarda)
        Object[] first = openConnectionTab(connection);
        TabPane firstQueryTabPane = (TabPane) ((Parent) first[0]).lookup("#queryTabPane");
        CompletableFuture<Void> closed = new CompletableFuture<>();
        Platform.runLater(() -> {
            ((QueryTabController) firstQueryTabPane.getTabs().getFirst().getUserData()).setSql("SELECT *\nFROM users;\n");
            ((ConnectionTabController) first[1]).close();
            closed.complete(null);
        });
        closed.get(20, TimeUnit.SECONDS);

        // al volver a abrirla (o tras reiniciar la app): la pestaña con su SQL
        Object[] second = openConnectionTab(connection);
        TabPane secondQueryTabPane = (TabPane) ((Parent) second[0]).lookup("#queryTabPane");
        assertEquals(1, secondQueryTabPane.getTabs().size());
        assertEquals("SELECT *\nFROM users;\n", ((QueryTabController) secondQueryTabPane.getTabs().getFirst().getUserData()).getSql());
        Platform.runLater(((ConnectionTabController) second[1])::close);
    }

    @Test
    void testTabMenuClosesUnmodifiedTabsAndLeavesOneWhenClosingAll() throws Exception {
        Map<String, Object> connection = CreateConnectionService.getInstance().invoke(CreateConnectionDto.fromPrimitives(
            Primitives.merge(ConnectionPrimitivesMother.getValidLocal("loc-menu-pestanas"), Primitives.of("port", "1"))
        )).getConnection();
        SaveQueryTabsService.getInstance().invoke(SaveQueryTabsDto.fromPrimitives(Primitives.of(
            "connectionUuid", connection.get(ConnectionsKeyEnum.CONNECTION_UUID),
            "activeDatabase", "db_test",
            "queryTabs", List.of(
                Primitives.of(WorkspaceKeyEnum.TAB_TITLE, "uno", WorkspaceKeyEnum.SQL_TEXT, "SELECT 1", WorkspaceKeyEnum.IS_SELECTED, true),
                Primitives.of(WorkspaceKeyEnum.TAB_TITLE, "dos", WorkspaceKeyEnum.SQL_TEXT, "SELECT 2", WorkspaceKeyEnum.IS_SELECTED, false),
                Primitives.of(WorkspaceKeyEnum.TAB_TITLE, "tres", WorkspaceKeyEnum.SQL_TEXT, "", WorkspaceKeyEnum.IS_SELECTED, false)
            )
        )));

        Object[] opened = openConnectionTab(connection);
        TabPane queryTabPane = (TabPane) ((Parent) opened[0]).lookup("#queryTabPane");
        CompletableFuture<int[]> counts = new CompletableFuture<>();
        Platform.runLater(() -> {
            int restoredTabs = queryTabPane.getTabs().size();
            ((QueryTabController) queryTabPane.getTabs().get(1).getUserData()).insertText(" -- tocada");
            fireTabMenuItem(queryTabPane.getTabs().getFirst(), "Cerrar las no modificadas");
            int afterUnmodified = queryTabPane.getTabs().size();
            String keptSql = ((QueryTabController) queryTabPane.getTabs().getFirst().getUserData()).getSql();
            fireTabMenuItem(queryTabPane.getTabs().getFirst(), "Cerrar todas");
            int afterAll = queryTabPane.getTabs().size();
            String freshSql = ((QueryTabController) queryTabPane.getTabs().getFirst().getUserData()).getSql();
            counts.complete(new int[] {restoredTabs, afterUnmodified, keptSql.contains("tocada") ? 1 : 0, afterAll, freshSql.isEmpty() ? 1 : 0});
        });

        assertEquals("[3, 1, 1, 1, 1]", Arrays.toString(counts.get(20, TimeUnit.SECONDS)));
        Platform.runLater(((ConnectionTabController) opened[1])::close);
    }

    private static void fireTabMenuItem(Tab tab, String spanishText) {
        tab.getContextMenu().getItems().stream()
            .filter(menuItem -> __(spanishText).equals(menuItem.getText()))
            .findFirst()
            .orElseThrow()
            .fire();
    }

    private Object[] openConnectionTab(Map<String, Object> connection) throws Exception {
        CompletableFuture<Object[]> opened = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                FXMLLoader fxmlLoader = new FXMLLoader(DesktopApplication.class.getResource("/modules/workspace/infrastructure/views/connection-tab.fxml"), core.config.TranslatorResourceBundle.getInstance());
                Parent root = fxmlLoader.load();
                new Scene(root);
                ConnectionTabController connectionTabController = fxmlLoader.getController();
                connectionTabController.setup(connection);
                root.applyCss();
                opened.complete(new Object[] {root, connectionTabController});
            }
            catch (Exception e) {
                opened.completeExceptionally(e);
            }
        });
        return opened.get(20, TimeUnit.SECONDS);
    }

    @Test
    void testResultGridSearchesAllColumnsAndKeepsHiddenColumns() throws Exception {
        CompletableFuture<int[]> counts = new CompletableFuture<>();
        Platform.runLater(() -> {
            ResultGridPane resultGridPane = ResultGridPane.getInstance(
                List.of("id", "email", "city"),
                List.of(List.of("1", "ana@x.es", "Madrid"), List.of("2", "BOB@y.es", "Bilbao"), Arrays.asList("3", null, "Madrid")),
                "madrid",
                Set.of("city")
            );
            int madridRows = resultGridPane.getVisibleRowCount();
            resultGridPane.getTableView().getColumns().get(1).setVisible(false);
            int hiddenColumns = resultGridPane.getHiddenColumnNames().size();
            counts.complete(new int[] {madridRows, hiddenColumns});
        });

        int[] madridRowsAndHiddenColumns = counts.get(20, TimeUnit.SECONDS);

        assertEquals(2, madridRowsAndHiddenColumns[0]);
        assertEquals(2, madridRowsAndHiddenColumns[1]);
    }

    @Test
    void testQueryTabsAreDraggableAndReopenInTheirNewOrder() throws Exception {
        Map<String, Object> connection = CreateConnectionService.getInstance().invoke(CreateConnectionDto.fromPrimitives(
            Primitives.merge(ConnectionPrimitivesMother.getValidLocal("loc-reordena"), Primitives.of("port", "1"))
        )).getConnection();
        SaveQueryTabsService.getInstance().invoke(SaveQueryTabsDto.fromPrimitives(Primitives.of(
            "connectionUuid", connection.get(ConnectionsKeyEnum.CONNECTION_UUID),
            "queryTabs", List.of(
                Primitives.of(WorkspaceKeyEnum.TAB_TITLE, "uno", WorkspaceKeyEnum.SQL_TEXT, "SELECT 1", WorkspaceKeyEnum.IS_SELECTED, true),
                Primitives.of(WorkspaceKeyEnum.TAB_TITLE, "dos", WorkspaceKeyEnum.SQL_TEXT, "SELECT 2", WorkspaceKeyEnum.IS_SELECTED, false)
            )
        )));

        // arrastrar "dos" delante de "uno": el skin de REORDER mueve la pestaña en getTabs() igual que esto
        Object[] first = openConnectionTab(connection);
        TabPane firstQueryTabPane = (TabPane) ((Parent) first[0]).lookup("#queryTabPane");
        CompletableFuture<TabPane.TabDragPolicy> dragPolicy = new CompletableFuture<>();
        Platform.runLater(() -> {
            Tab secondTab = firstQueryTabPane.getTabs().get(1);
            firstQueryTabPane.getTabs().remove(secondTab);
            firstQueryTabPane.getTabs().addFirst(secondTab);
            ((ConnectionTabController) first[1]).close();
            dragPolicy.complete(firstQueryTabPane.getTabDragPolicy());
        });
        assertEquals(TabPane.TabDragPolicy.REORDER, dragPolicy.get(20, TimeUnit.SECONDS));

        Object[] second = openConnectionTab(connection);
        TabPane secondQueryTabPane = (TabPane) ((Parent) second[0]).lookup("#queryTabPane");
        assertEquals("SELECT 2", ((QueryTabController) secondQueryTabPane.getTabs().getFirst().getUserData()).getSql());
        assertEquals("SELECT 1", ((QueryTabController) secondQueryTabPane.getTabs().get(1).getUserData()).getSql());
        Platform.runLater(((ConnectionTabController) second[1])::close);
    }

    @Test
    void testConnectionsPanelFitsTheLongestTreeRow() throws Exception {
        CreateConnectionService.getInstance().invoke(CreateConnectionDto.fromPrimitives(
            ConnectionPrimitivesMother.getValidLocal("una-conexion-con-un-nombre-bastante-largo-para-medir")
        ));
        CompletableFuture<SplitPane> loadedSplitPane = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                Parent root = new FXMLLoader(DesktopApplication.class.getResource("/modules/workspace/infrastructure/views/main-window.fxml"), core.config.TranslatorResourceBundle.getInstance()).load();
                Scene scene = new Scene(root, 1400, 700);
                scene.getStylesheets().add(DesktopApplication.class.getResource(DesktopApplication.PATH_STYLES).toExternalForm());
                root.applyCss();
                root.layout();
                loadedSplitPane.complete((SplitPane) root.lookup("#mainSplitPane"));
            }
            catch (Exception e) {
                loadedSplitPane.completeExceptionally(e);
            }
        });
        SplitPane mainSplitPane = loadedSplitPane.get(20, TimeUnit.SECONDS);
        Thread.sleep(1000);

        CompletableFuture<Double> panelWidth = new CompletableFuture<>();
        Platform.runLater(() -> {
            mainSplitPane.layout();
            panelWidth.complete(mainSplitPane.getItems().getFirst().getBoundsInParent().getWidth());
        });
        double fittedWidth = panelWidth.get(20, TimeUnit.SECONDS);

        // el nombre largo (unos 300 px en negrita normal) cabe entero, sin llegar al tope de media ventana
        assertTrue(fittedWidth > 300);
        assertTrue(fittedWidth < 700);
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
