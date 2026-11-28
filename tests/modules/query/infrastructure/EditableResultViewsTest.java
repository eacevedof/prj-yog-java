package modules.query.infrastructure;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import modules.shared.application.Primitives;

import core.boot.DesktopApplication;
import core.config.TranslatorResourceBundle;

import testcase.AbstractTestCase;

import modules.connections.domain.enums.ConnectionsKeyEnum;
import modules.connections.application.createconnection.CreateConnectionDto;
import modules.connections.application.createconnection.CreateConnectionService;
import modules.connections.infrastructure.factories.TargetSessionFactory;

import modules.history.domain.enums.QueryHistoryKeyEnum;

import modules.query.domain.enums.QueryKeyEnum;
import modules.query.application.executequery.ExecuteQueryDto;
import modules.query.application.executequery.ExecuteQueryService;
import modules.query.infrastructure.controllers.EditableResultController;
import modules.query.infrastructure.controllers.QueryTabController;

/**
 * Como SQLyog: el resultado de un SELECT de una sola tabla con clave primaria se edita y se guarda desde la pestaña de
 * query; un JOIN o una tabla sin clave se quedan de solo lectura y lo dicen. Sobre SQLite (sin servidor).
 */
final class EditableResultViewsTest extends AbstractTestCase {

    private static final String MAIN = "main";
    private static final String QUERY_SESSION = "ctab-editres:query-1";
    private static final String SEED_SESSION = "ctab-editres:seed";

    private static String connectionUuid;
    private Parent queryTabRoot;

    @BeforeAll
    static void seedAndStartJavaFx() throws Exception {
        bootFreshDatabase("EditableResultViewsTest");
        Path sqliteFile = Path.of("target", "javayog-editable-result.sqlite").toAbsolutePath();
        Files.deleteIfExists(sqliteFile);
        Files.createFile(sqliteFile);
        connectionUuid = String.valueOf(CreateConnectionService.getInstance().invoke(CreateConnectionDto.fromPrimitives(Primitives.of(
            "connectionName", "lite-editres", "dbEngine", "sqlite", "databaseName", sqliteFile.toString()
        ))).getConnection().get(ConnectionsKeyEnum.CONNECTION_UUID));
        execute("""
            CREATE TABLE items (id INTEGER PRIMARY KEY, name TEXT, price INTEGER);
            CREATE TABLE logs (message TEXT);
            INSERT INTO items (name, price) VALUES ('uno', 1), ('dos', 2);
            INSERT INTO logs (message) VALUES ('hola');
            """);
        try {
            Platform.startup(() -> { });
        }
        catch (IllegalStateException e) {
            // ya arrancado por otra clase de test
        }
    }

    @AfterAll
    static void closeSessions() {
        TargetSessionFactory.getInstance().closeAll();
    }

    @Test
    void testSingleTableSelectIsEditedAndSavedFromTheResult() throws Exception {
        QueryTabController queryTabController = loadQueryTab();
        EditableResultController resultPane = runAndWaitForResult(queryTabController, "SELECT id, name FROM items ORDER BY id", true);
        assertTrue(resultPane.isEditable());

        onFx(() -> {
            TableView<List<String>> tableView = resultPane.getResultGridPane().getTableView();
            tableView.getSelectionModel().clearAndSelect(0, tableView.getColumns().get(2));
            resultPane.getResultGridPane().setSelectedCellsNull();
            return null;
        });
        assertTrue(onFx(queryTabController::hasPendingChanges));

        onFx(() -> {
            resultPane.save();
            return null;
        });
        waitUntil(() -> !queryTabController.hasPendingChanges() && getCell("SELECT name FROM items WHERE id = 1") == null);
        assertEquals(null, getCell("SELECT name FROM items WHERE id = 1"));
        assertEquals("dos", getCell("SELECT name FROM items WHERE id = 2"));
    }

