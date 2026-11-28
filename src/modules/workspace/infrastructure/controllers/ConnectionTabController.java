package modules.workspace.infrastructure.controllers;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.concurrent.Task;
import javafx.event.Event;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseButton;
import javafx.util.Duration;

import modules.shared.domain.services.SqlDialect;
import modules.shared.domain.services.SystemDatabaseDetector;
import modules.shared.application.Primitives;
import modules.shared.infrastructure.components.icons.AppIconEnum;
import modules.shared.infrastructure.controllers.ConnectionTreeHandler;
import modules.shared.infrastructure.components.combobox.FilterableComboBoxComponent;
import modules.shared.infrastructure.components.icons.AppIcon;
import modules.shared.infrastructure.components.theme.ThemeComponent;
import modules.shared.infrastructure.components.uuid.UuidGenerator;
import modules.shared.infrastructure.controllers.AbstractFxController;
import modules.shared.infrastructure.controllers.ConnectionViewController;
import static modules.shared.application.Primitives.getBool;
import static modules.shared.application.Primitives.getTrimmedString;

import core.config.TranslatorResourceBundle;
import static core.config.Global.__;

import modules.connections.domain.enums.ConnectionsKeyEnum;
import modules.connections.domain.enums.DbEngineEnum;
import modules.connections.infrastructure.factories.TargetSessionFactory;

import modules.explorer.application.gettableinfo.GetTableInfoDto;
import modules.explorer.application.gettableinfo.GetTableInfoService;
import modules.explorer.infrastructure.components.TableInfoFormatter;
import modules.explorer.infrastructure.controllers.ExplorerTreeController;

import modules.query.infrastructure.components.SchemaCacheComponent;
import modules.query.infrastructure.controllers.ExecuteSqlFileDialogController;
import modules.query.infrastructure.controllers.QueryTabController;
import modules.query.infrastructure.controllers.TableDataTabController;

import modules.dbcopy.infrastructure.controllers.CopyDatabaseDialogController;
import modules.dbcopy.infrastructure.controllers.RenameDatabaseDialogController;
import modules.dbcopy.infrastructure.controllers.SqlDumpDialogController;

import modules.files.application.savefilesfolder.SaveFilesFolderDto;
import modules.files.application.savefilesfolder.SaveFilesFolderResultDto;
import modules.files.application.syncfilesfolder.SyncFilesFolderDto;
import modules.files.application.syncfilesfolder.SyncFilesFolderResultDto;
import modules.files.application.savefilesfolder.SaveFilesFolderService;
import modules.files.application.syncfilesfolder.SyncFilesFolderService;

import modules.workspace.domain.enums.WorkspaceKeyEnum;
import modules.workspace.application.getquerytabs.GetQueryTabsDto;
import modules.workspace.application.getquerytabs.GetQueryTabsResultDto;
import modules.workspace.application.savequerytabs.SaveQueryTabsDto;
import modules.workspace.application.getquerytabs.GetQueryTabsService;
import modules.workspace.application.savequerytabs.SaveQueryTabsService;

/**
 * Una conexion abierta: sus pestañas de query (el arbol de objetos va bajo la conexion en el panel de la izquierda). Tiene una BD activa comun (la que se elige en el
 * arbol o con un USE) que se aplica a cada ejecucion. Cada pestaña de query lleva su propia sesion MySQL, asi una
 * query larga no bloquea ni el arbol ni las otras pestañas. Al cerrar, se cancela lo que corra y se cierran sus sesiones.
 */
public final class ConnectionTabController extends AbstractFxController implements ConnectionViewController {

    private static final String PATH_QUERY_TAB = "/modules/query/infrastructure/views/query-tab.fxml";
    private static final String PATH_TABLE_DATA_TAB = "/modules/query/infrastructure/views/table-data-tab.fxml";
    private static final String CONNECTION_TAB_PREFIX = "ctab-";
    private static final String PGSQL_INITIAL_SCHEMA = "public";
    private static final String EXPLORER_SESSION = "explorer";
    private static final String FILES_SESSION_SUFFIX = "files";
    private static final String STYLE_SYSTEM_DATABASE = "active-database-system";
    private static final String STYLE_SYSTEM_DATABASE_ITEM = "explorer-db-system";
    // las pestañas se guardan un poco despues del ultimo cambio, no a cada tecla
    private static final double SAVE_STATE_DELAY_MS = 1500;

