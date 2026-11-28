package modules.workspace.infrastructure.controllers;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javafx.animation.Animation;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.stage.WindowEvent;
import javafx.util.Duration;

import modules.shared.domain.enums.LanguageEnum;
import modules.shared.application.Primitives;
import modules.shared.infrastructure.controllers.ConnectionTreeHandler;
import modules.shared.infrastructure.components.appversion.AppVersionComponent;
import modules.shared.infrastructure.components.icons.FlagIcon;
import modules.shared.infrastructure.components.theme.ThemeComponent;
import modules.shared.infrastructure.controllers.AbstractFxController;
import modules.shared.infrastructure.controllers.ConnectionViewController;
import static modules.shared.application.Primitives.getBool;
import static modules.shared.application.Primitives.getTrimmedString;

import core.boot.ErrorLogComponent;
import core.boot.DesktopApplication;
import core.config.Translator;
import core.config.TranslatorResourceBundle;
import static core.config.Global.__;

import modules.settings.domain.enums.SettingsKeyEnum;
import modules.settings.application.savesetting.SaveSettingDto;
import modules.settings.application.savesetting.SaveSettingService;

import modules.connections.domain.enums.ConnectionsKeyEnum;
import modules.connections.domain.enums.DbEngineEnum;
import modules.connections.infrastructure.factories.MongoSessionFactory;
import modules.connections.infrastructure.factories.RedisSessionFactory;
import modules.connections.infrastructure.factories.TargetSessionFactory;
import modules.connections.infrastructure.controllers.ConnectionsPanelController;

import modules.workspace.domain.enums.WorkspaceKeyEnum;
import modules.workspace.application.getworkspacestate.GetWorkspaceStateDto;
import modules.workspace.application.resetworkspace.ResetWorkspaceDto;
import modules.workspace.application.saveworkspacestate.SaveWorkspaceStateDto;
import modules.workspace.application.getworkspacestate.GetWorkspaceStateService;
import modules.workspace.application.resetworkspace.ResetWorkspaceService;
import modules.workspace.application.saveworkspacestate.SaveWorkspaceStateService;

/**
 * La ventana: el panel de conexiones y una pestaña por conexion abierta, con el color de la conexion en la cabecera
 * (feature 2: produccion en rojo, preproduccion en amarillo...) y la marca PROD.
 */
public final class MainWindowController extends AbstractFxController {

    private static final String PATH_CONNECTION_TAB = "/modules/workspace/infrastructure/views/connection-tab.fxml";
    private static final String PATH_REDIS_TAB = "/modules/redis/infrastructure/views/redis-tab.fxml";
    private static final String PATH_MONGO_TAB = "/modules/mongo/infrastructure/views/mongo-tab.fxml";
    private static final double LIGHT_COLOR_LUMINANCE = 0.6;
    private static final String PATH_MAIN_WINDOW = "/modules/workspace/infrastructure/views/main-window.fxml";
    // los filtros se guardan un poco despues de dejar de escribir
    private static final double SAVE_FILTERS_DELAY_MS = 600;
    // el ajuste automatico del panel de conexiones no pasa de media ventana
    private static final double MAX_PANEL_FRACTION = 0.5;

    @FXML
    private SplitPane mainSplitPane;
    @FXML
    private ConnectionsPanelController connectionsPanelController;
    @FXML
    private TabPane connectionTabPane;
    @FXML
    private Label noConnectionLabel;
    @FXML
    private CheckMenuItem darkThemeMenuItem;
    @FXML
    private MenuButton languageMenuButton;
    @FXML
    private Label versionLabel;

    private final Map<String, Tab> connectionTabsByUuid = new HashMap<>();
    private final Map<String, Runnable> connectionReleasersByUuid = new HashMap<>();
    private final Map<String, ConnectionViewController> connectionControllersByUuid = new HashMap<>();
    private final PauseTransition saveFiltersDelay = new PauseTransition(Duration.millis(SAVE_FILTERS_DELAY_MS));
    private String pendingDatabaseFilter = "";
    private String pendingObjectFilter = "";
    // el panel de conexiones se ajusta solo al texto mas largo del arbol hasta que el usuario mueve el divisor a mano
    private boolean isDividerMovedByUser;

