package modules.connections.infrastructure.controllers;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;

import javafx.animation.PauseTransition;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;

import modules.shared.application.Primitives;
import modules.shared.infrastructure.components.icons.AppIconEnum;
import modules.shared.infrastructure.controllers.ConnectionTreeHandler;
import modules.shared.infrastructure.components.icons.AppIcon;
import modules.shared.infrastructure.components.theme.ThemeComponent;
import modules.shared.infrastructure.controllers.AbstractFxController;
import static modules.shared.application.Primitives.getBool;
import static modules.shared.application.Primitives.getTrimmedString;

import core.config.TranslatorResourceBundle;
import static core.config.Global.__;

import modules.connections.domain.enums.ConnectionsConfigEnum;
import modules.connections.domain.enums.ConnectionsKeyEnum;
import modules.connections.domain.enums.DbEngineEnum;
import modules.connections.application.cloneconnection.CloneConnectionDto;
import modules.connections.application.deleteconnection.DeleteConnectionDto;
import modules.connections.application.renameconnection.RenameConnectionDto;
import modules.connections.application.cloneconnection.CloneConnectionService;
import modules.connections.application.deleteconnection.DeleteConnectionService;
import modules.connections.application.listconnections.ListConnectionsService;
import modules.connections.application.renameconnection.RenameConnectionService;
import modules.connections.infrastructure.components.EngineIconComponent;

/**
 * El panel de conexiones guardadas, en arbol: cada conexion con su color y, si esta abierta, su arbol de objetos
 * colgando debajo (BD, tablas, columnas), como el Object Browser de SQLyog. Abrir (doble clic o Enter) / nueva /
 * editar / clonar / borrar / refrescar, y los filtros de BD y de objetos, que valen para todas las conexiones abiertas.
 */
public final class ConnectionsPanelController extends AbstractFxController {

    private static final String PATH_CONNECTION_DIALOG = "/modules/connections/infrastructure/views/connection-dialog.fxml";

    @FXML
    private TreeView<Object> connectionsTreeView;
    @FXML
    private TextField databaseFilterField;
    @FXML
    private TextField objectFilterField;
    @FXML
    private Button clearFiltersButton;
    @FXML
    private Label treeStatusLabel;
    @FXML
    private Button editButton;
    @FXML
    private Button cloneButton;
    @FXML
    private Button deleteButton;
    @FXML
    private Button disconnectButton;
    @FXML
    private Button refreshButton;
    @FXML
    private Button createDatabaseButton;

    private final TreeItem<Object> rootItem = new TreeItem<>();
    private final Map<String, TreeItem<Object>> connectionItemsByUuid = new LinkedHashMap<>();
    private final Map<String, ConnectionTreeHandler> treeHandlersByUuid = new HashMap<>();
    private final Set<String> openConnectionUuids = new HashSet<>();
    private final Map<String, String> treeStatusesByUuid = new HashMap<>();
    private Consumer<Map<String, Object>> onOpenConnection = connection -> { };
    private Consumer<String> onDisconnectConnection = connectionUuid -> { };
    private BiConsumer<String, String> onFiltersChanged = (databaseFilter, objectFilter) -> { };
    private Consumer<String> onConnectionFocused = connectionUuid -> { };
    private DoubleConsumer onTreeContentWidthChanged = contentWidth -> { };
    private final PauseTransition treeContentWidthDelay = new PauseTransition(Duration.millis(ConnectionsConfigEnum.TREE_WIDTH_DELAY_MS));