    private final ExplorerTreeController explorerTreeController = new ExplorerTreeController();
    @FXML
    private TabPane queryTabPane;
    @FXML
    private ComboBox<String> activeDatabaseComboBox;
    @FXML
    private Label systemDatabaseWarningLabel;
    @FXML
    private Button exportSqlDumpButton;
    @FXML
    private Label filesStatusLabel;
    @FXML
    private Button saveFilesButton;
    @FXML
    private Button reloadFilesButton;

    private String connectionUuid = "";
    private String connectionTabUuid = "";
    private String activeDatabase = "";
    private Supplier<Set<String>> openConnectionUuidsSupplier = Set::of;
    private SystemDatabaseDetector systemDatabaseDetector = SystemDatabaseDetector.getInstance("");
    private FilterableComboBoxComponent activeDatabaseFilterComponent;
    private int queryTabCounter;
    private SchemaCacheComponent schemaCache;
    private final PauseTransition saveStateDelay = new PauseTransition(Duration.millis(SAVE_STATE_DELAY_MS));
    private boolean isRestoring;

    /**
     * Lo llama la ventana principal al abrir la conexion: pinta el arbol y deja una pestaña de query vacia.
     */
    @Override
    public void setup(Map<String, Object> connection) {
        connectionUuid = getTrimmedString(connection.get(ConnectionsKeyEnum.CONNECTION_UUID));
        connectionTabUuid = UuidGenerator.getInstance().getUniqueIdWithPrefix(CONNECTION_TAB_PREFIX);
        activeDatabaseFilterComponent = FilterableComboBoxComponent.getInstance(activeDatabaseComboBox);
        activeDatabaseFilterComponent.setOnChosen(this::setActiveDatabase);
        // en PostgreSQL la BD de la conexion es la BD a la que se conecta; lo "activo" es el esquema (public al empezar)
        // en SQLite la "BD" de la conexion es el fichero; lo activo es main
        String dbEngine = getTrimmedString(connection.get(ConnectionsKeyEnum.DB_ENGINE));
        systemDatabaseDetector = SystemDatabaseDetector.getInstance(dbEngine);
        activeDatabaseComboBox.setCellFactory(listView -> new DatabaseNameCell());
        if (DbEngineEnum.PGSQL.equals(dbEngine)) setActiveDatabase(PGSQL_INITIAL_SCHEMA);
        else if (DbEngineEnum.SQLITE.equals(dbEngine) || DbEngineEnum.FILES.equals(dbEngine)) setActiveDatabase(SqlDialect.SQLITE_MAIN);
        else setActiveDatabase(getTrimmedString(connection.get(ConnectionsKeyEnum.DATABASE_NAME)));
        schemaCache = new SchemaCacheComponent(connectionUuid, connectionTabUuid + TargetSessionFactory.SESSION_KEY_SEPARATOR + "completion");

        explorerTreeController.setup(
            connectionUuid,
            getExplorerSessionKey(),
            this::getActiveDatabase,
            this::setActiveDatabase,
            this::insertTextInSelectedQueryTab,
            this::executeInNewQueryTab
        );
        explorerTreeController.setOnShowTableInfo(this::showTableInfo);
        explorerTreeController.setOnShowTableData(this::showTableData);
        explorerTreeController.setOnRefreshed(() -> schemaCache.clear());
        explorerTreeController.setOnCopyToOtherDatabase(this::openCopyDialog);
        explorerTreeController.setOnExportSqlDump(this::openSqlDumpDialog);
        explorerTreeController.setOnExecuteSqlFile(this::openExecuteSqlFileDialog);
        explorerTreeController.setOnRenameDatabase(this::openRenameDatabaseDialog);
        explorerTreeController.setOnDuplicateDatabase(this::openDuplicateDatabaseDialog);
        explorerTreeController.setOnDatabasesLoaded(this::setDatabaseNames);
        activeDatabaseComboBox.setPromptText(__("(ninguna)"));
        // el volcado lee la BD como la copia: solo MySQL/MariaDB
        boolean isMysql = DbEngineEnum.MYSQL.equals(dbEngine);
        exportSqlDumpButton.setVisible(isMysql);
        exportSqlDumpButton.setManaged(isMysql);
        boolean isFiles = DbEngineEnum.FILES.equals(dbEngine);
        for (Node filesControl : List.of(filesStatusLabel, saveFilesButton, reloadFilesButton)) {
            filesControl.setVisible(isFiles);
            filesControl.setManaged(isFiles);
        }
        if (isFiles) syncFilesFolder(false);
        restoreQueryTabs();
        saveStateDelay.setOnFinished(event -> saveState());
        queryTabPane.getTabs().addListener((ListChangeListener<Tab>) change -> scheduleSaveState());
        queryTabPane.getSelectionModel().selectedItemProperty().addListener((observable, oldTab, newTab) -> scheduleSaveState());
    }