    @FXML
    private void initialize() {
        connectionsPanelController.setOnOpenConnection(this::openConnectionTab);
        connectionsPanelController.setOnDisconnectConnection(this::disconnect);
        restoreFilters();
        saveFiltersDelay.setOnFinished(event -> saveFilters());
        connectionsPanelController.setOnFiltersChanged((databaseFilter, objectFilter) -> {
            pendingDatabaseFilter = databaseFilter;
            pendingObjectFilter = objectFilter;
            saveFiltersDelay.playFromStart();
        });
        // al cerrar la ventana con su X: se guarda lo de cada conexion abierta (Salir del menu lo hace en onExit)
        connectionTabPane.sceneProperty().addListener((sceneObservable, oldScene, newScene) -> {
            if (newScene == null) return;
            // al cambiar de idioma la escena es la misma (ya tiene ventana): se engancha directamente
            if (newScene.getWindow() != null) newScene.getWindow().addEventHandler(WindowEvent.WINDOW_CLOSE_REQUEST, event -> saveWorkspace());
            newScene.windowProperty().addListener((windowObservable, oldWindow, newWindow) -> {
                if (newWindow != null) newWindow.addEventHandler(WindowEvent.WINDOW_CLOSE_REQUEST, event -> saveWorkspace());
            });
        });
        connectionsPanelController.setOnConnectionFocused(connectionUuid -> {
            Tab connectionTab = connectionTabsByUuid.get(connectionUuid);
            if (connectionTab != null) connectionTabPane.getSelectionModel().select(connectionTab);
        });
        installPanelAutoWidth();
        noConnectionLabel.visibleProperty().bind(Bindings.isEmpty(connectionTabPane.getTabs()));
        noConnectionLabel.setMouseTransparent(true);
        loadVersionLabel();
        darkThemeMenuItem.setSelected(ThemeComponent.getInstance().isDark());
        loadLanguageMenu();
    }

    /**
     * El panel de conexiones mide lo justo para leer la fila mas larga del arbol (al arrancar y cada vez que se abre,
     * pliega o filtra algo), hasta que el usuario arrastra el divisor: desde ahi manda su ancho. Al cambiar el tamaño
     * de la ventana el panel conserva su ancho en pixeles y crece o encoge el lado derecho.
     */
    private void installPanelAutoWidth() {
        SplitPane.setResizableWithParent(mainSplitPane.getItems().getFirst(), false);
        connectionsPanelController.setOnTreeContentWidthChanged(this::fitPanelWidth);
        mainSplitPane.widthProperty().addListener((observable, oldWidth, newWidth) -> {
            if (oldWidth.doubleValue() > 0 || newWidth.doubleValue() <= 0) return;
            Platform.runLater(() -> fitPanelWidth(connectionsPanelController.getTreeContentWidth()));
        });
        mainSplitPane.addEventFilter(MouseEvent.MOUSE_PRESSED, mouseEvent -> {
            if (isOnDivider(mouseEvent.getTarget())) isDividerMovedByUser = true;
        });
    }

    private void fitPanelWidth(double panelWidth) {
        double splitPaneWidth = mainSplitPane.getWidth();
        if (isDividerMovedByUser || panelWidth <= 0 || splitPaneWidth <= 0) return;
        mainSplitPane.setDividerPositions(Math.min(panelWidth / splitPaneWidth, MAX_PANEL_FRACTION));
    }

    private boolean isOnDivider(Object eventTarget) {
        Node node = eventTarget instanceof Node targetNode ? targetNode : null;
        while (node != null && node != mainSplitPane) {
            if (node.getStyleClass().contains("split-pane-divider")) return true;
            node = node.getParent();
        }
        return false;
    }

    /**
     * La version en el pie: asi se sabe que hay desplegado. Si no se puede leer, el pie se queda vacio y queda en
     * errors.log; la app sigue.
     */
    private void loadVersionLabel() {
        try {
            versionLabel.setText(DesktopApplication.APP_TITLE + " " + AppVersionComponent.getInstance().getVersion());
        }
        catch (IOException e) {
            ErrorLogComponent.getInstance().log(Thread.currentThread().getName(), e);
        }
    }