    @FXML
    private void initialize() {
        connectionsTreeView.setRoot(rootItem);
        connectionsTreeView.setCellFactory(treeView -> new ConnectionTreeCell());
        // abrir, plegar, cargar o filtrar cambia las filas que se ven: el panel se ajusta al texto mas largo, una vez
        // cuando deja de cambiar (al cargar una BD llegan muchas filas seguidas)
        treeContentWidthDelay.setOnFinished(event -> onTreeContentWidthChanged.accept(getTreeContentWidth()));
        rootItem.addEventHandler(TreeItem.treeNotificationEvent(), event -> treeContentWidthDelay.playFromStart());
        connectionsTreeView.getSelectionModel().selectedItemProperty().addListener(
            (observable, oldItem, newItem) -> onSelectionChanged(newItem)
        );
        connectionsTreeView.setOnMouseClicked(mouseEvent -> {
            if (mouseEvent.getButton() != MouseButton.PRIMARY || mouseEvent.getClickCount() != 2) return;
            TreeItem<Object> selectedItem = getSelectedItem();
            if (isConnectionItem(selectedItem)) {
                onOpenConnection();
                return;
            }
            ConnectionTreeHandler treeHandler = getTreeHandler(selectedItem);
            if (treeHandler != null) treeHandler.onActivated(selectedItem);
        });
        connectionsTreeView.setOnKeyPressed(keyEvent -> {
            TreeItem<Object> selectedItem = getSelectedItem();
            if (keyEvent.getCode() == KeyCode.ENTER && isConnectionItem(selectedItem)) {
                onOpenConnection();
                keyEvent.consume();
                return;
            }
            ConnectionTreeHandler treeHandler = getTreeHandler(selectedItem);
            if (treeHandler == null) return;
            if (isConnectionItem(selectedItem)) {
                if (keyEvent.getCode() == KeyCode.F5) {
                    treeHandler.refresh(selectedItem);
                    keyEvent.consume();
                }
                return;
            }
            treeHandler.onKeyPressed(selectedItem, keyEvent);
        });
        databaseFilterField.textProperty().addListener((observable, oldFilter, newFilter) -> applyFilters());
        objectFilterField.textProperty().addListener((observable, oldFilter, newFilter) -> applyFilters());
        clearFiltersButton.setDisable(true);
        onSelectionChanged(null);
        loadConnections(null);
    }

    /**
     * Lo fija la ventana principal: que hacer al abrir una conexion (su pestaña; si ya esta abierta, ponerla delante).
     */
    public void setOnOpenConnection(Consumer<Map<String, Object>> onOpenConnection) {
        this.onOpenConnection = onOpenConnection;
    }

    /**
     * Lo fija la ventana principal: al elegir un nodo de una conexion abierta, su pestaña pasa delante.
     */
    public void setOnConnectionFocused(Consumer<String> onConnectionFocused) {
        this.onConnectionFocused = onConnectionFocused;
    }

    /**
     * Se llama cuando cambian las filas desplegadas del arbol, con el ancho que necesita el panel para que se lea la
     * fila mas larga (ver getTreeContentWidth).
     */
    public void setOnTreeContentWidthChanged(DoubleConsumer onTreeContentWidthChanged) {
        this.onTreeContentWidthChanged = onTreeContentWidthChanged;
    }

    /**
     * El ancho del panel para ver entera la fila desplegada mas larga, tambien las que no caben en pantalla: cada fila
     * se mide con una celda igual a las del arbol (sangria, flecha, icono y texto) fuera de la vista, como hace el
     * propio TreeView para el ancho de su contenido. Mas el margen de la barra de desplazamiento y los bordes.
     */
    public double getTreeContentWidth() {
        if (connectionsTreeView.getScene() == null) return 0;
        TreeCell<Object> measuringCell = new ConnectionTreeCell();
        StackPane disclosureArrow = new StackPane();
        disclosureArrow.getStyleClass().add("arrow");
        StackPane disclosureNode = new StackPane(disclosureArrow);
        disclosureNode.getStyleClass().add("tree-disclosure-node");
        measuringCell.setDisclosureNode(disclosureNode);
        measuringCell.setManaged(false);
        measuringCell.setVisible(false);
        measuringCell.updateTreeView(connectionsTreeView);
        VBox panelRoot = (VBox) connectionsTreeView.getParent();
        panelRoot.getChildren().add(measuringCell);
        double maxRowWidth = 0;
        try {
            for (int rowIndex = 0; rowIndex < connectionsTreeView.getExpandedItemCount(); rowIndex++) {
                measuringCell.updateIndex(rowIndex);
                measuringCell.applyCss();
                maxRowWidth = Math.max(maxRowWidth, measuringCell.prefWidth(-1));
            }
        }
        finally {
            measuringCell.updateIndex(-1);
            panelRoot.getChildren().remove(measuringCell);
        }
        Insets treeInsets = connectionsTreeView.getInsets();
        Insets panelInsets = panelRoot.getInsets();
        return maxRowWidth + treeInsets.getLeft() + treeInsets.getRight() + panelInsets.getLeft() + panelInsets.getRight()
            + ConnectionsConfigEnum.TREE_SCROLLBAR_MARGIN;
    }