    /**
     * Motor "Ficheros": carga en el SQLite de trabajo los ficheros nuevos o cambiados de la carpeta (todos si
     * isReloadForced) y al acabar recarga el arbol. Un fichero que no se pudo leer se avisa sin parar a los demas.
     */
    private void syncFilesFolder(boolean isReloadForced) {
        SyncFilesFolderDto syncFilesFolderDto = SyncFilesFolderDto.fromPrimitives(Primitives.of(
            "connectionUuid", connectionUuid,
            "sessionKey", getFilesSessionKey(),
            "isReloadForced", isReloadForced
        ));
        setFilesStatus(__("Cargando ficheros..."));
        Task<SyncFilesFolderResultDto> syncTask = new Task<>() {
            @Override
            protected SyncFilesFolderResultDto call() throws SQLException {
                return SyncFilesFolderService.getInstance().invoke(syncFilesFolderDto);
            }
        };
        syncTask.setOnSucceeded(event -> {
            SyncFilesFolderResultDto syncFilesFolderResultDto = syncTask.getValue();
            setFilesStatus(__("{0} fichero(s) cargado(s), {1} sin cambios", syncFilesFolderResultDto.getLoadedCount(), syncFilesFolderResultDto.getUnchangedCount()));
            schemaCache.clear();
            explorerTreeController.loadDatabases();
            if (!syncFilesFolderResultDto.getErrors().isEmpty()) showErrorMessage(String.join("\n", syncFilesFolderResultDto.getErrors()));
        });
        syncTask.setOnFailed(event -> {
            setFilesStatus(getErrorMessage(syncTask.getException()));
            showError(syncTask.getException());
        });
        startDaemon(syncTask, "files-sync");
    }

    /**
     * "Guardar en ficheros": solo se reescriben los ficheros cuya tabla cambio.
     */
    @FXML
    private void onSaveFiles() {
        SaveFilesFolderDto saveFilesFolderDto = SaveFilesFolderDto.fromPrimitives(Primitives.of(
            "connectionUuid", connectionUuid,
            "sessionKey", getFilesSessionKey()
        ));
        saveFilesButton.setDisable(true);
        setFilesStatus(__("Guardando ficheros..."));
        Task<SaveFilesFolderResultDto> saveTask = new Task<>() {
            @Override
            protected SaveFilesFolderResultDto call() throws SQLException, IOException {
                return SaveFilesFolderService.getInstance().invoke(saveFilesFolderDto);
            }
        };
        saveTask.setOnSucceeded(event -> {
            saveFilesButton.setDisable(false);
            SaveFilesFolderResultDto saveFilesFolderResultDto = saveTask.getValue();
            List<String> writtenFileNames = new ArrayList<>(saveFilesFolderResultDto.getSavedFileNames());
            writtenFileNames.addAll(saveFilesFolderResultDto.getCreatedFileNames());
            setFilesStatus(writtenFileNames.isEmpty()
                ? __("Nada que guardar: los ficheros ya están al día")
                : __("Guardado: {0}", String.join(", ", writtenFileNames)));
        });
        saveTask.setOnFailed(event -> {
            saveFilesButton.setDisable(false);
            setFilesStatus(getErrorMessage(saveTask.getException()));
            showError(saveTask.getException());
        });
        startDaemon(saveTask, "files-save");
    }

    /**
     * Se pregunta antes: lo editado y no guardado se pierde.
     */
    @FXML
    private void onReloadFiles() {
        if (!isConfirmed(__("¿Volver a leer todos los ficheros de la carpeta? Se pierde lo que no hayas guardado."))) return;
        syncFilesFolder(true);
    }

    private String getFilesSessionKey() {
        return connectionTabUuid + TargetSessionFactory.SESSION_KEY_SEPARATOR + FILES_SESSION_SUFFIX;
    }

    private void setFilesStatus(String status) {
        filesStatusLabel.setText(status);
    }

    private void startDaemon(Task<?> task, String threadName) {
        Thread thread = new Thread(task, threadName);
        thread.setDaemon(true);
        thread.start();
    }

    public String getActiveDatabase() {
        return activeDatabase;
    }

