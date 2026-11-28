package modules.explorer.infrastructure.controllers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

import javafx.concurrent.Task;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.IndexedCell;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.control.skin.VirtualFlow;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

import modules.shared.domain.enums.ExceptionEnum;
import modules.shared.domain.services.SqlDialect;
import modules.shared.domain.services.SqlIdentifierQuoter;
import modules.shared.domain.services.SystemDatabaseDetector;
import modules.shared.application.Primitives;
import modules.shared.infrastructure.components.icons.AppIconEnum;
import modules.shared.infrastructure.controllers.ConnectionTreeHandler;
import modules.shared.infrastructure.components.date.DateTimer;
import modules.shared.infrastructure.components.icons.AppIcon;
import modules.shared.infrastructure.components.theme.ThemeComponent;
import modules.shared.infrastructure.controllers.AbstractFxController;
import modules.shared.infrastructure.exceptions.InterfaceException;
import static modules.shared.application.Primitives.getBool;
import static modules.shared.application.Primitives.getTrimmedString;

import static core.config.Global.__;

import modules.connections.domain.enums.ConnectionsKeyEnum;
import modules.connections.infrastructure.factories.TargetSessionFactory;

import modules.explorer.domain.enums.DatabaseEnvironmentEnum;
import modules.explorer.domain.enums.ExplorerKeyEnum;
import modules.explorer.domain.enums.ExplorerMessageEnum;
import modules.explorer.domain.enums.ExplorerNodeTypeEnum;
import modules.explorer.domain.services.DatabaseCreateSqlBuilder;
import modules.explorer.domain.services.DatabaseDropSqlBuilder;
import modules.explorer.domain.services.DatabaseEnvironmentResolver;
import modules.explorer.domain.services.ObjectDefinitionSqlBuilder;
import modules.explorer.domain.services.TableCreateSqlBuilder;
import modules.explorer.domain.services.TableDuplicateSqlBuilder;
import modules.explorer.domain.services.TableNameValidator;
import modules.explorer.domain.services.TableSelectSqlBuilder;
import modules.explorer.application.dropdatabase.DropDatabaseDto;
import modules.explorer.application.droptable.DropTableDto;
import modules.explorer.application.duplicatetable.DuplicateTableDto;
import modules.explorer.application.listdatabaseobjects.ListDatabaseObjectsDto;
import modules.explorer.application.listdatabaseobjects.ListDatabaseObjectsResultDto;
import modules.explorer.application.listdatabases.ListDatabasesDto;
import modules.explorer.application.listtablecolumns.ListTableColumnsDto;
import modules.explorer.application.renametable.RenameTableDto;
import modules.explorer.application.renametablecolumn.RenameTableColumnDto;
import modules.explorer.application.dropdatabase.DropDatabaseService;
import modules.explorer.application.droptable.DropTableService;
import modules.explorer.application.duplicatetable.DuplicateTableService;
import modules.explorer.application.listdatabaseobjects.ListDatabaseObjectsService;
import modules.explorer.application.listdatabases.ListDatabasesService;
import modules.explorer.application.listtablecolumns.ListTableColumnsService;
import modules.explorer.application.renametable.RenameTableService;
import modules.explorer.application.renametablecolumn.RenameTableColumnService;

/**
 * El arbol de objetos de una conexion abierta, colgado bajo su nodo en el panel de conexiones: BD, carpetas, tablas y
 * columnas. Cada nivel se pide al servidor al desplegarlo (en segundo plano), asi un servidor con cientos de BD abre al
 * instante. Seleccionar un nodo hace activa su BD (como SQLyog); doble clic en una tabla, vista o columna inserta su
 * nombre en el editor. Los filtros (BD y objetos) los pone el panel.
 */
public final class ExplorerTreeController extends AbstractFxController implements ConnectionTreeHandler {

    private static final int SELECT_LIMIT = 1000;
    private static final String STYLE_SYSTEM_DATABASE = "explorer-db-system";
    private static final List<String> NODE_STYLE_CLASSES = List.of(
        "explorer-database", "explorer-active-database", "explorer-folder", "explorer-loading", "explorer-error",
        "explorer-db-" + DatabaseEnvironmentEnum.PRODUCTION,
        "explorer-db-" + DatabaseEnvironmentEnum.DEVELOPMENT,
        "explorer-db-" + DatabaseEnvironmentEnum.LOCAL,
        STYLE_SYSTEM_DATABASE
    );

    private TreeItem<Object> rootItem = new TreeItem<>();
    private final Map<String, TreeItem<Object>> databaseItemsByName = new LinkedHashMap<>();
    /**
     * Lo cargado de cada BD sin filtrar: sus carpetas y, de cada carpeta, todos sus objetos. El filtro de objetos
     * ensena un subconjunto y lo devuelve entero al vaciarlo, sin volver a pedirlo al servidor.
     */
    private final Map<TreeItem<Object>, List<TreeItem<Object>>> foldersByDatabaseItem = new HashMap<>();
    private final Map<TreeItem<Object>, List<TreeItem<Object>>> childrenByFolderItem = new HashMap<>();
    private final DatabaseEnvironmentResolver databaseEnvironmentResolver = DatabaseEnvironmentResolver.getInstance();

    private String databaseFilter = "";
    private String objectFilter = "";
    private String connectionUuid = "";
    private String dbEngine = SqlDialect.MYSQL;
    private String sessionKey = "";
    private TreeView<Object> treeView;
    private Supplier<String> activeDatabaseSupplier = () -> "";
    private Consumer<String> onUseDatabase = databaseName -> { };
    private Consumer<String> onInsertText = text -> { };
    private Consumer<String> onExecuteInNewTab = sql -> { };
    private BiConsumer<String, String> onShowTableInfo = (databaseName, tableName) -> { };
    private BiConsumer<String, String> onShowTableData = (databaseName, tableName) -> { };
    private BiConsumer<String, String> onCopyToOtherDatabase = (databaseName, tableName) -> { };
    private Consumer<String> onExportSqlDump = databaseName -> { };
    private Consumer<String> onRenameDatabase = databaseName -> { };
    private Consumer<String> onDuplicateDatabase = databaseName -> { };
    private Consumer<List<String>> onDatabasesLoaded = databaseNames -> { };
    private Consumer<String> onExecuteSqlFile = databaseName -> { };
    private Runnable onRefreshed = () -> { };
    private Runnable onViewChanged = () -> { };
    private Consumer<String> onStatusChanged = status -> { };

    private ExplorerTreeController() {
    }

    /**
     * Una instancia nueva por pestaña de conexion: guarda el estado de su arbol.
     */
    public static ExplorerTreeController getInstance() {
        return new ExplorerTreeController();
    }

    /**
     * Lo llama la pestaña de conexion al abrirse.
     */
    public void setup(
        String connectionUuid,
        String sessionKey,
        Supplier<String> activeDatabaseSupplier,
        Consumer<String> onUseDatabase,
        Consumer<String> onInsertText,
        Consumer<String> onExecuteInNewTab
    ) {
        this.connectionUuid = connectionUuid;
        this.sessionKey = sessionKey;
        this.activeDatabaseSupplier = activeDatabaseSupplier;
        this.onUseDatabase = onUseDatabase;
        this.dbEngine = TargetSessionFactory.getInstance().getDbEngine(connectionUuid);
        this.onInsertText = onInsertText;
        this.onExecuteInNewTab = onExecuteInNewTab;
    }