    /**
     * Lo fija la ventana principal: "Desconectar" cierra la pestaña de la conexion (y con ella sus sesiones).
     */
    public void setOnDisconnectConnection(Consumer<String> onDisconnectConnection) {
        this.onDisconnectConnection = onDisconnectConnection;
    }

    /**
     * Lo fija la ventana principal: guardar los filtros para el siguiente arranque.
     */
    public void setOnFiltersChanged(BiConsumer<String, String> onFiltersChanged) {
        this.onFiltersChanged = onFiltersChanged;
    }

    /**
     * Los filtros guardados, al arrancar: se aplican a los arboles como si se hubieran escrito.
     */
    public void setFilters(String databaseFilter, String objectFilter) {
        databaseFilterField.setText(databaseFilter);
        objectFilterField.setText(objectFilter);
    }

    @FXML
    private void onClearFilters() {
        setFilters("", "");
        databaseFilterField.requestFocus();
    }

    /**
     * La conexion tiene su pestaña abierta: "conectada" en el panel, se puede desconectar y no borrar. Vale para todos
     * los motores; Redis y MongoDB no cuelgan arbol, asi que no basta con attachTree.
     */
    public void markConnectionOpen(String connectionUuid) {
        openConnectionUuids.add(connectionUuid);
        TreeItem<Object> connectionItem = connectionItemsByUuid.get(connectionUuid);
        if (connectionItem != null) connectionsTreeView.getSelectionModel().select(connectionItem);
        connectionsTreeView.refresh();
        onSelectionChanged(getSelectedItem());
    }

    /**
     * Cuelga el arbol de objetos de una conexion abierta bajo su nodo.
     */
    public void attachTree(String connectionUuid, ConnectionTreeHandler treeHandler) {
        TreeItem<Object> connectionItem = connectionItemsByUuid.get(connectionUuid);
        if (connectionItem == null) return;
        treeHandlersByUuid.put(connectionUuid, treeHandler);
        treeHandler.setFilters(databaseFilterField.getText(), objectFilterField.getText());
        treeHandler.attach(connectionItem, connectionsTreeView::refresh, status -> setTreeStatus(connectionUuid, status));
        connectionsTreeView.getSelectionModel().select(connectionItem);
        connectionsTreeView.refresh();
        onSelectionChanged(getSelectedItem());
    }

    /**
     * Al cerrar la pestaña: la conexion se queda en el panel, plegada y sin hijos.
     */
    public void detachTree(String connectionUuid) {
        openConnectionUuids.remove(connectionUuid);
        treeHandlersByUuid.remove(connectionUuid);
        treeStatusesByUuid.remove(connectionUuid);
        TreeItem<Object> connectionItem = connectionItemsByUuid.get(connectionUuid);
        if (connectionItem != null) {
            connectionItem.getChildren().clear();
            connectionItem.setExpanded(false);
        }
        connectionsTreeView.refresh();
        onSelectionChanged(getSelectedItem());
    }

    @FXML
    private void onOpenConnection() {
        Map<String, Object> connection = getSelectedConnection();
        if (connection == null) return;
        onOpenConnection.accept(connection);
    }

    @FXML
    private void onNewConnection() {
        openConnectionDialog("");
    }

    @FXML
    private void onEditConnection() {
        Map<String, Object> connection = getSelectedConnection();
        if (connection == null) return;
        openConnectionDialog(getConnectionUuid(connection));
    }

    @FXML
    private void onCloneConnection() {
        Map<String, Object> connection = getSelectedConnection();
        if (connection == null) return;
        try {
            Map<String, Object> clonedConnection = CloneConnectionService.getInstance().invoke(
                CloneConnectionDto.fromPrimitives(Primitives.of("connectionUuid", getConnectionUuid(connection)))
            ).getConnection();
            loadConnections(getConnectionUuid(clonedConnection));
        }
        catch (RuntimeException e) {
            showError(e);
        }
    }