    /**
     * Las conexiones abiertas en la ventana (destinos de la copia).
     */
    public void setOpenConnectionUuidsSupplier(Supplier<Set<String>> openConnectionUuidsSupplier) {
        this.openConnectionUuidsSupplier = openConnectionUuidsSupplier;
    }

    /**
     * Feature 29: el dialogo de copia de la BD (tableName vacio) o de una tabla a otra BD u otro servidor.
     */
    private void openCopyDialog(String databaseName, String tableName) {
        try {
            CopyDatabaseDialogController.open(queryTabPane.getScene().getWindow(), connectionUuid, databaseName, tableName, openConnectionUuidsSupplier.get());
        }
        catch (RuntimeException e) {
            showError(e);
        }
    }

    /**
     * "Duplicar BD" (MySQL): el dialogo de copia hacia esta misma conexion; al acabar se recargan las BD del arbol.
     */
    private void openDuplicateDatabaseDialog(String databaseName) {
        try {
            CopyDatabaseDialogController.openDuplicate(queryTabPane.getScene().getWindow(), connectionUuid, databaseName, () -> {
                schemaCache.clear();
                explorerTreeController.loadDatabases();
            });
        }
        catch (RuntimeException e) {
            showError(e);
        }
    }

    @FXML
    private void onExportSqlDump() {
        openSqlDumpDialog(activeDatabase);
    }

    @FXML
    private void onExecuteSqlFile() {
        openExecuteSqlFileDialog(activeDatabase);
    }

    /**
     * El "SQL Dump" de SQLyog sobre una BD de esta conexion (MySQL/MariaDB).
     */
    private void openSqlDumpDialog(String databaseName) {
        try {
            SqlDumpDialogController.open(queryTabPane.getScene().getWindow(), connectionUuid, databaseName);
        }
        catch (RuntimeException e) {
            showError(e);
        }
    }

    /**
     * "Renombrar BD" (MySQL): al acabar se recargan las BD del arbol y, si la renombrada era la activa, pasa a serlo la
     * nueva.
     */
    private void openRenameDatabaseDialog(String databaseName) {
        try {
            RenameDatabaseDialogController.open(queryTabPane.getScene().getWindow(), connectionUuid, databaseName, (oldName, newName) -> {
                if (oldName.equals(activeDatabase)) setActiveDatabase(newName);
                schemaCache.clear();
                explorerTreeController.loadDatabases();
            });
        }
        catch (RuntimeException e) {
            showError(e);
        }
    }

    /**
     * "Execute Query(s) From A File" de SQLyog en una BD de esta conexion (cualquier motor SQL).
     */
    private void openExecuteSqlFileDialog(String databaseName) {
        try {
            ExecuteSqlFileDialogController.open(queryTabPane.getScene().getWindow(), connectionUuid, databaseName);
        }
        catch (RuntimeException e) {
            showError(e);
        }
    }

    /**
     * Feature 10: la pestaña Info de una tabla o vista (DDL, indices y tamaño), leida en segundo plano con la sesion
     * del arbol.
     */
    private void showTableInfo(String databaseName, String tableName) {
        TextArea infoTextArea = new TextArea(__("Cargando..."));
        infoTextArea.setEditable(false);
        infoTextArea.getStyleClass().add("result-text");
        Tab infoTab = new Tab(__("Info: {0}", tableName), infoTextArea);
        infoTab.setGraphic(AppIcon.of(AppIconEnum.INFO));
        infoTab.setTooltip(new Tooltip(databaseName + "." + tableName));
        queryTabPane.getTabs().add(infoTab);
        queryTabPane.getSelectionModel().select(infoTab);

        GetTableInfoDto getTableInfoDto = GetTableInfoDto.fromPrimitives(Primitives.of(
            "connectionUuid", connectionUuid,
            "sessionKey", getExplorerSessionKey(),
            "databaseName", databaseName,
            "tableName", tableName
        ));
        Task<String> tableInfoTask = new Task<>() {
            @Override
            protected String call() throws SQLException {
                return TableInfoFormatter.getInstance().getText(GetTableInfoService.getInstance().invoke(getTableInfoDto));
            }
        };
        tableInfoTask.setOnSucceeded(event -> infoTextArea.setText(tableInfoTask.getValue()));
        tableInfoTask.setOnFailed(event -> infoTextArea.setText(getErrorMessage(tableInfoTask.getException())));
        Thread tableInfoThread = new Thread(tableInfoTask, "table-info");
        tableInfoThread.setDaemon(true);
        tableInfoThread.start();
    }