    /**
     * Que hacer con "Info" de una tabla o vista (bd, tabla): la pestaña de conexion abre la pestaña Info.
     */
    public void setOnShowTableInfo(BiConsumer<String, String> onShowTableInfo) {
        this.onShowTableInfo = onShowTableInfo;
    }

    /**
     * Que hacer con "Ver datos" (F11) de una tabla o vista: la pestaña de conexion abre su pestaña Datos.
     */
    public void setOnShowTableData(BiConsumer<String, String> onShowTableData) {
        this.onShowTableData = onShowTableData;
    }

    /**
     * Feature 29, "Copiar BD/tabla a otro servidor/BD" (bd, tabla; tabla vacia = la BD entera): la pestaña de conexion
     * abre el dialogo de copia. Solo en MySQL/MariaDB.
     */
    public void setOnCopyToOtherDatabase(BiConsumer<String, String> onCopyToOtherDatabase) {
        this.onCopyToOtherDatabase = onCopyToOtherDatabase;
    }

    /**
     * Cada vez que se leen las BD de la conexion (al abrirla y al refrescarla): el desplegable de la BD activa.
     */
    public void setOnDatabasesLoaded(Consumer<List<String>> onDatabasesLoaded) {
        this.onDatabasesLoaded = onDatabasesLoaded;
    }

    /**
     * "Renombrar BD..." del menu de la BD (MySQL): lo abre la pestaña de conexion, que tiene el dialogo.
     */
    public void setOnRenameDatabase(Consumer<String> onRenameDatabase) {
        this.onRenameDatabase = onRenameDatabase;
    }

    /**
     * "Duplicar BD..." del menu de la BD (MySQL): la copia en el mismo servidor; lo abre la pestaña de conexion, que
     * tiene el dialogo de copia.
     */
    public void setOnDuplicateDatabase(Consumer<String> onDuplicateDatabase) {
        this.onDuplicateDatabase = onDuplicateDatabase;
    }

    public void setOnExportSqlDump(Consumer<String> onExportSqlDump) {
        this.onExportSqlDump = onExportSqlDump;
    }

    public void setOnExecuteSqlFile(Consumer<String> onExecuteSqlFile) {
        this.onExecuteSqlFile = onExecuteSqlFile;
    }

    /**
     * Tras Refrescar (F5): la pestaña de conexion olvida su cache de esquema del autocompletado.
     */
    public void setOnRefreshed(Runnable onRefreshed) {
        this.onRefreshed = onRefreshed;
    }

    @Override
    public void attach(TreeItem<Object> connectionItem, Runnable onViewChanged, Consumer<String> onStatusChanged) {
        this.rootItem = connectionItem;
        this.onViewChanged = onViewChanged;
        this.onStatusChanged = onStatusChanged;
        rootItem.getChildren().setAll(getLoadingItem(__("Cargando bases de datos...")));
        rootItem.setExpanded(true);
        loadDatabases();
    }

    /**
     * Pide las BD al servidor y las pinta. Las que ya estaban desplegadas se recargan cerradas.
     */
    public void loadDatabases() {
        ListDatabasesDto listDatabasesDto = ListDatabasesDto.fromPrimitives(Primitives.of(
            "connectionUuid", connectionUuid,
            "sessionKey", sessionKey
        ));
        setStatus(__("Cargando bases de datos..."));
        runInBackground(
            () -> ListDatabasesService.getInstance().invoke(listDatabasesDto).getDatabaseNames(),
            databaseNames -> {
                databaseItemsByName.clear();
                foldersByDatabaseItem.clear();
                childrenByFolderItem.clear();
                for (String databaseName : databaseNames) {
                    databaseItemsByName.put(databaseName, getLazyItem(ExplorerNode.of(ExplorerNodeTypeEnum.DATABASE, databaseName, databaseName, databaseName)));
                }
                showDatabases();
                setStatus(__("{0} BD", databaseNames.size()));
                onDatabasesLoaded.accept(List.copyOf(databaseNames));
            },
            throwable -> {
                rootItem.getChildren().setAll(getErrorItem(getErrorMessage(throwable)));
                setStatus(getErrorMessage(throwable));
            },
            "load-databases"
        );
    }

    /**
     * Repinta (negrita) la BD activa: la cambia el arbol, un USE en el editor o el selector de la pestaña.
     */
    public void refreshActiveDatabase() {
        onViewChanged.run();
    }

    /**
     * Al cerrar la pestaña: la conexion se queda en el panel, sin hijos.
     */
    public void detach() {
        rootItem.getChildren().clear();
        databaseItemsByName.clear();
        foldersByDatabaseItem.clear();
        childrenByFolderItem.clear();
    }

    @Override
    public void setFilters(String databaseFilter, String objectFilter) {
        String newDatabaseFilter = getNormalizedFilter(databaseFilter);
        String newObjectFilter = getNormalizedFilter(objectFilter);
        boolean isDatabaseFilterChanged = !newDatabaseFilter.equals(this.databaseFilter);
        boolean isObjectFilterChanged = !newObjectFilter.equals(this.objectFilter);
        this.databaseFilter = newDatabaseFilter;
        this.objectFilter = newObjectFilter;
        if (isDatabaseFilterChanged && !databaseItemsByName.isEmpty()) showDatabases();
        if (!isObjectFilterChanged) return;
        for (TreeItem<Object> databaseItem : foldersByDatabaseItem.keySet()) {
            showDatabaseObjects(databaseItem);
        }
    }

    @Override
    public void refresh(TreeItem<Object> treeItem) {
        onRefreshed.run();
        TreeItem<Object> databaseItem = getDatabaseItem(treeItem);
        if (databaseItem == null) {
            loadDatabases();
            return;
        }
        reloadItem(databaseItem);
    }

    @Override
    public void onSelected(TreeItem<Object> treeItem) {
        ExplorerNode explorerNode = getNode(treeItem);
        if (explorerNode == null || explorerNode.databaseName().isEmpty()) return;
        onUseDatabase.accept(explorerNode.databaseName());
    }

    @Override
    public void onActivated(TreeItem<Object> treeItem) {
        ExplorerNode explorerNode = getNode(treeItem);
        if (explorerNode == null) return;
        if (List.of(ExplorerNodeTypeEnum.TABLE, ExplorerNodeTypeEnum.VIEW, ExplorerNodeTypeEnum.COLUMN).contains(explorerNode.type())) {
            onInsertText.accept(getQuotedIdentifier(explorerNode.name()));
        }
    }

    @Override
    public String getDragText(TreeItem<Object> treeItem) {
        ExplorerNode explorerNode = getNode(treeItem);
        if (explorerNode == null) return "";
        if (ExplorerNodeTypeEnum.DATABASE.equals(explorerNode.type())) return getQuotedIdentifier(explorerNode.databaseName());
        if (List.of(ExplorerNodeTypeEnum.TABLE, ExplorerNodeTypeEnum.VIEW, ExplorerNodeTypeEnum.COLUMN).contains(explorerNode.type())) {
            return getQuotedIdentifier(explorerNode.name());
        }
        return "";
    }