    /**
     * Solo con la fila de la conexion elegida y su pestaña cerrada: borrar desde un nodo de dentro seria un accidente.
     */
    @FXML
    private void onDeleteConnection() {
        TreeItem<Object> selectedItem = getSelectedItem();
        if (!isConnectionItem(selectedItem)) return;
        Map<String, Object> connection = getConnection(selectedItem);
        if (openConnectionUuids.contains(getConnectionUuid(connection))) return;

        String connectionName = getTrimmedString(connection.get(ConnectionsKeyEnum.CONNECTION_NAME));
        if (!isDeleteConfirmed(__("¿Borrar la conexión \"{0}\"?", connectionName))) return;
        try {
            DeleteConnectionService.getInstance().invoke(
                DeleteConnectionDto.fromPrimitives(Primitives.of("connectionUuid", getConnectionUuid(connection)))
            );
            loadConnections(null);
        }
        catch (RuntimeException e) {
            showError(e);
        }
    }

    /**
     * Cierra la conexion elegida: su pestaña, sus sesiones y su arbol; la conexion se queda en el panel "sin conexion".
     */
    @FXML
    private void onDisconnectConnection() {
        Map<String, Object> connection = getSelectedConnection();
        if (connection == null) return;
        String connectionUuid = getConnectionUuid(connection);
        if (!openConnectionUuids.contains(connectionUuid)) return;
        connectionsTreeView.getSelectionModel().select(connectionItemsByUuid.get(connectionUuid));
        onDisconnectConnection.accept(connectionUuid);
    }

    /**
     * "Crear BD" en la conexion elegida (abierta y de un motor que crea BDs): el dialogo es del arbol de la conexion.
     */
    @FXML
    private void onCreateDatabase() {
        TreeItem<Object> connectionItem = getConnectionItem(getSelectedItem());
        ConnectionTreeHandler treeHandler = getTreeHandler(connectionItem);
        if (treeHandler == null || !treeHandler.canCreateDatabase()) return;
        treeHandler.createDatabase(getTrimmedString(getConnection(connectionItem).get(ConnectionsKeyEnum.CONNECTION_NAME)));
    }

    /**
     * F5 o el boton: la BD del nodo elegido, o todas las de su conexion si esta elegida la conexion.
     */
    @FXML
    private void onRefresh() {
        TreeItem<Object> selectedItem = getSelectedItem();
        ConnectionTreeHandler treeHandler = getTreeHandler(selectedItem);
        if (treeHandler != null) treeHandler.refresh(selectedItem);
    }

    /**
     * Recarga las conexiones y deja seleccionada la indicada (la recien creada, editada o clonada). Los nodos de las
     * conexiones que siguen se reutilizan: asi una conexion abierta no pierde su arbol al editar otra.
     */
    private void loadConnections(String connectionUuidToSelect) {
        List<Map<String, Object>> connections;
        try {
            connections = ListConnectionsService.getInstance().invoke().getConnections();
        }
        catch (RuntimeException e) {
            showError(e);
            return;
        }
        Map<String, TreeItem<Object>> oldItemsByUuid = new HashMap<>(connectionItemsByUuid);
        connectionItemsByUuid.clear();
        List<TreeItem<Object>> connectionItems = new ArrayList<>();
        for (Map<String, Object> connection : connections) {
            String connectionUuid = getConnectionUuid(connection);
            TreeItem<Object> connectionItem = oldItemsByUuid.get(connectionUuid);
            if (connectionItem == null) connectionItem = new TreeItem<>();
            connectionItem.setValue(connection);
            connectionItemsByUuid.put(connectionUuid, connectionItem);
            connectionItems.add(connectionItem);
        }
        rootItem.getChildren().setAll(connectionItems);
        connectionsTreeView.refresh();
        if (connectionUuidToSelect == null) return;

        TreeItem<Object> itemToSelect = connectionItemsByUuid.get(connectionUuidToSelect);
        if (itemToSelect != null) connectionsTreeView.getSelectionModel().select(itemToSelect);
    }