    /**
     * Selector de idioma arriba a la derecha: la bandera del actual y, al abrirlo, los cuatro con su nombre en su lengua.
     */
    private void loadLanguageMenu() {
        String currentLanguage = Translator.getInstance().getLanguage();
        languageMenuButton.setGraphic(FlagIcon.of(currentLanguage));
        languageMenuButton.setText(LanguageEnum.NATIVE_NAMES.get(currentLanguage));
        for (String language : LanguageEnum.VALUES) {
            MenuItem languageMenuItem = new MenuItem(LanguageEnum.NATIVE_NAMES.get(language), FlagIcon.of(language));
            languageMenuItem.setOnAction(actionEvent -> changeLanguage(language));
            languageMenuButton.getItems().add(languageMenuItem);
        }
    }

    /**
     * Guarda el idioma y vuelve a montar la ventana en el nuevo (las pestañas abiertas se cierran: se avisa antes).
     */
    private void changeLanguage(String language) {
        if (language.equals(Translator.getInstance().getLanguage())) return;
        if (!connectionTabPane.getTabs().isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION, __("Cambiar el idioma cierra las conexiones abiertas. ¿Seguir?"), ButtonType.OK, ButtonType.CANCEL);
            alert.initOwner(languageMenuButton.getScene().getWindow());
            ThemeComponent.getInstance().applyTo(alert);
            if (alert.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
        }
        try {
            SaveSettingService.getInstance().invoke(SaveSettingDto.fromPrimitives(Primitives.of(
                "settingKey", SettingsKeyEnum.LANGUAGE,
                "settingValue", language
            )));
        }
        catch (RuntimeException e) {
            showError(e);
            return;
        }
        Translator.getInstance().setLanguage(language);
        closeAllConnectionTabs();
        Parent mainWindowRoot;
        try {
            mainWindowRoot = new FXMLLoader(MainWindowController.class.getResource(PATH_MAIN_WINDOW), TranslatorResourceBundle.getInstance()).load();
        }
        catch (IOException | RuntimeException e) {
            showError(e);
            return;
        }
        languageMenuButton.getScene().setRoot(mainWindowRoot);
    }

    /**
     * Cambiar de idioma y restablecer el espacio de trabajo: cierra cada pestaña como su X (guarda sus pestañas de
     * query) y luego todas las sesiones. Un fallo al cerrar una no corta el resto (queda en errors.log). Las sesiones se
     * cierran en segundo plano: con un servidor remoto o un tunel SSH, cerrar puede tardar y congelaria la ventana.
     */
    private void closeAllConnectionTabs() {
        for (Tab connectionTab : java.util.List.copyOf(connectionTabPane.getTabs())) {
            connectionTabPane.getTabs().remove(connectionTab);
            try {
                if (connectionTab.getOnClosed() != null) connectionTab.getOnClosed().handle(null);
            }
            catch (RuntimeException e) {
                ErrorLogComponent.getInstance().log(Thread.currentThread().getName(), e);
            }
        }
        Thread closeSessionsThread = new Thread(() -> {
            TargetSessionFactory.getInstance().closeAll();
            RedisSessionFactory.getInstance().closeAll();
            MongoSessionFactory.getInstance().closeAll();
        }, "close-all-sessions");
        closeSessionsThread.setDaemon(true);
        closeSessionsThread.start();
    }

    /**
     * Feature 37: cambia el tema de todas las ventanas abiertas y lo guarda para el siguiente arranque.
     */
    @FXML
    private void onToggleDarkTheme() {
        boolean isDark = darkThemeMenuItem.isSelected();
        try {
            SaveSettingService.getInstance().invoke(SaveSettingDto.fromPrimitives(Primitives.of(
                "settingKey", SettingsKeyEnum.THEME,
                "settingValue", isDark ? SettingsKeyEnum.THEME_DARK : SettingsKeyEnum.THEME_LIGHT
            )));
        }
        catch (RuntimeException e) {
            showError(e);
        }
        ThemeComponent themeComponent = ThemeComponent.getInstance();
        themeComponent.setDark(isDark);
        themeComponent.applyToAllWindows();
    }

    /**
     * Los filtros del panel tal como se dejaron en el ultimo arranque.
     */
    private void restoreFilters() {
        try {
            connectionsPanelController.setFilters(getSavedState(WorkspaceKeyEnum.DATABASE_FILTER), getSavedState(WorkspaceKeyEnum.OBJECT_FILTER));
        }
        catch (RuntimeException e) {
            System.err.println("multidbs: no se pudieron leer los filtros guardados: " + getErrorMessage(e));
        }
    }