    @Override
    public void onKeyPressed(TreeItem<Object> treeItem, KeyEvent keyEvent) {
        if (keyEvent.getCode() == KeyCode.F5) {
            refresh(treeItem);
            keyEvent.consume();
            return;
        }
        ExplorerNode explorerNode = getNode(treeItem);
        // Supr sobre una tabla = "Eliminar tabla...": misma confirmacion (No por defecto, nombre en produccion)
        if (keyEvent.getCode() == KeyCode.DELETE) {
            if (explorerNode != null && ExplorerNodeTypeEnum.TABLE.equals(explorerNode.type())) {
                dropTable(treeItem, explorerNode);
                keyEvent.consume();
            }
            return;
        }
        if (keyEvent.getCode() == KeyCode.F2) {
            if (explorerNode != null && ExplorerNodeTypeEnum.DATABASE.equals(explorerNode.type()) && SqlDialect.MYSQL.equals(dbEngine)) {
                onRenameDatabase.accept(explorerNode.databaseName());
                keyEvent.consume();
            }
            else if (explorerNode != null && ExplorerNodeTypeEnum.TABLE.equals(explorerNode.type())) {
                renameTable(treeItem, explorerNode, false);
                keyEvent.consume();
            }
            else if (explorerNode != null && ExplorerNodeTypeEnum.COLUMN.equals(explorerNode.type())) {
                renameColumn(treeItem, explorerNode, null, false);
                keyEvent.consume();
            }
            return;
        }
        if (keyEvent.getCode() != KeyCode.F11) return;
        if (explorerNode != null && isTableOrView(explorerNode)) {
            onShowTableData.accept(explorerNode.databaseName(), explorerNode.name());
            keyEvent.consume();
        }
    }

    @Override
    public void updateCell(TreeCell<Object> treeCell, TreeItem<Object> treeItem) {
        if (treeView == null) treeView = treeCell.getTreeView();
        treeCell.getStyleClass().removeAll(NODE_STYLE_CLASSES);
        ExplorerNode explorerNode = getNode(treeItem);
        if (explorerNode == null) return;
        treeCell.setMnemonicParsing(false);
        treeCell.setText(explorerNode.label());
        treeCell.setGraphic(getNodeIcon(explorerNode));
        treeCell.setTooltip(null);
        switch (explorerNode.type()) {
            case ExplorerNodeTypeEnum.DATABASE -> {
                treeCell.getStyleClass().add("explorer-database");
                String environment = databaseEnvironmentResolver.getEnvironment(explorerNode.databaseName());
                if (!environment.isEmpty()) treeCell.getStyleClass().add("explorer-db-" + environment);
                if (SystemDatabaseDetector.getInstance(dbEngine).isSystemDatabase(explorerNode.databaseName())) {
                    treeCell.getStyleClass().add(STYLE_SYSTEM_DATABASE);
                    treeCell.setTooltip(new Tooltip(__("BD de sistema del servidor: cuidado al escribir en ella")));
                }
                if (explorerNode.databaseName().equals(activeDatabaseSupplier.get())) {
                    treeCell.getStyleClass().add("explorer-active-database");
                }
            }
            case ExplorerNodeTypeEnum.FOLDER -> treeCell.getStyleClass().add("explorer-folder");
            case ExplorerNodeTypeEnum.LOADING -> treeCell.getStyleClass().add("explorer-loading");
            case ExplorerNodeTypeEnum.ERROR -> {
                treeCell.getStyleClass().add("explorer-error");
                treeCell.setTooltip(new Tooltip(explorerNode.name()));
            }
            default -> {
            }
        }
        boolean isPlaceholder = ExplorerNodeTypeEnum.LOADING.equals(explorerNode.type()) || ExplorerNodeTypeEnum.ERROR.equals(explorerNode.type());
        treeCell.setContextMenu(isPlaceholder ? null : getNodeContextMenu(treeItem, explorerNode));
    }

    private void setStatus(String status) {
        onStatusChanged.accept(status);
    }

    private void showDatabases() {
        List<TreeItem<Object>> visibleItems = new ArrayList<>();
        for (Map.Entry<String, TreeItem<Object>> entry : databaseItemsByName.entrySet()) {
            if (databaseFilter.isEmpty() || entry.getKey().toLowerCase(Locale.ROOT).contains(databaseFilter)) {
                visibleItems.add(entry.getValue());
            }
        }
        rootItem.getChildren().setAll(visibleItems);
    }

    /**
     * Segundo nivel del filtro: dentro de cada BD ya desplegada, solo los objetos cuyo nombre contiene el texto. Con
     * filtro, las carpetas sin coincidencias se ocultan, las demas se abren y su contador dice "visibles/total".
     */
    private void showDatabaseObjects(TreeItem<Object> databaseItem) {
        List<TreeItem<Object>> folderItems = foldersByDatabaseItem.get(databaseItem);
        if (folderItems == null) return;
        List<TreeItem<Object>> visibleFolderItems = new ArrayList<>();
        for (TreeItem<Object> folderItem : folderItems) {
            List<TreeItem<Object>> allChildren = childrenByFolderItem.getOrDefault(folderItem, List.of());
            List<TreeItem<Object>> visibleChildren = new ArrayList<>();
            for (TreeItem<Object> childItem : allChildren) {
                if (objectFilter.isEmpty() || getNode(childItem).name().toLowerCase(Locale.ROOT).contains(objectFilter)) {
                    visibleChildren.add(childItem);
                }
            }
            folderItem.getChildren().setAll(visibleChildren);
            ExplorerNode folderNode = getNode(folderItem);
            String count = objectFilter.isEmpty() ? String.valueOf(allChildren.size()) : visibleChildren.size() + "/" + allChildren.size();
            folderItem.setValue(folderNode.withLabel(folderNode.name() + " (" + count + ")"));
            if (objectFilter.isEmpty()) {
                visibleFolderItems.add(folderItem);
                continue;
            }
            if (visibleChildren.isEmpty()) continue;
            folderItem.setExpanded(true);
            visibleFolderItems.add(folderItem);
        }
        databaseItem.getChildren().setAll(visibleFolderItems);
    }