    /**
     * connectionUuid vacio = conexion nueva.
     */
    private void openConnectionDialog(String connectionUuid) {
        FXMLLoader fxmlLoader = new FXMLLoader(ConnectionsPanelController.class.getResource(PATH_CONNECTION_DIALOG), TranslatorResourceBundle.getInstance());
        Parent dialogRoot;
        try {
            dialogRoot = fxmlLoader.load();
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        ConnectionDialogController connectionDialogController = fxmlLoader.getController();
        Stage dialogStage = new Stage();
        dialogStage.initOwner(connectionsTreeView.getScene().getWindow());
        dialogStage.initModality(Modality.WINDOW_MODAL);
        dialogStage.setTitle(connectionUuid.isEmpty() ? __("Nueva conexión") : __("Editar conexión"));
        Scene dialogScene = new Scene(dialogRoot);
        ThemeComponent.getInstance().applyTo(dialogScene);
        dialogStage.setScene(dialogScene);

        try {
            connectionDialogController.loadConnection(connectionUuid, this::loadConnections);
        }
        catch (RuntimeException e) {
            showError(e);
            return;
        }
        dialogStage.showAndWait();
    }

    private void onSelectionChanged(TreeItem<Object> selectedItem) {
        TreeItem<Object> connectionItem = getConnectionItem(selectedItem);
        boolean hasConnection = connectionItem != null;
        String connectionUuid = hasConnection ? getConnectionUuid(getConnection(connectionItem)) : "";
        boolean isOpen = openConnectionUuids.contains(connectionUuid);
        editButton.setDisable(!hasConnection);
        cloneButton.setDisable(!hasConnection);
        deleteButton.setDisable(!isConnectionItem(selectedItem) || isOpen);
        ConnectionTreeHandler treeHandler = treeHandlersByUuid.get(connectionUuid);
        refreshButton.setDisable(treeHandler == null);
        createDatabaseButton.setDisable(treeHandler == null || !treeHandler.canCreateDatabase());
        disconnectButton.setDisable(!isOpen);
        showTreeStatus(connectionUuid);
        if (!isOpen) return;

        onConnectionFocused.accept(connectionUuid);
        if (treeHandler != null && !isConnectionItem(selectedItem)) treeHandler.onSelected(selectedItem);
    }

    private void applyFilters() {
        clearFiltersButton.setDisable(databaseFilterField.getText().isEmpty() && objectFilterField.getText().isEmpty());
        onFiltersChanged.accept(databaseFilterField.getText(), objectFilterField.getText());
        for (ConnectionTreeHandler treeHandler : treeHandlersByUuid.values()) {
            treeHandler.setFilters(databaseFilterField.getText(), objectFilterField.getText());
        }
    }

    private void setTreeStatus(String connectionUuid, String status) {
        treeStatusesByUuid.put(connectionUuid, status);
        TreeItem<Object> connectionItem = getConnectionItem(getSelectedItem());
        if (connectionItem == null || connectionUuid.equals(getConnectionUuid(getConnection(connectionItem)))) {
            showTreeStatus(connectionUuid);
        }
    }

    /**
     * Una sola linea en la barra (los errores de Connector/J traen varias); el texto entero, en el tooltip.
     */
    private void showTreeStatus(String connectionUuid) {
        String status = treeStatusesByUuid.getOrDefault(connectionUuid, "");
        String firstLine = status.lines().findFirst().orElse("");
        treeStatusLabel.setText(firstLine);
        treeStatusLabel.setTooltip(firstLine.equals(status.trim()) ? null : new Tooltip(status));
    }

    private TreeItem<Object> getSelectedItem() {
        return connectionsTreeView.getSelectionModel().getSelectedItem();
    }

    /**
     * La conexion del nodo elegido: el propio nodo o, si es una BD o una tabla, la conexion de la que cuelga.
     */
    private Map<String, Object> getSelectedConnection() {
        TreeItem<Object> connectionItem = getConnectionItem(getSelectedItem());
        return connectionItem == null ? null : getConnection(connectionItem);
    }

    private TreeItem<Object> getConnectionItem(TreeItem<Object> treeItem) {
        TreeItem<Object> currentItem = treeItem;
        while (currentItem != null && currentItem != rootItem) {
            if (isConnectionItem(currentItem)) return currentItem;
            currentItem = currentItem.getParent();
        }
        return null;
    }

    private ConnectionTreeHandler getTreeHandler(TreeItem<Object> treeItem) {
        TreeItem<Object> connectionItem = getConnectionItem(treeItem);
        if (connectionItem == null) return null;
        return treeHandlersByUuid.get(getConnectionUuid(getConnection(connectionItem)));
    }

    private static boolean isConnectionItem(TreeItem<Object> treeItem) {
        return treeItem != null && treeItem.getValue() instanceof Map<?, ?>;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> getConnection(TreeItem<Object> connectionItem) {
        return (Map<String, Object>) connectionItem.getValue();
    }

    private static String getConnectionUuid(Map<String, Object> connection) {
        return getTrimmedString(connection.get(ConnectionsKeyEnum.CONNECTION_UUID));
    }

    /**
     * Clic derecho en una conexion: lo mismo que la barra (abrir, editar, clonar, borrar) mas renombrar. Cada opcion
     * elige antes la conexion del menu. Borrar pide confirmacion siempre y solo con la conexion cerrada.
     */
    private ContextMenu getConnectionContextMenu(TreeItem<Object> connectionItem, boolean isOpen) {
        MenuItem openMenuItem = getMenuItem(AppIconEnum.CONNECT, __("Conectar"), connectionItem, this::onOpenConnection);
        openMenuItem.setDisable(isOpen);
        MenuItem editMenuItem = getMenuItem(AppIconEnum.EDIT, __("Editar..."), connectionItem, this::onEditConnection);
        MenuItem cloneMenuItem = getMenuItem(AppIconEnum.COPY, __("Clonar"), connectionItem, this::onCloneConnection);
        MenuItem renameMenuItem = getMenuItem(AppIconEnum.TEXT, __("Renombrar..."), connectionItem, this::onRenameConnection);
        MenuItem disconnectMenuItem = getMenuItem(AppIconEnum.DISCONNECT, __("Desconectar"), connectionItem, this::onDisconnectConnection);
        disconnectMenuItem.setDisable(!isOpen);
        MenuItem deleteMenuItem = getMenuItem(AppIconEnum.DELETE, isOpen ? __("Eliminar (desconéctala antes)") : __("Eliminar"), connectionItem, this::onDeleteConnection);
        deleteMenuItem.setDisable(isOpen);
        MenuItem refreshMenuItem = getMenuItem(AppIconEnum.REFRESH, __("Refrescar (F5)"), connectionItem, this::onRefresh);
        ConnectionTreeHandler treeHandler = getTreeHandler(connectionItem);
        refreshMenuItem.setDisable(treeHandler == null);
        MenuItem createDatabaseMenuItem = getMenuItem(AppIconEnum.DATABASE_NEW, __("Crear BD..."), connectionItem, this::onCreateDatabase);
        createDatabaseMenuItem.setDisable(treeHandler == null || !treeHandler.canCreateDatabase());
        return new ContextMenu(
            openMenuItem, disconnectMenuItem, new SeparatorMenuItem(),
            editMenuItem, cloneMenuItem, renameMenuItem, deleteMenuItem, new SeparatorMenuItem(),
            createDatabaseMenuItem, refreshMenuItem
        );
    }

    private MenuItem getMenuItem(String iconName, String text, TreeItem<Object> connectionItem, Runnable action) {
        MenuItem menuItem = new MenuItem(text, AppIcon.of(iconName));
        menuItem.setMnemonicParsing(false);
        menuItem.setOnAction(actionEvent -> {
            connectionsTreeView.getSelectionModel().select(connectionItem);
            action.run();
        });
        return menuItem;
    }

    /**
     * Pide el nombre nuevo (con el actual escrito) y lo guarda; la conexion sigue elegida.
     */
    private void onRenameConnection() {
        Map<String, Object> connection = getSelectedConnection();
        if (connection == null) return;
        String currentName = getTrimmedString(connection.get(ConnectionsKeyEnum.CONNECTION_NAME));
        TextInputDialog renameDialog = new TextInputDialog(currentName);
        ThemeComponent.getInstance().applyTo(renameDialog);
        renameDialog.setTitle(__("Renombrar la conexión"));
        renameDialog.setHeaderText(null);
        renameDialog.setContentText(__("Nombre"));
        renameDialog.initOwner(connectionsTreeView.getScene().getWindow());
        String newName = renameDialog.showAndWait().map(String::trim).orElse("");
        if (newName.isEmpty() || newName.equals(currentName)) return;
        try {
            RenameConnectionService.getInstance().invoke(RenameConnectionDto.fromPrimitives(Primitives.of(
                "connectionUuid", getConnectionUuid(connection), "connectionName", newName
            )));
            loadConnections(getConnectionUuid(connection));
        }
        catch (RuntimeException e) {
            showError(e);
        }
    }

    /**
     * Una conexion: enchufe con su color, nombre, motor si no es MySQL, PROD y user@host en el tooltip. Lo de
     * debajo (BD, tablas...) lo pinta el arbol de esa conexion.
     */
    private final class ConnectionTreeCell extends TreeCell<Object> {

        // arrastrar una BD, tabla, vista o columna al editor suelta su nombre, como el doble clic
        private ConnectionTreeCell() {
            setOnDragDetected(mouseEvent -> {
                if (isEmpty() || getTreeItem() == null) return;
                ConnectionTreeHandler treeHandler = getTreeHandler(getTreeItem());
                String dragText = treeHandler == null ? "" : treeHandler.getDragText(getTreeItem());
                if (dragText.isEmpty()) return;
                Dragboard dragboard = startDragAndDrop(TransferMode.COPY);
                ClipboardContent clipboardContent = new ClipboardContent();
                clipboardContent.putString(dragText);
                dragboard.setContent(clipboardContent);
                mouseEvent.consume();
            });
        }

        @Override
        protected void updateItem(Object value, boolean isEmpty) {
            super.updateItem(value, isEmpty);
            getStyleClass().removeIf(styleClass -> styleClass.startsWith("explorer-"));
            setContextMenu(null);
            setTooltip(null);
            if (isEmpty || value == null) {
                setText(null);
                setGraphic(null);
                return;
            }
            if (!(value instanceof Map<?, ?>)) {
                ConnectionTreeHandler treeHandler = getTreeHandler(getTreeItem());
                setText(null);
                setGraphic(null);
                if (treeHandler != null) treeHandler.updateCell(this, getTreeItem());
                return;
            }
            Map<String, Object> connection = getConnection(getTreeItem());
            // un enchufe del color de su entorno (prod rojo, pre naranja...), como el "Connect" de SQLyog
            AppIcon plugIcon = AppIcon.ofPlug(getTrimmedString(connection.get(ConnectionsKeyEnum.COLOR_HEX)));

            Label nameLabel = new Label(getTrimmedString(connection.get(ConnectionsKeyEnum.CONNECTION_NAME)));
            nameLabel.setMnemonicParsing(false);
            boolean isOpen = openConnectionUuids.contains(getConnectionUuid(connection));
            nameLabel.getStyleClass().add(isOpen ? "connection-open" : "connection-closed");
            // sin conexion: el enchufe apagado y el estado escrito; conectada: el enchufe en color y el nombre en negrita
            if (!isOpen) plugIcon.setOpacity(0.35);
            setContextMenu(getConnectionContextMenu(getTreeItem(), isOpen));
            // conexiones de motores distintos con nombres parecidos: la placa del motor delante del nombre lo aclara
            String dbEngine = getTrimmedString(connection.get(ConnectionsKeyEnum.DB_ENGINE));
            if (dbEngine.isEmpty()) dbEngine = DbEngineEnum.DEFAULT;
            HBox row = new HBox(6, plugIcon, EngineIconComponent.getInstance().getIcon(dbEngine), nameLabel);
            row.setAlignment(Pos.CENTER_LEFT);
            if (getBool(connection.get(ConnectionsKeyEnum.IS_PRODUCTION))) {
                Label productionLabel = new Label("PROD");
                productionLabel.getStyleClass().add("production-badge");
                row.getChildren().add(productionLabel);
            }

            setTooltip(new Tooltip(
                __(DbEngineEnum.LABELS.getOrDefault(dbEngine, dbEngine)) + "\n"
                    + getTrimmedString(connection.get(ConnectionsKeyEnum.USER_NAME)) + "@"
                    + getTrimmedString(connection.get(ConnectionsKeyEnum.HOST)) + ":"
                    + getTrimmedString(connection.get(ConnectionsKeyEnum.PORT))
                    + "\n" + (isOpen ? __("Conectada") : __("Sin conexión"))
            ));
            if (!isOpen) {
                Label stateLabel = new Label(__("sin conexión"));
                stateLabel.getStyleClass().add("connection-state");
                row.getChildren().add(stateLabel);
            }
            setText(null);
            setGraphic(row);
        }
    }
}