    private String getSavedState(String stateKey) {
        return GetWorkspaceStateService.getInstance().invoke(GetWorkspaceStateDto.fromPrimitives(Primitives.of(
            "stateKey", stateKey
        ))).getStateValue();
    }

    private void saveFilters() {
        saveFiltersDelay.stop();
        try {
            saveState(WorkspaceKeyEnum.DATABASE_FILTER, pendingDatabaseFilter);
            saveState(WorkspaceKeyEnum.OBJECT_FILTER, pendingObjectFilter);
        }
        catch (RuntimeException e) {
            // se guarda solo, al escribir: un fallo no debe sacar un dialogo
            System.err.println("multidbs: no se pudieron guardar los filtros: " + getErrorMessage(e));
        }
    }

    private void saveState(String stateKey, String stateValue) {
        SaveWorkspaceStateService.getInstance().invoke(SaveWorkspaceStateDto.fromPrimitives(Primitives.of(
            "stateKey", stateKey,
            "stateValue", stateValue
        )));
    }

    /**
     * Al salir de la app: los filtros que esten por guardar y las pestañas de cada conexion abierta.
     */
    private void saveWorkspace() {
        if (saveFiltersDelay.getStatus() == Animation.Status.RUNNING) saveFilters();
        for (ConnectionViewController connectionViewController : connectionControllersByUuid.values()) {
            connectionViewController.saveState();
        }
    }

    /**
     * "Desconectar" del panel: quita la pestaña de la conexion y cierra sus sesiones y su arbol, como su X.
     */
    private void disconnect(String connectionUuid) {
        Tab connectionTab = connectionTabsByUuid.get(connectionUuid);
        Runnable releaseConnection = connectionReleasersByUuid.get(connectionUuid);
        if (connectionTab == null || releaseConnection == null) return;
        connectionTabPane.getTabs().remove(connectionTab);
        releaseConnection.run();
    }

    /**
     * Archivo > Restablecer espacio de trabajo: como si se entrara por primera vez en lo que recuerda la ventana
     * (filtros, BD activa, carpetas, ultimo fichero y pestañas de query). Cierra antes las conexiones abiertas, porque
     * al cerrarse guardan sus pestañas; las conexiones guardadas, el tema, el idioma y el historial no se tocan.
     */
    @FXML
    private void onResetWorkspace() {
        String message = __("Se borran los filtros, la BD activa de cada conexión, las carpetas y el último fichero recordados y las pestañas de query guardadas. Las conexiones, el tema, el idioma y el historial se quedan.");
        if (!connectionTabPane.getTabs().isEmpty()) message += "\n\n" + __("Se cierran las conexiones abiertas.");
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.OK, ButtonType.CANCEL);
        alert.setHeaderText(__("¿Restablecer el espacio de trabajo?"));
        alert.initOwner(connectionTabPane.getScene().getWindow());
        ThemeComponent.getInstance().applyTo(alert);
        if (alert.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;

        closeAllConnectionTabs();
        try {
            ResetWorkspaceService.getInstance().invoke(ResetWorkspaceDto.fromPrimitives(Primitives.of()));
        }
        catch (RuntimeException e) {
            showError(e);
            return;
        }
        // filtros vacios sin volver a guardarlos: quedan como el primer dia (sin fila)
        connectionsPanelController.setFilters("", "");
        saveFiltersDelay.stop();
        pendingDatabaseFilter = "";
        pendingObjectFilter = "";
    }

    @FXML
    private void onExit() {
        saveWorkspace();
        Platform.exit();
    }