    private String getNormalizedFilter(String filter) {
        return filter == null ? "" : filter.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Un nodo que se carga al desplegarlo: lleva un hijo "Cargando..." para que se vea el triangulo.
     */
    private TreeItem<Object> getLazyItem(ExplorerNode explorerNode) {
        TreeItem<Object> lazyItem = new TreeItem<>(explorerNode);
        lazyItem.getChildren().add(getLoadingItem(__("Cargando...")));
        lazyItem.expandedProperty().addListener((observable, wasExpanded, isExpanded) -> {
            if (isExpanded && !getNode(lazyItem).isLoaded()) {
                loadChildren(lazyItem);
            }
        });
        return lazyItem;
    }

    private TreeItem<Object> getLoadingItem(String text) {
        return new TreeItem<>(ExplorerNode.of(ExplorerNodeTypeEnum.LOADING, "", "", text));
    }

    /**
     * Un fallo al cargar, en rojo con su icono: la primera linea en el arbol y el mensaje entero en el tooltip.
     */
    private TreeItem<Object> getErrorItem(String errorMessage) {
        String firstLine = errorMessage.lines().findFirst().orElse("");
        return new TreeItem<>(ExplorerNode.of(ExplorerNodeTypeEnum.ERROR, "", errorMessage, __("Error: {0}", firstLine)));
    }

    private void reloadItem(TreeItem<Object> lazyItem) {
        reloadItem(lazyItem, () -> { });
    }

    /**
     * onLoaded: cuando una BD desplegada ya tiene sus objetos (no se llama si no estaba desplegada o falla).
     */
    private void reloadItem(TreeItem<Object> lazyItem, Runnable onLoaded) {
        forgetDatabaseObjects(lazyItem);
        lazyItem.setValue(getNode(lazyItem).withLoaded(false));
        lazyItem.getChildren().setAll(getLoadingItem(__("Cargando...")));
        if (!lazyItem.isExpanded()) return;
        ExplorerNode explorerNode = getNode(lazyItem);
        if (!ExplorerNodeTypeEnum.DATABASE.equals(explorerNode.type())) {
            loadChildren(lazyItem);
            return;
        }
        lazyItem.setValue(explorerNode.withLoaded(true));
        loadDatabaseObjects(lazyItem, explorerNode.databaseName(), onLoaded);
    }

    private void forgetDatabaseObjects(TreeItem<Object> databaseItem) {
        List<TreeItem<Object>> oldFolderItems = foldersByDatabaseItem.remove(databaseItem);
        if (oldFolderItems != null) oldFolderItems.forEach(childrenByFolderItem::remove);
    }

    private void loadChildren(TreeItem<Object> lazyItem) {
        ExplorerNode explorerNode = getNode(lazyItem);
        lazyItem.setValue(explorerNode.withLoaded(true));
        if (ExplorerNodeTypeEnum.DATABASE.equals(explorerNode.type())) {
            loadDatabaseObjects(lazyItem, explorerNode.databaseName(), () -> { });
            return;
        }
        loadTableColumns(lazyItem, explorerNode.databaseName(), explorerNode.name());
    }

    private void loadDatabaseObjects(TreeItem<Object> databaseItem, String databaseName, Runnable onLoaded) {
        ListDatabaseObjectsDto listDatabaseObjectsDto = ListDatabaseObjectsDto.fromPrimitives(Primitives.of(
            "connectionUuid", connectionUuid,
            "sessionKey", sessionKey,
            "databaseName", databaseName
        ));
        runInBackground(
            () -> ListDatabaseObjectsService.getInstance().invoke(listDatabaseObjectsDto),
            listDatabaseObjectsResultDto -> {
                forgetDatabaseObjects(databaseItem);
                foldersByDatabaseItem.put(databaseItem, getFolderItems(databaseName, listDatabaseObjectsResultDto));
                showDatabaseObjects(databaseItem);
                onLoaded.run();
            },
            throwable -> showLoadError(databaseItem, throwable),
            "load-database-objects"
        );
    }

    private List<TreeItem<Object>> getFolderItems(String databaseName, ListDatabaseObjectsResultDto listDatabaseObjectsResultDto) {
        return List.of(
            getFolderItem(databaseName, __("Tablas"), ExplorerNodeTypeEnum.TABLE, listDatabaseObjectsResultDto.getTables()),
            getFolderItem(databaseName, __("Vistas"), ExplorerNodeTypeEnum.VIEW, listDatabaseObjectsResultDto.getViews()),
            getFolderItem(databaseName, __("Procedimientos"), ExplorerNodeTypeEnum.PROCEDURE, listDatabaseObjectsResultDto.getProcedures()),
            getFolderItem(databaseName, __("Funciones"), ExplorerNodeTypeEnum.FUNCTION, listDatabaseObjectsResultDto.getFunctions()),
            getFolderItem(databaseName, __("Triggers"), ExplorerNodeTypeEnum.TRIGGER, listDatabaseObjectsResultDto.getTriggers())
        );
    }

    /**
     * Tablas y vistas se despliegan a sus columnas; rutinas y triggers son hojas.
     */
    private TreeItem<Object> getFolderItem(String databaseName, String folderLabel, String childType, List<String> childNames) {
        TreeItem<Object> folderItem = new TreeItem<>(ExplorerNode.ofFolder(
            databaseName, folderLabel, folderLabel + " (" + childNames.size() + ")", childType
        ));
        boolean hasColumns = ExplorerNodeTypeEnum.TABLE.equals(childType) || ExplorerNodeTypeEnum.VIEW.equals(childType);
        List<TreeItem<Object>> childItems = new ArrayList<>();
        for (String childName : childNames) {
            ExplorerNode childNode = ExplorerNode.of(childType, databaseName, childName, childName);
            childItems.add(hasColumns ? getLazyItem(childNode) : new TreeItem<>(childNode));
        }
        childrenByFolderItem.put(folderItem, childItems);
        return folderItem;
    }

    private void loadTableColumns(TreeItem<Object> tableItem, String databaseName, String tableName) {
        ListTableColumnsDto listTableColumnsDto = ListTableColumnsDto.fromPrimitives(Primitives.of(
            "connectionUuid", connectionUuid,
            "sessionKey", sessionKey,
            "databaseName", databaseName,
            "tableName", tableName
        ));
        runInBackground(
            () -> ListTableColumnsService.getInstance().invoke(listTableColumnsDto).getColumns(),
            columns -> {
                List<TreeItem<Object>> columnItems = new ArrayList<>();
                for (Map<String, Object> column : columns) {
                    columnItems.add(new TreeItem<>(ExplorerNode.ofColumn(
                        databaseName, getTrimmedString(column.get(ExplorerKeyEnum.COLUMN_NAME)), getColumnLabel(column), ExplorerKeyEnum.COLUMN_KEY_PRIMARY.equals(getTrimmedString(column.get(ExplorerKeyEnum.COLUMN_KEY)))
                    )));
                }
                tableItem.getChildren().setAll(columnItems);
            },
            throwable -> showLoadError(tableItem, throwable),
            "load-table-columns"
        );
    }

    /**
     * "Crear tabla..." en el menu de la BD y en el de su carpeta Tablas, en cualquier motor SQL.
     */
    private void addCreateTableMenuItem(List<MenuItem> menuItems, TreeItem<Object> treeItem, ExplorerNode explorerNode) {
        if (!TableCreateSqlBuilder.getInstance(dbEngine).isSupported()) return;
        menuItems.add(getMenuItem(AppIconEnum.TABLE, __("Crear tabla..."), () -> openNewTableEditor(treeItem, explorerNode)));
    }

    /**
     * El "Create Table" de SQLyog; al crearla, el arbol se recarga como estaba con la tabla nueva elegida.
     */
    private void openNewTableEditor(TreeItem<Object> treeItem, ExplorerNode explorerNode) {
        try {
            TableEditorDialogController.openNewTable(
                treeView == null ? null : treeView.getScene().getWindow(), connectionUuid, sessionKey, explorerNode.databaseName(),
                tableName -> {
                    setStatus(__("Tabla creada: {0}", tableName));
                    reloadKeepingView(getDatabaseItem(treeItem), ExplorerNodeTypeEnum.TABLE, tableName);
                }
            );
        }
        catch (RuntimeException e) {
            showError(e);
        }
    }

    /**
     * El "Alter Table" de SQLyog; al guardar, el arbol se recarga como estaba con la tabla (quiza renombrada) elegida.
     */
    private void openTableEditor(TreeItem<Object> tableItem, ExplorerNode explorerNode) {
        try {
            TableEditorDialogController.open(
                treeView == null ? null : treeView.getScene().getWindow(), connectionUuid, sessionKey, explorerNode.databaseName(), explorerNode.name(),
                tableName -> {
                    setStatus(__("Tabla guardada: {0}", tableName));
                    reloadKeepingView(getDatabaseItem(tableItem), ExplorerNodeTypeEnum.TABLE, tableName);
                }
            );
        }
        catch (RuntimeException e) {
            showError(e);
        }
    }

    @Override
    public boolean canCreateDatabase() {
        return DatabaseCreateSqlBuilder.getInstance(dbEngine).isSupported();
    }

    /**
     * El "Create Database" de SQLyog; al crearla se recargan las BD de la conexion.
     */
    @Override
    public void createDatabase(String connectionName) {
        try {
            CreateDatabaseDialogController.open(
                treeView == null ? null : treeView.getScene().getWindow(), connectionUuid, sessionKey, connectionName,
                databaseName -> {
                    setStatus(__("BD creada: {0}", databaseName));
                    loadDatabases();
                }
            );
        }
        catch (RuntimeException e) {
            showError(e);
        }
    }

    private String getConnectionName() {
        return rootItem.getValue() instanceof Map<?, ?> connection ? getTrimmedString(connection.get(ConnectionsKeyEnum.CONNECTION_NAME)) : "";
    }

    /**
     * El "Alter Database" de SQLyog; al guardar se recarga la BD como estaba.
     */
    private void openDatabaseEditor(TreeItem<Object> databaseItem, ExplorerNode explorerNode) {
        try {
            DatabaseEditorDialogController.open(
                treeView == null ? null : treeView.getScene().getWindow(), connectionUuid, sessionKey, explorerNode.databaseName(),
                databaseName -> {
                    setStatus(__("BD guardada: {0}", databaseName));
                    reloadKeepingView(databaseItem, ExplorerNodeTypeEnum.DATABASE, "");
                }
            );
        }
        catch (RuntimeException e) {
            showError(e);
        }
    }

    /**
     * Pide el nombre de la copia (con &lt;tabla&gt;_copy_&lt;ymdhis&gt; ya escrito) hasta que sea valido o se cancele; al
     * acabar recarga la BD y deja el arbol como estaba (lo desplegado y la primera fila visible) con la copia elegida.
     */
    private void duplicateTable(TreeItem<Object> tableItem, ExplorerNode explorerNode) {
        String defaultName = TableDuplicateSqlBuilder.getInstance(dbEngine).getCopyTableName(explorerNode.name(), DateTimer.getInstance().getNowDt());
        String copyTableName = askTableName(__("Duplicar tabla"), __("Nombre de la copia de {0}", explorerNode.name()), defaultName);
        if (copyTableName == null) return;
        DuplicateTableDto duplicateTableDto = DuplicateTableDto.fromPrimitives(Primitives.of(
            "connectionUuid", connectionUuid,
            "sessionKey", sessionKey,
            "databaseName", explorerNode.databaseName(),
            "tableName", explorerNode.name(),
            "copyTableName", copyTableName
        ));
        setStatus(__("Duplicando {0}...", explorerNode.name()));
        runInBackground(
            () -> DuplicateTableService.getInstance().invoke(duplicateTableDto).getCopyTableName(),
            createdTableName -> {
                setStatus(__("Tabla duplicada: {0}", createdTableName));
                reloadKeepingView(getDatabaseItem(tableItem), ExplorerNodeTypeEnum.TABLE, createdTableName);
            },
            throwable -> {
                setStatus(getErrorMessage(throwable));
                showError(throwable);
            },
            "duplicate-table"
        );
    }

    /**
     * F2 sobre una tabla: pide el nombre nuevo (con el actual escrito) y la renombra; en produccion (428) se confirma
     * antes. Al acabar recarga la BD como estaba, con la tabla renombrada elegida.
     */
    private void renameTable(TreeItem<Object> tableItem, ExplorerNode explorerNode, boolean isConfirmed) {
        renameTable(tableItem, explorerNode, askTableName(__("Cambiar nombre"), __("Nombre nuevo de {0}", explorerNode.name()), explorerNode.name()), isConfirmed);
    }

    private void renameTable(TreeItem<Object> tableItem, ExplorerNode explorerNode, String newTableName, boolean isConfirmed) {
        if (newTableName == null || newTableName.equals(explorerNode.name())) return;
        RenameTableDto renameTableDto = RenameTableDto.fromPrimitives(Primitives.of(
            "connectionUuid", connectionUuid,
            "sessionKey", sessionKey,
            "databaseName", explorerNode.databaseName(),
            "tableName", explorerNode.name(),
            "newTableName", newTableName,
            "isConfirmed", isConfirmed
        ));
        setStatus(__("Renombrando {0}...", explorerNode.name()));
        runInBackground(
            () -> RenameTableService.getInstance().invoke(renameTableDto).getNewTableName(),
            renamedTableName -> {
                setStatus(__("Tabla renombrada: {0} -> {1}", explorerNode.name(), renamedTableName));
                reloadKeepingView(getDatabaseItem(tableItem), ExplorerNodeTypeEnum.TABLE, renamedTableName);
            },
            throwable -> {
                if (isPreconditionRequired(throwable)) {
                    setStatus("");
                    if (isDeleteConfirmed(throwable.getMessage())) renameTable(tableItem, explorerNode, newTableName, true);
                    return;
                }
                setStatus(getErrorMessage(throwable));
                showError(throwable);
            },
            "rename-table"
        );
    }

    /**
     * F2 sobre una columna: el nombre nuevo, sin tocar su tipo; en produccion (428) se confirma antes. Al acabar
     * recarga la BD como estaba (sus columnas incluidas).
     */
    private void renameColumn(TreeItem<Object> columnItem, ExplorerNode explorerNode, String newColumnName, boolean isConfirmed) {
        ExplorerNode tableNode = columnItem.getParent() == null ? null : getNode(columnItem.getParent());
        if (tableNode == null) return;
        String askedName = newColumnName != null ? newColumnName : askName(
            __("Cambiar nombre"), __("Nombre nuevo de {0}.{1}", tableNode.name(), explorerNode.name()), explorerNode.name(),
            TableNameValidator.getInstance(dbEngine)::isValidColumn, ExplorerMessageEnum.INVALID_COLUMN_NAME_X
        );
        if (askedName == null || askedName.equals(explorerNode.name())) return;
        RenameTableColumnDto renameTableColumnDto = RenameTableColumnDto.fromPrimitives(Primitives.of(
            "connectionUuid", connectionUuid,
            "sessionKey", sessionKey,
            "databaseName", explorerNode.databaseName(),
            "tableName", tableNode.name(),
            "columnName", explorerNode.name(),
            "newColumnName", askedName,
            "isConfirmed", isConfirmed
        ));
        setStatus(__("Renombrando {0}...", explorerNode.name()));
        runInBackground(
            () -> RenameTableColumnService.getInstance().invoke(renameTableColumnDto),
            renameTableColumnResultDto -> {
                setStatus(__("Columna renombrada: {0} -> {1}", explorerNode.name(), askedName));
                reloadKeepingView(getDatabaseItem(columnItem), ExplorerNodeTypeEnum.TABLE, tableNode.name());
            },
            throwable -> {
                if (isPreconditionRequired(throwable)) {
                    setStatus("");
                    if (isDeleteConfirmed(throwable.getMessage())) renameColumn(columnItem, explorerNode, askedName, true);
                    return;
                }
                setStatus(getErrorMessage(throwable));
                showError(throwable);
            },
            "rename-column"
        );
    }

    private boolean isPreconditionRequired(Throwable throwable) {
        return throwable instanceof InterfaceException interfaceException && interfaceException.getCode() == ExceptionEnum.CODE_PRECONDITION_REQUIRED;
    }

    /**
     * Siempre se pregunta con No por defecto; en una conexion de produccion (428 del servicio) ademas hay que escribir
     * el nombre de la tabla. Al acabar recarga la BD y deja el arbol como estaba.
     */
    private void dropTable(TreeItem<Object> tableItem, ExplorerNode explorerNode) {
        if (!isDeleteConfirmed(__("¿Eliminar la tabla {0} de {1}? Se borran también sus datos y no se puede deshacer.", explorerNode.name(), explorerNode.databaseName()))) {
            return;
        }
        runDropTable(tableItem, explorerNode, false);
    }

    private void runDropTable(TreeItem<Object> tableItem, ExplorerNode explorerNode, boolean isConfirmed) {
        DropTableDto dropTableDto = DropTableDto.fromPrimitives(Primitives.of(
            "connectionUuid", connectionUuid,
            "sessionKey", sessionKey,
            "databaseName", explorerNode.databaseName(),
            "tableName", explorerNode.name(),
            "isConfirmed", isConfirmed
        ));
        setStatus(__("Eliminando {0}...", explorerNode.name()));
        runInBackground(
            () -> DropTableService.getInstance().invoke(dropTableDto),
            dropTableResultDto -> {
                setStatus(__("Tabla eliminada: {0}", explorerNode.name()));
                reloadKeepingView(getDatabaseItem(tableItem), ExplorerNodeTypeEnum.TABLE, "");
            },
            throwable -> {
                if (throwable instanceof InterfaceException interfaceException && interfaceException.getCode() == ExceptionEnum.CODE_PRECONDITION_REQUIRED) {
                    setStatus("");
                    if (isNameTyped(__("Eliminar tabla..."), throwable.getMessage(), explorerNode.name())) runDropTable(tableItem, explorerNode, true);
                    return;
                }
                setStatus(getErrorMessage(throwable));
                showError(throwable);
            },
            "drop-table"
        );
    }

    /**
     * Solo se sigue si se escribe el nombre exacto (de la tabla o de la BD).
     */
    private boolean isNameTyped(String title, String warning, String name) {
        TextInputDialog nameDialog = new TextInputDialog();
        ThemeComponent.getInstance().applyTo(nameDialog);
        nameDialog.setTitle(title);
        nameDialog.setHeaderText(warning);
        nameDialog.setContentText(__("Escribe {0} para confirmar", name));
        if (treeView != null) nameDialog.initOwner(treeView.getScene().getWindow());
        return nameDialog.showAndWait().map(String::trim).filter(name::equals).isPresent();
    }

    /**
     * "Eliminar BD": siempre hay que escribir su nombre; en produccion (428 del servicio) otra vez, con el aviso. Al acabar
     * recarga las BD; si era la activa, se queda sin BD activa.
     */
    private void dropDatabase(ExplorerNode explorerNode) {
        String warning = __("Se elimina la BD {0} con todas sus tablas, vistas, rutinas y datos. No se puede deshacer.", explorerNode.databaseName());
        if (!isNameTyped(__("Eliminar BD..."), warning, explorerNode.databaseName())) return;
        runDropDatabase(explorerNode, false);
    }

    private void runDropDatabase(ExplorerNode explorerNode, boolean isConfirmed) {
        DropDatabaseDto dropDatabaseDto = DropDatabaseDto.fromPrimitives(Primitives.of(
            "connectionUuid", connectionUuid,
            "sessionKey", sessionKey,
            "databaseName", explorerNode.databaseName(),
            "isConfirmed", isConfirmed
        ));
        setStatus(__("Eliminando {0}...", explorerNode.databaseName()));
        runInBackground(
            () -> DropDatabaseService.getInstance().invoke(dropDatabaseDto),
            dropDatabaseResultDto -> {
                setStatus(__("BD eliminada: {0}", explorerNode.databaseName()));
                if (explorerNode.databaseName().equals(activeDatabaseSupplier.get())) onUseDatabase.accept("");
                loadDatabases();
            },
            throwable -> {
                if (isPreconditionRequired(throwable)) {
                    setStatus("");
                    if (isNameTyped(__("Eliminar BD..."), throwable.getMessage(), explorerNode.databaseName())) runDropDatabase(explorerNode, true);
                    return;
                }
                setStatus(getErrorMessage(throwable));
                showError(throwable);
            },
            "drop-database"
        );
    }

    /**
     * Un nombre de tabla: solo letras, digitos y _ (TableNameValidator); si no lo es, avisa y vuelve a preguntar con lo
     * escrito. null si se cancela.
     */
    private String askTableName(String title, String message, String initialName) {
        return askName(title, message, initialName, TableNameValidator.getInstance(dbEngine)::isValid, ExplorerMessageEnum.INVALID_TABLE_NAME_X);
    }

    /**
     * Pide un nombre hasta que sea valido o se cancele (null).
     */
    private String askName(String title, String message, String initialName, Predicate<String> isValidName, String invalidMessage) {
        String proposedName = initialName;
        while (true) {
            TextInputDialog nameDialog = new TextInputDialog(proposedName);
            ThemeComponent.getInstance().applyTo(nameDialog);
            nameDialog.setTitle(title);
            nameDialog.setHeaderText(null);
            nameDialog.setContentText(message);
            if (treeView != null) nameDialog.initOwner(treeView.getScene().getWindow());
            String name = nameDialog.showAndWait().map(String::trim).orElse(null);
            if (name == null || name.isEmpty()) return null;
            if (isValidName.test(name)) return name;
            showErrorMessage(__(invalidMessage, name));
            proposedName = name;
        }
    }

    /**
     * Recarga la BD y, al llegar sus objetos, vuelve a desplegar las carpetas y tablas que lo estaban, elige el objeto
     * indicado (la tabla recien creada) y deja arriba la misma fila que antes.
     */
    private void reloadKeepingView(TreeItem<Object> databaseItem, String selectType, String selectName) {
        if (databaseItem == null) return;
        Set<String> expandedKeys = new HashSet<>();
        for (TreeItem<Object> folderItem : foldersByDatabaseItem.getOrDefault(databaseItem, List.of())) {
            if (folderItem.isExpanded()) expandedKeys.add(getViewKey(getNode(folderItem)));
            for (TreeItem<Object> childItem : childrenByFolderItem.getOrDefault(folderItem, List.of())) {
                if (childItem.isExpanded()) expandedKeys.add(getViewKey(getNode(childItem)));
            }
        }
        int firstVisibleRow = getFirstVisibleRow();
        onRefreshed.run();
        reloadItem(databaseItem, () -> {
            TreeItem<Object> itemToSelect = null;
            for (TreeItem<Object> folderItem : foldersByDatabaseItem.getOrDefault(databaseItem, List.of())) {
                if (expandedKeys.contains(getViewKey(getNode(folderItem)))) folderItem.setExpanded(true);
                for (TreeItem<Object> childItem : childrenByFolderItem.getOrDefault(folderItem, List.of())) {
                    ExplorerNode childNode = getNode(childItem);
                    if (expandedKeys.contains(getViewKey(childNode))) childItem.setExpanded(true);
                    if (childNode.type().equals(selectType) && childNode.name().equals(selectName)) itemToSelect = childItem;
                }
            }
            if (treeView == null) return;
            if (itemToSelect != null) {
                if (itemToSelect.getParent() != null) itemToSelect.getParent().setExpanded(true);
                treeView.getSelectionModel().select(itemToSelect);
            }
            if (firstVisibleRow >= 0) treeView.scrollTo(firstVisibleRow);
        });
    }

    private String getViewKey(ExplorerNode explorerNode) {
        return explorerNode.type() + "|" + explorerNode.name();
    }

    /**
     * La fila que se ve arriba del todo en el arbol; -1 si aun no hay arbol pintado.
     */
    private int getFirstVisibleRow() {
        if (treeView == null) return -1;
        if (!(treeView.lookup(".virtual-flow") instanceof VirtualFlow<?> virtualFlow)) return -1;
        IndexedCell<?> firstVisibleCell = virtualFlow.getFirstVisibleCell();
        return firstVisibleCell == null ? -1 : firstVisibleCell.getIndex();
    }

    /**
     * SELECT con las columnas de la tabla una por linea, como SQLyog; si no se pueden leer, SELECT *.
     */
    private void executeColumnsSelect(ExplorerNode explorerNode) {
        ListTableColumnsDto listTableColumnsDto = ListTableColumnsDto.fromPrimitives(Primitives.of(
            "connectionUuid", connectionUuid,
            "sessionKey", sessionKey,
            "databaseName", explorerNode.databaseName(),
            "tableName", explorerNode.name()
        ));
        TableSelectSqlBuilder tableSelectSqlBuilder = TableSelectSqlBuilder.getInstance(dbEngine);
        runInBackground(
            () -> ListTableColumnsService.getInstance().invoke(listTableColumnsDto).getColumns(),
            columns -> {
                List<String> columnNames = new ArrayList<>();
                for (Map<String, Object> column : columns) columnNames.add(getTrimmedString(column.get(ExplorerKeyEnum.COLUMN_NAME)));
                onExecuteInNewTab.accept(tableSelectSqlBuilder.getSelectSql(explorerNode.databaseName(), explorerNode.name(), columnNames, SELECT_LIMIT));
            },
            throwable -> onExecuteInNewTab.accept(tableSelectSqlBuilder.getSelectSql(explorerNode.databaseName(), explorerNode.name(), List.of(), SELECT_LIMIT)),
            "select-columns"
        );
    }

    /**
     * "id, int unsigned, PK" / "email, varchar(255), null": lo que SQLyog ensena al desplegar Columns.
     */
    private String getColumnLabel(Map<String, Object> column) {
        StringBuilder columnLabel = new StringBuilder(getTrimmedString(column.get(ExplorerKeyEnum.COLUMN_NAME)))
            .append(", ").append(getTrimmedString(column.get(ExplorerKeyEnum.COLUMN_TYPE)));
        String columnKey = getTrimmedString(column.get(ExplorerKeyEnum.COLUMN_KEY));
        if (ExplorerKeyEnum.COLUMN_KEY_PRIMARY.equals(columnKey)) columnLabel.append(", PK");
        if (ExplorerKeyEnum.COLUMN_KEY_UNIQUE.equals(columnKey)) columnLabel.append(", UNIQUE");
        if (getBool(column.get(ExplorerKeyEnum.IS_NULLABLE))) columnLabel.append(", null");
        return columnLabel.toString();
    }

    /**
     * El error se queda como hijo del nodo (se ve donde se pidio) y el nodo queda para reintentar con Refrescar.
     */
    private void showLoadError(TreeItem<Object> lazyItem, Throwable throwable) {
        lazyItem.setValue(getNode(lazyItem).withLoaded(false));
        lazyItem.getChildren().setAll(getErrorItem(getErrorMessage(throwable)));
        setStatus(getErrorMessage(throwable));
    }

    /**
     * La BD del nodo subiendo por el arbol; null si el nodo es la conexion (o esta por encima de las BD).
     */
    private TreeItem<Object> getDatabaseItem(TreeItem<Object> treeItem) {
        TreeItem<Object> currentItem = treeItem;
        while (currentItem != null) {
            ExplorerNode explorerNode = getNode(currentItem);
            if (explorerNode == null) return null;
            if (ExplorerNodeTypeEnum.DATABASE.equals(explorerNode.type())) return currentItem;
            currentItem = currentItem.getParent();
        }
        return null;
    }

    private ExplorerNode getNode(TreeItem<Object> treeItem) {
        if (treeItem == null) return null;
        return treeItem.getValue() instanceof ExplorerNode explorerNode ? explorerNode : null;
    }

    private boolean isTableOrView(ExplorerNode explorerNode) {
        return ExplorerNodeTypeEnum.TABLE.equals(explorerNode.type()) || ExplorerNodeTypeEnum.VIEW.equals(explorerNode.type());
    }

    private ContextMenu getNodeContextMenu(TreeItem<Object> treeItem, ExplorerNode explorerNode) {
        List<MenuItem> menuItems = new ArrayList<>();
        switch (explorerNode.type()) {
            case ExplorerNodeTypeEnum.DATABASE -> {
                // copiar entre motores: el origen puede ser cualquier motor SQL (MySQL a MySQL copia todo; el resto, tablas)
                menuItems.add(getMenuItem(AppIconEnum.COPY, __("Copiar BD a otro servidor/BD..."), () -> onCopyToOtherDatabase.accept(explorerNode.databaseName(), "")));
                if (SqlDialect.MYSQL.equals(dbEngine)) {
                    menuItems.add(getMenuItem(AppIconEnum.COPY, __("Duplicar BD..."), () -> onDuplicateDatabase.accept(explorerNode.databaseName())));
                    menuItems.add(getMenuItem(AppIconEnum.DATABASE_EXPORT, __("Volcado SQL (exportar a .sql)..."), () -> onExportSqlDump.accept(explorerNode.databaseName())));
                    menuItems.add(getMenuItem(AppIconEnum.EDIT, __("Alterar BD (juego de caracteres, solo lectura...)"), () -> openDatabaseEditor(treeItem, explorerNode)));
                    menuItems.add(getMenuItem(AppIconEnum.EDIT, __("Renombrar BD... (F2)"), () -> onRenameDatabase.accept(explorerNode.databaseName())));
                    menuItems.add(new SeparatorMenuItem());
                }
                menuItems.add(getMenuItem(AppIconEnum.DATABASE, __("Usar esta BD"), () -> onUseDatabase.accept(explorerNode.databaseName())));
                if (canCreateDatabase()) menuItems.add(getMenuItem(AppIconEnum.DATABASE_NEW, __("Crear BD..."), () -> createDatabase(getConnectionName())));
                if (DatabaseDropSqlBuilder.getInstance(dbEngine).isSupported()) {
                    MenuItem dropDatabaseMenuItem = getMenuItem(AppIconEnum.DELETE, __("Eliminar BD..."), () -> dropDatabase(explorerNode));
                    dropDatabaseMenuItem.setDisable(SystemDatabaseDetector.getInstance(dbEngine).isSystemDatabase(explorerNode.databaseName()));
                    menuItems.add(dropDatabaseMenuItem);
                }
                menuItems.add(getMenuItem(AppIconEnum.DATABASE_IMPORT, __("Ejecutar fichero SQL..."), () -> onExecuteSqlFile.accept(explorerNode.databaseName())));
                addCreateTableMenuItem(menuItems, treeItem, explorerNode);
                addDefinitionMenuItem(menuItems, explorerNode);
            }
            case ExplorerNodeTypeEnum.FOLDER -> {
                if (ExplorerNodeTypeEnum.TABLE.equals(explorerNode.childType())) addCreateTableMenuItem(menuItems, treeItem, explorerNode);
            }
            case ExplorerNodeTypeEnum.TABLE, ExplorerNodeTypeEnum.VIEW -> {
                menuItems.add(getMenuItem(AppIconEnum.TABLE, __("Ver datos (F11)"), () -> onShowTableData.accept(explorerNode.databaseName(), explorerNode.name())));
                menuItems.add(getMenuItem(AppIconEnum.EXECUTE, __("SELECT en pestaña nueva"), () -> executeColumnsSelect(explorerNode)));
                menuItems.add(getMenuItem(AppIconEnum.INFO, __("Info: DDL, índices y tamaño"), () -> onShowTableInfo.accept(explorerNode.databaseName(), explorerNode.name())));
                menuItems.add(getMenuItem(AppIconEnum.PASTE, __("Insertar nombre en el editor"), () -> onInsertText.accept(getQuotedIdentifier(explorerNode.name()))));
                addDefinitionMenuItem(menuItems, explorerNode);
                if (ExplorerNodeTypeEnum.TABLE.equals(explorerNode.type())) {
                    menuItems.add(getMenuItem(AppIconEnum.COPY, __("Copiar tabla a otro servidor/BD..."), () -> onCopyToOtherDatabase.accept(explorerNode.databaseName(), explorerNode.name())));
                }
                if (ExplorerNodeTypeEnum.TABLE.equals(explorerNode.type()) && SqlDialect.MYSQL.equals(dbEngine)) {
                    menuItems.add(getMenuItem(AppIconEnum.EDIT, __("Editar tabla (columnas, índices...)"), () -> openTableEditor(treeItem, explorerNode)));
                }
                if (ExplorerNodeTypeEnum.TABLE.equals(explorerNode.type())) {
                    menuItems.add(getMenuItem(AppIconEnum.COPY, __("Duplicar tabla"), () -> duplicateTable(treeItem, explorerNode)));
                    menuItems.add(getMenuItem(AppIconEnum.EDIT, __("Cambiar nombre... (F2)"), () -> renameTable(treeItem, explorerNode, false)));
                    menuItems.add(getMenuItem(AppIconEnum.DELETE, __("Eliminar tabla... (Supr)"), () -> dropTable(treeItem, explorerNode)));
                }
            }
            case ExplorerNodeTypeEnum.PROCEDURE, ExplorerNodeTypeEnum.FUNCTION, ExplorerNodeTypeEnum.TRIGGER -> addDefinitionMenuItem(menuItems, explorerNode);
            case ExplorerNodeTypeEnum.COLUMN -> {
                menuItems.add(getMenuItem(
                    AppIconEnum.PASTE, __("Insertar nombre en el editor"), () -> onInsertText.accept(getQuotedIdentifier(explorerNode.name()))
                ));
                menuItems.add(getMenuItem(AppIconEnum.EDIT, __("Cambiar nombre... (F2)"), () -> renameColumn(treeItem, explorerNode, null, false)));
            }
            default -> {
            }
        }
        if (!ExplorerNodeTypeEnum.FOLDER.equals(explorerNode.type())) {
            menuItems.add(getMenuItem(AppIconEnum.COPY, __("Copiar nombre"), () -> copyToClipboard(explorerNode.name())));
        }
        menuItems.add(new SeparatorMenuItem());
        menuItems.add(getMenuItem(AppIconEnum.REFRESH, __("Refrescar (F5)"), () -> refresh(treeItem)));
        return new ContextMenu(menuItems.toArray(MenuItem[]::new));
    }

    /**
     * "SHOW CREATE ..." en MySQL, "Ver definición" en PostgreSQL y SQLite; nada si el motor no la da (la tabla de
     * PostgreSQL: su DDL esta en Info).
     */
    private void addDefinitionMenuItem(List<MenuItem> menuItems, ExplorerNode explorerNode) {
        ObjectDefinitionSqlBuilder objectDefinitionSqlBuilder = ObjectDefinitionSqlBuilder.getInstance(dbEngine);
        String definitionSql = objectDefinitionSqlBuilder.getDefinitionSql(explorerNode.type(), explorerNode.databaseName(), explorerNode.name());
        if (definitionSql.isEmpty()) return;
        String showCreateLabel = objectDefinitionSqlBuilder.getShowCreateLabel(explorerNode.type());
        String menuLabel = showCreateLabel.isEmpty() ? __("Ver definición") : showCreateLabel;
        menuItems.add(getMenuItem(AppIconEnum.QUERY, menuLabel, () -> onExecuteInNewTab.accept(definitionSql)));
    }

    private MenuItem getMenuItem(String iconName, String text, Runnable action) {
        MenuItem menuItem = new MenuItem(text, AppIcon.of(iconName));
        menuItem.setMnemonicParsing(false);
        menuItem.setOnAction(actionEvent -> action.run());
        return menuItem;
    }

    /**
     * El icono de cada nodo: cilindro para BD, rejilla para tabla o vista, llave para la columna PK...
     */
    private AppIcon getNodeIcon(ExplorerNode explorerNode) {
        return switch (explorerNode.type()) {
            case ExplorerNodeTypeEnum.DATABASE -> AppIcon.of(AppIconEnum.DATABASE);
            case ExplorerNodeTypeEnum.FOLDER -> AppIcon.of(AppIconEnum.FOLDER);
            case ExplorerNodeTypeEnum.TABLE -> AppIcon.of(AppIconEnum.TABLE);
            case ExplorerNodeTypeEnum.VIEW -> AppIcon.of(AppIconEnum.VIEW);
            case ExplorerNodeTypeEnum.PROCEDURE -> AppIcon.of(AppIconEnum.PROCEDURE);
            case ExplorerNodeTypeEnum.FUNCTION -> AppIcon.of(AppIconEnum.FUNCTION);
            case ExplorerNodeTypeEnum.TRIGGER -> AppIcon.of(AppIconEnum.TRIGGER);
            case ExplorerNodeTypeEnum.COLUMN -> AppIcon.of(explorerNode.isPrimaryKey() ? AppIconEnum.KEY : AppIconEnum.COLUMN);
            case ExplorerNodeTypeEnum.ERROR -> AppIcon.of(AppIconEnum.ERROR);
            default -> null;
        };
    }

    private void copyToClipboard(String text) {
        ClipboardContent clipboardContent = new ClipboardContent();
        clipboardContent.putString(text);
        Clipboard.getSystemClipboard().setContent(clipboardContent);
    }

    private String getQuotedIdentifier(String identifier) {
        return SqlIdentifierQuoter.getInstance(dbEngine).getQuoted(identifier);
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
     * Un nodo del arbol. databaseName vacio solo en el de "Cargando..." / error.
     * @param childType    en una carpeta, el tipo de lo que cuelga de ella (la de "Tablas": TABLE); vacio en el resto
     * @param isPrimaryKey en una columna, si es de la clave primaria (el icono de llave); no se deduce de la etiqueta
     */
    private record ExplorerNode(String type, String databaseName, String name, String label, boolean isLoaded, String childType, boolean isPrimaryKey) {

        static ExplorerNode of(String type, String databaseName, String name, String label) {
            return new ExplorerNode(type, databaseName, name, label, false, "", false);
        }

        static ExplorerNode ofFolder(String databaseName, String name, String label, String childType) {
            return new ExplorerNode(ExplorerNodeTypeEnum.FOLDER, databaseName, name, label, false, childType, false);
        }

        static ExplorerNode ofColumn(String databaseName, String name, String label, boolean isPrimaryKey) {
            return new ExplorerNode(ExplorerNodeTypeEnum.COLUMN, databaseName, name, label, false, "", isPrimaryKey);
        }

        ExplorerNode withLoaded(boolean isLoaded) {
            return new ExplorerNode(type, databaseName, name, label, isLoaded, childType, isPrimaryKey);
        }

        ExplorerNode withLabel(String newLabel) {
            return new ExplorerNode(type, databaseName, name, newLabel, isLoaded, childType, isPrimaryKey);
        }
    }
}