    @Test
    void testJoinsAndTablesWithoutKeyStayReadOnlyAndSayWhy() throws Exception {
        QueryTabController queryTabController = loadQueryTab();
        EditableResultController joinPane = runAndWaitForResult(queryTabController, "SELECT i.id, l.message FROM items i JOIN logs l", false);
        assertFalse(joinPane.isEditable());

        EditableResultController noKeyPane = runAndWaitForResult(queryTabController, "SELECT * FROM logs", false);
        waitUntil(() -> !noKeyPane.getEditStatusText().contains("..."));
        assertFalse(noKeyPane.isEditable());
        assertTrue(noKeyPane.getEditStatusText().contains("logs"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testSavedGridChangesShowInTheHistoryTabNewestFirstAndSortable() throws Exception {
        QueryTabController queryTabController = loadQueryTab();
        EditableResultController resultPane = runAndWaitForResult(queryTabController, "SELECT id, price FROM items ORDER BY id", true);
        onFx(() -> {
            TableView<List<String>> tableView = resultPane.getResultGridPane().getTableView();
            tableView.getSelectionModel().clearAndSelect(1, tableView.getColumns().get(2));
            resultPane.getResultGridPane().setSelectedCellsNull();
            resultPane.save();
            return null;
        });
        waitUntil(() -> !queryTabController.hasPendingChanges() && getCell("SELECT price FROM items WHERE id = 2") == null);

        TabPane resultTabPane = onFx(() -> (TabPane) queryTabRoot.lookup("#resultTabPane"));
        TableView<Map<String, Object>> historyTableView = onFx(() -> {
            resultTabPane.getSelectionModel().select(resultTabPane.getTabs().getLast());
            queryTabRoot.applyCss();
            return (TableView<Map<String, Object>>) queryTabRoot.lookup("#historyTableView");
        });
        List<Map<String, Object>> newestFirst = onFx(() -> List.copyOf(historyTableView.getItems()));
        // lo ultimo es el SELECT que se vuelve a lanzar al guardar; justo antes, el UPDATE con sus valores
        String updateSql = newestFirst.stream()
            .map(historyRow -> String.valueOf(historyRow.get(QueryHistoryKeyEnum.SQL_QUERY)))
            .filter(sql -> sql.startsWith("UPDATE"))
            .findFirst().orElse("");
        assertTrue(updateSql.contains("NULL") && updateSql.contains("2"));
        assertTrue(getId(newestFirst.getFirst()) > getId(newestFirst.getLast()));

        List<Map<String, Object>> oldestFirst = onFx(() -> {
            TableColumn<Map<String, Object>, ?> idColumn = historyTableView.getColumns().getFirst();
            idColumn.setSortType(TableColumn.SortType.ASCENDING);
            historyTableView.getSortOrder().setAll(idColumn);
            return List.copyOf(historyTableView.getItems());
        });
        assertTrue(getId(oldestFirst.getFirst()) < getId(oldestFirst.getLast()));
    }

    private int getId(Map<String, Object> historyRow) {
        return Integer.parseInt(String.valueOf(historyRow.get(QueryHistoryKeyEnum.ID)));
    }

    private QueryTabController loadQueryTab() throws Exception {
        return onFx(() -> {
            FXMLLoader fxmlLoader = new FXMLLoader(DesktopApplication.class.getResource("/modules/query/infrastructure/views/query-tab.fxml"), TranslatorResourceBundle.getInstance());
            Parent root = fxmlLoader.load();
            new Scene(root);
            // el SplitPane mete sus paneles en la escena al crear su skin: sin esto lookup() no los ve
            root.applyCss();
            queryTabRoot = root;
            QueryTabController queryTabController = fxmlLoader.getController();
            queryTabController.setup(connectionUuid, QUERY_SESSION, () -> MAIN, databaseName -> { }, () -> { }, () -> { });
            return queryTabController;
        });
    }

    /**
     * Ejecuta y espera a la rejilla; si isEditable, tambien a que lea las columnas de la tabla y quede editable.
     */
    private EditableResultController runAndWaitForResult(QueryTabController queryTabController, String sql, boolean isEditable) throws Exception {
        TabPane resultTabPane = onFx(() -> (TabPane) queryTabRoot.lookup("#resultTabPane"));
        Object previousContent = onFx(() -> resultTabPane.getTabs().getFirst().getContent());
        onFx(() -> {
            queryTabController.setSqlAndExecute(sql);
            return null;
        });
        waitUntil(() -> resultTabPane.getTabs().getFirst().getContent() instanceof EditableResultController pane && pane != previousContent
            && (!isEditable || pane.isEditable()));
        return onFx(() -> (EditableResultController) resultTabPane.getTabs().getFirst().getContent());
    }

    private static void execute(String sql) {
        ExecuteQueryService.getInstance().invoke(ExecuteQueryDto.fromPrimitives(Primitives.of(
            "connectionUuid", connectionUuid,
            "sessionKey", SEED_SESSION,
            "databaseName", MAIN,
            "sqlQuery", sql,
            "maxRows", 100
        )));
    }

    @SuppressWarnings("unchecked")
    private static String getCell(String sql) {
        Map<String, Object> result = ExecuteQueryService.getInstance().invoke(ExecuteQueryDto.fromPrimitives(Primitives.of(
            "connectionUuid", connectionUuid,
            "sessionKey", SEED_SESSION,
            "databaseName", MAIN,
            "sqlQuery", sql,
            "maxRows", 1
        ))).getResults().getFirst();
        return ((List<List<String>>) result.get(QueryKeyEnum.ROWS)).getFirst().getFirst();
    }

    private void waitUntil(Callable<Boolean> condition) throws Exception {
        long deadline = System.currentTimeMillis() + 15000;
        while (System.currentTimeMillis() < deadline) {
            if (onFx(condition)) return;
            Thread.sleep(100);
        }
        throw new AssertionError("no se cumplio a tiempo");
    }

    private static <T> T onFx(Callable<T> work) throws Exception {
        CompletableFuture<T> future = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                future.complete(work.call());
            }
            catch (Throwable e) {
                future.completeExceptionally(e);
            }
        });
        return future.get(20, TimeUnit.SECONDS);
    }
}