    /**
     * Cada motor trae su vista: los SQL, editor (y su arbol bajo la conexion, en el panel); Redis y MongoDB, su
     * navegador + consola. Una pestaña por conexion: si ya esta abierta, se pone delante.
     */
    private void openConnectionTab(Map<String, Object> connection) {
        String connectionUuid = getTrimmedString(connection.get(ConnectionsKeyEnum.CONNECTION_UUID));
        Tab openTab = connectionTabsByUuid.get(connectionUuid);
        if (openTab != null) {
            connectionTabPane.getSelectionModel().select(openTab);
            return;
        }
        String dbEngine = getTrimmedString(connection.get(ConnectionsKeyEnum.DB_ENGINE));
        String viewPath = DbEngineEnum.REDIS.equals(dbEngine) ? PATH_REDIS_TAB : DbEngineEnum.MONGODB.equals(dbEngine) ? PATH_MONGO_TAB : PATH_CONNECTION_TAB;
        FXMLLoader fxmlLoader = new FXMLLoader(MainWindowController.class.getResource(viewPath), TranslatorResourceBundle.getInstance());
        Parent connectionTabRoot;
        try {
            connectionTabRoot = fxmlLoader.load();
        }
        catch (IOException e) {
            showAndLogError(e);
            return;
        }

        ConnectionViewController connectionTabController = fxmlLoader.getController();
        // el dialogo de copia solo ofrece como destino las conexiones abiertas
        if (connectionTabController instanceof ConnectionTabController sqlConnectionTabController) {
            sqlConnectionTabController.setOpenConnectionUuidsSupplier(() -> Set.copyOf(connectionTabsByUuid.keySet()));
        }
        try {
            connectionTabController.setup(connection);
        }
        catch (RuntimeException e) {
            showError(e);
            return;
        }

        Tab connectionTab = new Tab();
        connectionTab.setContent(connectionTabRoot);
        setConnectionTabHeader(connectionTab, connection);
        // lo mismo al cerrar la pestaña con su X que al "Desconectar" del panel (quitar la pestaña a mano no lanza onClosed)
        Runnable releaseConnection = () -> {
            connectionTabsByUuid.remove(connectionUuid);
            connectionReleasersByUuid.remove(connectionUuid);
            connectionControllersByUuid.remove(connectionUuid);
            connectionTabController.close();
            connectionsPanelController.detachTree(connectionUuid);
        };
        connectionTab.setOnClosed(event -> releaseConnection.run());
        connectionReleasersByUuid.put(connectionUuid, releaseConnection);
        connectionControllersByUuid.put(connectionUuid, connectionTabController);
        connectionTabsByUuid.put(connectionUuid, connectionTab);
        connectionTabPane.getTabs().add(connectionTab);
        connectionTabPane.getSelectionModel().select(connectionTab);
        connectionsPanelController.markConnectionOpen(connectionUuid);
        ConnectionTreeHandler connectionTreeHandler = connectionTabController.getConnectionTreeHandler();
        if (connectionTreeHandler != null) connectionsPanelController.attachTree(connectionUuid, connectionTreeHandler);
    }

    /**
     * Cabecera: el nombre (con el texto en blanco o negro segun lo claro que sea el color), PROD si es de produccion y
     * user@host en el tooltip.
     */
    private void setConnectionTabHeader(Tab connectionTab, Map<String, Object> connection) {
        String connectionName = getTrimmedString(connection.get(ConnectionsKeyEnum.CONNECTION_NAME));
        Label nameLabel = new Label(connectionName);
        nameLabel.setMnemonicParsing(false);
        HBox header = new HBox(6, nameLabel);
        header.setAlignment(Pos.CENTER_LEFT);

        String colorHex = getTrimmedString(connection.get(ConnectionsKeyEnum.COLOR_HEX));
        if (!colorHex.isEmpty()) {
            Color color = Color.web(colorHex);
            connectionTab.setStyle("-fx-background-color: " + colorHex + ";");
            nameLabel.setTextFill(getBrightness(color) > LIGHT_COLOR_LUMINANCE ? Color.BLACK : Color.WHITE);
        }
        if (getBool(connection.get(ConnectionsKeyEnum.IS_PRODUCTION))) {
            Label productionLabel = new Label(__("PROD"));
            productionLabel.getStyleClass().add("production-badge");
            header.getChildren().add(productionLabel);
        }

        connectionTab.setGraphic(header);
        connectionTab.setTooltip(new Tooltip(
            getTrimmedString(connection.get(ConnectionsKeyEnum.USER_NAME)) + "@"
                + getTrimmedString(connection.get(ConnectionsKeyEnum.HOST)) + ":"
                + getTrimmedString(connection.get(ConnectionsKeyEnum.PORT))
        ));
    }

    private double getBrightness(Color color) {
        return 0.299 * color.getRed() + 0.587 * color.getGreen() + 0.114 * color.getBlue();
    }
}