    /**
     * Feature 7: la pestaña Datos de una tabla; si ya esta abierta, se va a ella (con su filtro intacto).
     */
    private void showTableData(String databaseName, String tableName) {
        for (Tab tab : queryTabPane.getTabs()) {
            if (tab.getUserData() instanceof TableDataTabController tableDataTabController && tableDataTabController.isTable(databaseName, tableName)) {
                queryTabPane.getSelectionModel().select(tab);
                return;
            }
        }

        FXMLLoader fxmlLoader = new FXMLLoader(ConnectionTabController.class.getResource(PATH_TABLE_DATA_TAB), TranslatorResourceBundle.getInstance());
        Parent tableDataRoot;
        try {
            tableDataRoot = fxmlLoader.load();
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        queryTabCounter++;
        TableDataTabController tableDataTabController = fxmlLoader.getController();
        tableDataTabController.setup(
            connectionUuid,
            connectionTabUuid + TargetSessionFactory.SESSION_KEY_SEPARATOR + "data-" + queryTabCounter,
            databaseName,
            tableName
        );

        Tab tableDataTab = new Tab(__("Datos: {0}", tableName), tableDataRoot);
        tableDataTab.setGraphic(AppIcon.of(AppIconEnum.TABLE));
        tableDataTab.setTooltip(new Tooltip(databaseName + "." + tableName));
        tableDataTab.setUserData(tableDataTabController);
        tableDataTab.setOnCloseRequest(event -> {
            if (tableDataTabController.hasPendingChanges() && !isConfirmed(__("La pestaña tiene cambios sin guardar. ¿Cerrarla y perderlos?"))) {
                event.consume();
            }
        });
        tableDataTab.setOnClosed(event -> closeSessionsInBackground(tableDataTabController.getSessionKey()));
        tableDataTab.setContextMenu(getTabContextMenu(tableDataTab, null));
        queryTabPane.getTabs().add(tableDataTab);
        queryTabPane.getSelectionModel().select(tableDataTab);
        tableDataTabController.onRefresh();
    }

    private String getExplorerSessionKey() {
        return connectionTabUuid + TargetSessionFactory.SESSION_KEY_SEPARATOR + EXPLORER_SESSION;
    }

    /**
     * Cancela lo que se este ejecutando y cierra las sesiones de esta conexion, fuera del hilo de la UI: cerrar una
     * sesion con una query en marcha espera a que el servidor la corte.
     */
    /**
     * El arbol va bajo la conexion en el panel de la izquierda; la ventana lo engancha y empieza a cargarse.
     */
    @Override
    public ConnectionTreeHandler getConnectionTreeHandler() {
        return explorerTreeController;
    }

    /**
     * Las pestañas de query (titulo, SQL y cual esta delante) y la BD activa, para abrir la conexion como se dejo. Las
     * de Datos e Info no se guardan: se vuelven a abrir desde el arbol.
     */
    @Override
    public void saveState() {
        saveStateDelay.stop();
        if (isRestoring || connectionUuid.isEmpty()) return;
        Tab selectedTab = queryTabPane.getSelectionModel().getSelectedItem();
        List<Map<String, Object>> queryTabs = new ArrayList<>();
        for (Tab tab : queryTabPane.getTabs()) {
            if (!(tab.getUserData() instanceof QueryTabController queryTabController)) continue;
            queryTabs.add(Primitives.of(
                WorkspaceKeyEnum.TAB_TITLE, getQueryTabTitle(tab, queryTabs.size() + 1),
                WorkspaceKeyEnum.SQL_TEXT, queryTabController.getSql(),
                WorkspaceKeyEnum.IS_SELECTED, tab == selectedTab
            ));
        }
        try {
            SaveQueryTabsService.getInstance().invoke(SaveQueryTabsDto.fromPrimitives(Primitives.of(
                "connectionUuid", connectionUuid,
                "activeDatabase", activeDatabase,
                "queryTabs", queryTabs
            )));
        }
        catch (RuntimeException e) {
            // se guarda solo, sin que nadie lo pida: un fallo no debe sacar un dialogo a cada tecla
            System.err.println("multidbs: no se pudieron guardar las pestañas: " + getErrorMessage(e));
        }
    }

    @Override
    public void close() {
        saveState();
        explorerTreeController.detach();
        for (QueryTabController queryTabController : getQueryTabControllers()) {
            queryTabController.cancel();
        }
        closeSessionsInBackground(connectionTabUuid);
    }

    /**
     * Las BD que lee el arbol de la conexion (al abrirla y al refrescar): las del desplegable de la BD activa.
     */
    private void setDatabaseNames(List<String> databaseNames) {
        activeDatabaseFilterComponent.setItems(databaseNames);
        showActiveDatabaseInComboBox();
    }

    /**
     * El desplegable ensena la BD activa aunque no este en la lista (un USE a una BD que el arbol aun no conoce).
     */
    private void showActiveDatabaseInComboBox() {
        activeDatabaseFilterComponent.showValue(activeDatabase.isEmpty() ? null : activeDatabase);
    }

    private void setActiveDatabase(String databaseName) {
        if (databaseName == null || databaseName.equals(activeDatabase)) return;
        activeDatabase = databaseName;
        showActiveDatabaseInComboBox();
        showSystemDatabaseWarning();
        explorerTreeController.refreshActiveDatabase();
        scheduleSaveState();
    }

    /**
     * Una BD de sistema como activa (mysql, information_schema, master...): selector en naranja y aviso al lado.
     */
    private void showSystemDatabaseWarning() {
        boolean isSystemDatabase = systemDatabaseDetector.isSystemDatabase(activeDatabase);
        systemDatabaseWarningLabel.setVisible(isSystemDatabase);
        systemDatabaseWarningLabel.setManaged(isSystemDatabase);
        activeDatabaseComboBox.getStyleClass().remove(STYLE_SYSTEM_DATABASE);
        if (isSystemDatabase) activeDatabaseComboBox.getStyleClass().add(STYLE_SYSTEM_DATABASE);
    }

    private QueryTabController addQueryTab() {
        return addQueryTab("", "");
    }

    /**
     * Una pestaña de query; title vacio = "Query N". Las recuperadas llegan con su titulo y su SQL.
     */
    private QueryTabController addQueryTab(String title, String sql) {
        FXMLLoader fxmlLoader = new FXMLLoader(ConnectionTabController.class.getResource(PATH_QUERY_TAB), TranslatorResourceBundle.getInstance());
        Parent queryTabRoot;
        try {
            queryTabRoot = fxmlLoader.load();
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        queryTabCounter++;
        String queryTabKey = connectionTabUuid + TargetSessionFactory.SESSION_KEY_SEPARATOR + "query-" + queryTabCounter;
        Tab queryTab = new Tab();
        Label queryTabLabel = new Label(title.isEmpty() ? __("Query {0}", queryTabCounter) : title);
        queryTabLabel.setMnemonicParsing(false);
        queryTabLabel.setGraphic(AppIcon.of(AppIconEnum.QUERY));
        queryTabLabel.setOnMouseClicked(mouseEvent -> {
            if (mouseEvent.getButton() == MouseButton.PRIMARY && mouseEvent.getClickCount() == 2) {
                renameQueryTab(queryTabLabel);
            }
        });
        queryTab.setGraphic(queryTabLabel);
        queryTab.setContent(queryTabRoot);

        QueryTabController queryTabController = fxmlLoader.getController();
        queryTabController.setup(
            connectionUuid,
            queryTabKey,
            this::getActiveDatabase,
            this::setActiveDatabase,
            this::addQueryTab,
            () -> closeQueryTab(queryTab)
        );
        queryTabController.setSchemaCache(schemaCache);
        if (!sql.isEmpty()) queryTabController.setSql(sql);
        queryTabController.setOnSqlChanged(this::scheduleSaveState);
        queryTab.setUserData(queryTabController);
        queryTab.setOnCloseRequest(event -> {
            if (queryTabController.hasPendingChanges() && !isConfirmed(__("La pestaña tiene cambios sin guardar. ¿Cerrarla y perderlos?"))) {
                event.consume();
            }
        });
        queryTab.setOnClosed(event -> onQueryTabClosed(queryTabController));
        queryTab.setContextMenu(getTabContextMenu(queryTab, () -> renameQueryTab(queryTabLabel)));

        queryTabPane.getTabs().add(queryTab);
        queryTabPane.getSelectionModel().select(queryTab);
        Platform.runLater(queryTabController::focusEditor);
        return queryTabController;
    }

    /**
     * El menu de cada pestaña (query o datos), como el de los IDE: cerrar esta, las demas, todas, las no modificadas y
     * las de la derecha; y renombrar si es de query (onRename null en las de datos).
     */
    private ContextMenu getTabContextMenu(Tab tab, Runnable onRename) {
        MenuItem closeMenuItem = new MenuItem(__("Cerrar (Ctrl+W)"));
        closeMenuItem.setOnAction(actionEvent -> closeTabs(List.of(tab)));
        MenuItem closeOthersMenuItem = new MenuItem(__("Cerrar las demás"));
        closeOthersMenuItem.setOnAction(actionEvent -> closeTabs(queryTabPane.getTabs().stream().filter(otherTab -> otherTab != tab).toList()));
        MenuItem closeAllMenuItem = new MenuItem(__("Cerrar todas"));
        closeAllMenuItem.setOnAction(actionEvent -> closeTabs(List.copyOf(queryTabPane.getTabs())));
        MenuItem closeUnmodifiedMenuItem = new MenuItem(__("Cerrar las no modificadas"));
        closeUnmodifiedMenuItem.setOnAction(actionEvent -> closeTabs(queryTabPane.getTabs().stream().filter(otherTab -> !isTabModified(otherTab)).toList()));
        MenuItem closeRightMenuItem = new MenuItem(__("Cerrar las de la derecha"));
        closeRightMenuItem.setOnAction(actionEvent -> {
            List<Tab> tabs = queryTabPane.getTabs();
            closeTabs(List.copyOf(tabs.subList(tabs.indexOf(tab) + 1, tabs.size())));
        });
        ContextMenu contextMenu = new ContextMenu(closeMenuItem, closeOthersMenuItem, closeAllMenuItem, closeUnmodifiedMenuItem, closeRightMenuItem);
        if (onRename != null) {
            MenuItem renameMenuItem = new MenuItem(__("Renombrar... (doble clic)"));
            renameMenuItem.setOnAction(actionEvent -> onRename.run());
            contextMenu.getItems().addAll(new SeparatorMenuItem(), renameMenuItem);
        }
        contextMenu.setOnShowing(windowEvent -> {
            List<Tab> tabs = queryTabPane.getTabs();
            closeOthersMenuItem.setDisable(tabs.size() < 2);
            closeRightMenuItem.setDisable(tabs.indexOf(tab) == tabs.size() - 1);
            closeUnmodifiedMenuItem.setDisable(tabs.stream().allMatch(this::isTabModified));
        });
        return contextMenu;
    }

    /**
     * Query: su SQL ya no es el de cuando se abrio. Datos: tiene cambios sin guardar.
     */
    private boolean isTabModified(Tab tab) {
        if (tab.getUserData() instanceof QueryTabController queryTabController) return queryTabController.isModified() || queryTabController.hasPendingChanges();
        if (tab.getUserData() instanceof TableDataTabController tableDataTabController) return tableDataTabController.hasPendingChanges();
        return false;
    }

    /**
     * Cierra como la X de cada una: pasa por su aviso de cerrar (los cambios sin guardar de una de datos) y por su
     * limpieza (quitar una pestaña desde codigo no lanza ninguno de los dos). Si se cierran todas las de query, queda
     * una vacia.
     */
    private void closeTabs(List<Tab> tabs) {
        for (Tab tab : tabs) {
            Event closeRequest = new Event(Tab.TAB_CLOSE_REQUEST_EVENT);
            if (tab.getOnCloseRequest() != null) tab.getOnCloseRequest().handle(closeRequest);
            if (closeRequest.isConsumed()) continue;
            queryTabPane.getTabs().remove(tab);
            if (tab.getOnClosed() != null) tab.getOnClosed().handle(new Event(Tab.CLOSED_EVENT));
        }
    }

    /**
     * Ctrl+W: quitar la pestaña desde codigo no lanza onClosed, por eso se llama a mano.
     */
    private void closeQueryTab(Tab queryTab) {
        closeTabs(List.of(queryTab));
    }

    /**
     * Siempre queda al menos una pestaña de query, como en SQLyog.
     */
    private void onQueryTabClosed(QueryTabController queryTabController) {
        queryTabController.cancel();
        closeSessionsInBackground(queryTabController.getSessionKey());
        if (getQueryTabControllers().isEmpty()) {
            addQueryTab();
        }
    }

    private void renameQueryTab(Label queryTabLabel) {
        TextInputDialog textInputDialog = new TextInputDialog(queryTabLabel.getText());
        ThemeComponent.getInstance().applyTo(textInputDialog);
        textInputDialog.setTitle(__("Renombrar pestaña"));
        textInputDialog.setHeaderText(null);
        textInputDialog.setContentText(__("Nombre:"));
        textInputDialog.showAndWait()
            .map(String::trim)
            .filter(newName -> !newName.isEmpty())
            .ifPresent(newName -> {
                queryTabLabel.setText(newName);
                scheduleSaveState();
            });
    }

    /**
     * Abre la conexion como se dejo: sus pestañas de query con su SQL, la que estaba delante y su BD activa. Sin nada
     * guardado (o si falla la lectura), una pestaña vacia como siempre.
     */
    private void restoreQueryTabs() {
        GetQueryTabsResultDto savedQueryTabs;
        try {
            savedQueryTabs = GetQueryTabsService.getInstance().invoke(GetQueryTabsDto.fromPrimitives(Primitives.of(
                "connectionUuid", connectionUuid
            )));
        }
        catch (RuntimeException e) {
            System.err.println("multidbs: no se pudieron leer las pestañas guardadas: " + getErrorMessage(e));
            addQueryTab();
            return;
        }
        if (!savedQueryTabs.getActiveDatabase().isEmpty()) setActiveDatabase(savedQueryTabs.getActiveDatabase());
        if (savedQueryTabs.getQueryTabs().isEmpty()) {
            addQueryTab();
            return;
        }

        isRestoring = true;
        Tab selectedTab = null;
        for (Map<String, Object> savedQueryTab : savedQueryTabs.getQueryTabs()) {
            Object sqlText = savedQueryTab.get(WorkspaceKeyEnum.SQL_TEXT);
            addQueryTab(getTrimmedString(savedQueryTab.get(WorkspaceKeyEnum.TAB_TITLE)), sqlText == null ? "" : String.valueOf(sqlText));
            if (getBool(savedQueryTab.get(WorkspaceKeyEnum.IS_SELECTED))) selectedTab = queryTabPane.getTabs().getLast();
        }
        if (selectedTab != null) queryTabPane.getSelectionModel().select(selectedTab);
        isRestoring = false;
    }

    private void scheduleSaveState() {
        if (isRestoring || connectionUuid.isEmpty()) return;
        saveStateDelay.playFromStart();
    }

    /**
     * El titulo de la pestaña; sin titulo, "Query N" con N su posicion entre las de query (las de Datos e Info no cuentan).
     */
    private String getQueryTabTitle(Tab queryTab, int queryTabPosition) {
        String title = queryTab.getGraphic() instanceof Label queryTabLabel ? queryTabLabel.getText().trim() : "";
        if (title.isEmpty()) return __("Query {0}", queryTabPosition);
        return title.length() > WorkspaceKeyEnum.MAX_TAB_TITLE_LENGTH ? title.substring(0, WorkspaceKeyEnum.MAX_TAB_TITLE_LENGTH) : title;
    }

    private void insertTextInSelectedQueryTab(String text) {
        QueryTabController selectedQueryTabController = getSelectedQueryTabController();
        if (selectedQueryTabController != null) {
            selectedQueryTabController.insertText(text);
        }
    }

    /**
     * Desde el arbol: el SQL en una pestaña nueva, para no pisar lo que se esta escribiendo.
     */
    private void executeInNewQueryTab(String sql) {
        addQueryTab().setSqlAndExecute(sql);
    }

    private QueryTabController getSelectedQueryTabController() {
        Tab selectedTab = queryTabPane.getSelectionModel().getSelectedItem();
        return selectedTab != null && selectedTab.getUserData() instanceof QueryTabController queryTabController ? queryTabController : null;
    }

    private List<QueryTabController> getQueryTabControllers() {
        List<QueryTabController> queryTabControllers = new ArrayList<>();
        for (Tab queryTab : queryTabPane.getTabs()) {
            if (queryTab.getUserData() instanceof QueryTabController queryTabController) {
                queryTabControllers.add(queryTabController);
            }
        }
        return queryTabControllers;
    }

    private void closeSessionsInBackground(String sessionKeyPrefix) {
        Thread closeSessionsThread = new Thread(
            () -> TargetSessionFactory.getInstance().closeSessions(sessionKeyPrefix),
            "close-sessions"
        );
        closeSessionsThread.setDaemon(true);
        closeSessionsThread.start();
    }

    /**
     * En el desplegable de la BD activa, las de sistema en gris (como en el arbol).
     */
    private final class DatabaseNameCell extends ListCell<String> {

        @Override
        protected void updateItem(String databaseName, boolean isEmpty) {
            super.updateItem(databaseName, isEmpty);
            getStyleClass().remove(STYLE_SYSTEM_DATABASE_ITEM);
            setText(isEmpty || databaseName == null ? null : databaseName);
            if (!isEmpty && systemDatabaseDetector.isSystemDatabase(databaseName)) getStyleClass().add(STYLE_SYSTEM_DATABASE_ITEM);
        }
    }
}
