package modules.redis.infrastructure.controllers;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;

import modules.shared.domain.enums.ExceptionEnum;
import modules.shared.domain.enums.SessionKeyEnum;
import modules.shared.domain.enums.SharedConfigEnum;
import modules.shared.application.Primitives;
import modules.shared.infrastructure.components.icons.AppIconEnum;
import modules.shared.infrastructure.components.icons.AppIcon;
import modules.shared.infrastructure.components.theme.ThemeComponent;
import modules.shared.infrastructure.components.uuid.UuidGenerator;
import modules.shared.infrastructure.controllers.AbstractFxController;
import modules.shared.infrastructure.controllers.ConnectionViewController;
import modules.shared.infrastructure.exceptions.InterfaceException;
import static modules.shared.application.Primitives.getBool;
import static modules.shared.application.Primitives.getTrimmedString;

import static core.config.Global.__;

import modules.connections.domain.enums.ConnectionsKeyEnum;
import modules.connections.infrastructure.factories.RedisSessionFactory;

import modules.redis.domain.enums.RedisConfigEnum;
import modules.redis.domain.enums.RedisKeyEnum;
import modules.redis.application.changekey.ChangeKeyDto;
import modules.redis.application.createkey.CreateKeyDto;
import modules.redis.application.executecommand.ExecuteRedisCommandDto;
import modules.redis.application.getkeyvalue.GetKeyValueDto;
import modules.redis.application.getkeyvalue.GetKeyValueResultDto;
import modules.redis.application.scankeys.ScanKeysDto;
import modules.redis.application.scankeys.ScanKeysResultDto;
import modules.redis.application.changekey.ChangeKeyService;
import modules.redis.application.createkey.CreateKeyService;
import modules.redis.application.executecommand.ExecuteRedisCommandService;
import modules.redis.application.getkeyvalue.GetKeyValueService;
import modules.redis.application.scankeys.ScanKeysService;

/**
 * La pestaña de una conexion Redis: a la izquierda las claves (SCAN por patron, por tandas, agrupadas por los ":" de
 * su nombre); a la derecha el valor de la elegida, editable segun su tipo, y una consola como redis-cli. El navegador y
 * la consola llevan cada uno su sesion (Jedis no es seguro entre hilos). Todo lo que hace red va en segundo plano.
 */
public final class RedisTabController extends AbstractFxController implements ConnectionViewController {

    private static final String TAB_PREFIX = "ctab-";
    private static final int CLOSE_TIMEOUT_S = 10;
    private static final String KEY_SEPARATOR = ":";
    private static final int KEYS_PER_LOAD = 500;
    private static final int MAX_SCAN_ROUNDS_PER_LOAD = 50;

    @FXML
    private TextField patternField;
    @FXML
    private Button moreButton;
    @FXML
    private TreeView<KeyNode> keysTreeView;
    @FXML
    private Label keysStatusLabel;
    @FXML
    private TabPane redisTabPane;
    @FXML
    private Label keyNameLabel;
    @FXML
    private Label keyTypeLabel;
    @FXML
    private Label keyInfoLabel;
    @FXML
    private Button ttlButton;
    @FXML
    private Button renameButton;
    @FXML
    private Button deleteKeyButton;
    @FXML
    private Button reloadValueButton;
    @FXML
    private TextArea stringValueArea;
    @FXML
    private TableView<List<String>> valueTableView;
    @FXML
    private HBox editBar;
    @FXML
    private TextField itemFieldField;
    @FXML
    private TextField itemValueField;
    @FXML
    private TextField itemScoreField;
    @FXML
    private Button saveItemButton;
    @FXML
    private Button removeItemButton;
    @FXML
    private Label valueStatusLabel;
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
    private final ExecutorService browserExecutor = getSessionExecutor("redis-browser");
    private final ExecutorService consoleExecutor = getSessionExecutor("redis-console");
    private String consoleSessionKey = "";
    private final List<Map<String, Object>> loadedKeys = new ArrayList<>();
    private String nextCursor = RedisConfigEnum.SCAN_FIRST_CURSOR;
    private GetKeyValueResultDto currentValue;
    private final List<String> consoleHistory = new ArrayList<>();
    private int consoleHistoryIndex;

    @FXML
    private void initialize() {
        keysTreeView.setRoot(new TreeItem<>());
        keysTreeView.setCellFactory(treeView -> new KeyTreeCell());
        keysTreeView.getSelectionModel().selectedItemProperty().addListener((observable, oldItem, newItem) -> {
            if (newItem != null && newItem.getValue() != null && newItem.getValue().fullKey() != null) loadValue(newItem.getValue().fullKey());
        });
        valueTableView.getSelectionModel().selectedItemProperty().addListener((observable, oldRow, newRow) -> fillEditFields(newRow));
        consoleInputField.setOnKeyPressed(keyEvent -> {
            if (keyEvent.getCode() == KeyCode.UP) showConsoleHistory(-1);
            if (keyEvent.getCode() == KeyCode.DOWN) showConsoleHistory(1);
        });
    }

    @Override
    public void setup(Map<String, Object> connection) {
        connectionUuid = getTrimmedString(connection.get(ConnectionsKeyEnum.CONNECTION_UUID));
        tabUuid = UuidGenerator.getInstance().getUniqueIdWithPrefix(TAB_PREFIX);
        browserSessionKey = tabUuid + SessionKeyEnum.SEPARATOR + SessionKeyEnum.REDIS_BROWSER;
        consoleSessionKey = tabUuid + SessionKeyEnum.SEPARATOR + SessionKeyEnum.REDIS_CONSOLE;
        String databaseName = getTrimmedString(connection.get(ConnectionsKeyEnum.DATABASE_NAME));
        consolePromptLabel.setText("db" + (databaseName.isEmpty() ? "0" : databaseName) + ">");
        onScan();
    }

    @Override
    public void close() {
        browserExecutor.shutdownNow();
        consoleExecutor.shutdownNow();
        // shutdownNow descarta lo encolado, pero la tarea en curso sigue usando su Jedis (la E/S del socket no atiende la
        // interrupcion): se espera a que acabe antes de cerrar la sesion; si se cuelga, se cierra igual al cabo del tope
        Thread closeThread = new Thread(() -> {
            awaitTermination(browserExecutor);
            awaitTermination(consoleExecutor);
            RedisSessionFactory.getInstance().closeSessions(tabUuid);
        }, "close-redis-sessions");
        closeThread.setDaemon(true);
        closeThread.start();
    }

    /**
     * Empieza el SCAN de nuevo con el patron.
     */
    @FXML
    private void onScan() {
        loadedKeys.clear();
        nextCursor = RedisConfigEnum.SCAN_FIRST_CURSOR;
        keysTreeView.getRoot().getChildren().clear();
        loadKeys();
    }

    @FXML
    private void onMore() {
        loadKeys();
    }

    /**
     * "Nueva clave": nombre, tipo y su primer elemento (un hash necesita campo; un zset, score) y caducidad opcional.
     * Al crearla se vuelve a buscar y se abre su valor.
     */
    @FXML
    private void onNewKey() {
        TextField keyField = new TextField(getNewKeyPrefix());
        ComboBox<String> typeComboBox = new ComboBox<>();
        typeComboBox.getItems().setAll(RedisKeyEnum.CREATABLE_TYPES);
        typeComboBox.setValue(RedisKeyEnum.TYPE_STRING);
        TextField fieldField = new TextField();
        fieldField.setPromptText(__("campo"));
        TextArea valueArea = new TextArea();
        valueArea.setPrefRowCount(4);
        valueArea.setWrapText(true);
        TextField scoreField = new TextField("0");
        TextField ttlField = new TextField();
        ttlField.setPromptText(__("vacío = no caduca"));
        fieldField.disableProperty().bind(typeComboBox.valueProperty().isNotEqualTo(RedisKeyEnum.TYPE_HASH));
        scoreField.disableProperty().bind(typeComboBox.valueProperty().isNotEqualTo(RedisKeyEnum.TYPE_ZSET));

        GridPane formGrid = new GridPane();
        formGrid.setHgap(8);
        formGrid.setVgap(8);
        formGrid.addRow(0, new Label(__("Clave")), keyField);
        formGrid.addRow(1, new Label(__("Tipo")), typeComboBox);
        formGrid.addRow(2, new Label(__("Campo (hash)")), fieldField);
        formGrid.addRow(3, new Label(__("Valor")), valueArea);
        formGrid.addRow(4, new Label(__("Score (zset)")), scoreField);
        formGrid.addRow(5, new Label(__("Caduca en (s)")), ttlField);

        Dialog<ButtonType> newKeyDialog = new Dialog<>();
        ThemeComponent.getInstance().applyTo(newKeyDialog);
        newKeyDialog.setTitle(__("Nueva clave"));
        newKeyDialog.getDialogPane().setContent(formGrid);
        newKeyDialog.getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
        if (keysTreeView.getScene() != null) newKeyDialog.initOwner(keysTreeView.getScene().getWindow());
        newKeyDialog.setOnShown(event -> keyField.requestFocus());
        if (newKeyDialog.showAndWait().filter(ButtonType.OK::equals).isEmpty()) return;

        Map<String, Object> newKey = Primitives.of(
            "connectionUuid", connectionUuid,
            "sessionKey", browserSessionKey,
            "key", keyField.getText(),
            "type", typeComboBox.getValue(),
            "field", fieldField.getText(),
            "value", valueArea.getText(),
            "score", scoreField.getText(),
            "ttlSeconds", ttlField.getText()
        );
        keysStatusLabel.setText(__("Creando..."));
        runInBackground(
            () -> CreateKeyService.getInstance().invoke(CreateKeyDto.fromPrimitives(newKey)).getKey(),
            createdKey -> {
                onScan();
                loadValue(createdKey);
            },
            throwable -> {
                keysStatusLabel.setText(getErrorMessage(throwable));
                showError(throwable);
            },
            browserExecutor
        );
    }

    /**
     * El patron de busqueda sin el comodin final ("user:*" -> "user:"): la clave nueva suele ir en esa carpeta.
     */
    private String getNewKeyPrefix() {
        String pattern = patternField.getText().trim();
        return pattern.endsWith("*") && !pattern.substring(0, pattern.length() - 1).contains("*")
            ? pattern.substring(0, pattern.length() - 1)
            : "";
    }

    /**
     * SCAN devuelve pocas claves por vuelta (o ninguna, si el patron filtra mucho): se dan vueltas hasta juntar
     * KEYS_PER_LOAD o acabar, con un tope de vueltas para no quedarse leyendo una BD enorme.
     */
    private void loadKeys() {
        String pattern = patternField.getText().trim();
        String startCursor = nextCursor;
        moreButton.setDisable(true);
        keysStatusLabel.setText(__("Buscando..."));
        runInBackground(() -> {
            List<Map<String, Object>> keys = new ArrayList<>();
            String cursor = startCursor;
            boolean isComplete = false;
            for (int round = 0; round < MAX_SCAN_ROUNDS_PER_LOAD && keys.size() < KEYS_PER_LOAD; round++) {
                ScanKeysResultDto scanKeysResultDto = ScanKeysService.getInstance().invoke(ScanKeysDto.fromPrimitives(Primitives.of(
                    "connectionUuid", connectionUuid, "sessionKey", browserSessionKey, "pattern", pattern, "cursor", cursor, "count", KEYS_PER_LOAD
                )));
                keys.addAll(scanKeysResultDto.getKeys());
                cursor = scanKeysResultDto.getNextCursor();
                isComplete = scanKeysResultDto.isComplete();
                if (isComplete) break;
            }
            return Primitives.of(RedisKeyEnum.KEYS, keys, "cursor", cursor, "isComplete", isComplete);
        }, scannedKeys -> {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> keys = (List<Map<String, Object>>) scannedKeys.get(RedisKeyEnum.KEYS);
            loadedKeys.addAll(keys);
            nextCursor = String.valueOf(scannedKeys.get("cursor"));
            boolean isComplete = getBool(scannedKeys.get("isComplete"));
            moreButton.setDisable(isComplete);
            rebuildTree();
            keysStatusLabel.setText(isComplete ? __("{0} clave(s)", loadedKeys.size()) : __("{0} clave(s) · hay más (Más)", loadedKeys.size()));
        }, throwable -> keysStatusLabel.setText(getErrorMessage(throwable)), browserExecutor);
    }

    /**
     * "user:1:name" queda en user › 1 › name; las carpetas cuentan sus claves.
     */
    private void rebuildTree() {
        TreeItem<KeyNode> root = new TreeItem<>();
        Map<String, TreeItem<KeyNode>> foldersByPath = new LinkedHashMap<>();
        List<Map<String, Object>> sortedKeys = new ArrayList<>(loadedKeys);
        sortedKeys.sort((left, right) -> String.valueOf(left.get(RedisKeyEnum.KEY)).compareTo(String.valueOf(right.get(RedisKeyEnum.KEY))));
        for (Map<String, Object> key : sortedKeys) {
            String fullKey = String.valueOf(key.get(RedisKeyEnum.KEY));
            String[] parts = fullKey.split(KEY_SEPARATOR, -1);
            TreeItem<KeyNode> parent = root;
            StringBuilder path = new StringBuilder();
            for (int i = 0; i < parts.length - 1; i++) {
                path.append(parts[i]).append(KEY_SEPARATOR);
                TreeItem<KeyNode> currentParent = parent;
                parent = foldersByPath.computeIfAbsent(path.toString(), folderPath -> {
                    TreeItem<KeyNode> folder = new TreeItem<>();
                    currentParent.getChildren().add(folder);
                    return folder;
                });
                parent.setValue(new KeyNode(parts[i], null, "", 0, parent.getValue() == null ? 1 : parent.getValue().count() + 1));
            }
            long ttlMs = key.get(RedisKeyEnum.TTL_MS) instanceof Number number ? number.longValue() : -1;
            parent.getChildren().add(new TreeItem<>(new KeyNode(parts[parts.length - 1], fullKey, getTrimmedString(key.get(RedisKeyEnum.TYPE)), ttlMs, 0)));
        }
        keysTreeView.setRoot(root);
    }

    @FXML
    private void onReloadValue() {
        if (currentValue != null) loadValue(currentValue.getKey());
    }

    private void loadValue(String key) {
        valueStatusLabel.setText(__("Leyendo..."));
        runInBackground(
            () -> GetKeyValueService.getInstance().invoke(GetKeyValueDto.fromPrimitives(Primitives.of(
                "connectionUuid", connectionUuid, "sessionKey", browserSessionKey, "key", key
            ))),
            this::showValue,
            throwable -> {
                currentValue = null;
                showValueControls(false);
                keyNameLabel.setText(key);
                valueStatusLabel.setText(getErrorMessage(throwable));
            },
            browserExecutor
        );
    }

    private void showValue(GetKeyValueResultDto keyValue) {
        currentValue = keyValue;
        redisTabPane.getSelectionModel().selectFirst();
        keyNameLabel.setText(keyValue.getKey());
        keyTypeLabel.setText(keyValue.getType().toUpperCase(java.util.Locale.ROOT));
        keyTypeLabel.setVisible(true);
        keyInfoLabel.setText(getTtlText(keyValue.getTtlMs()) + " · " + (RedisKeyEnum.TYPE_STRING.equals(keyValue.getType())
            ? __("{0} bytes", keyValue.getSize()) : __("{0} elemento(s)", keyValue.getSize())));
        showValueControls(true);

        boolean isString = RedisKeyEnum.TYPE_STRING.equals(keyValue.getType());
        stringValueArea.setVisible(isString);
        valueTableView.setVisible(!isString);
        if (isString) {
            stringValueArea.setText(keyValue.getStringValue());
            stringValueArea.setEditable(!keyValue.isBinary());
        }
        else {
            fillTable(keyValue);
        }
        configureEditBar(keyValue);
        int shownRows = keyValue.getRows().size();
        valueStatusLabel.setText(keyValue.isBinary() ? __("Valor binario: se ve en hexadecimal y no se edita aquí (usa la consola).")
            : !isString && shownRows < keyValue.getSize() ? __("Se ven los primeros {0} de {1}.", shownRows, keyValue.getSize()) : "");
    }

    private void fillTable(GetKeyValueResultDto keyValue) {
        valueTableView.getColumns().clear();
        List<String> columns = keyValue.getColumns();
        for (int i = 0; i < columns.size(); i++) {
            int columnIndex = i;
            TableColumn<List<String>, String> tableColumn = new TableColumn<>(__(columns.get(i)));
            tableColumn.setCellValueFactory(cellData -> new ReadOnlyStringWrapper(
                columnIndex < cellData.getValue().size() ? cellData.getValue().get(columnIndex) : ""
            ));
            tableColumn.setPrefWidth(i == columns.size() - 1 ? 420 : 200);
            valueTableView.getColumns().add(tableColumn);
        }
        valueTableView.getItems().setAll(keyValue.getRows());
    }

    /**
     * Que se puede editar en cada tipo: hash campo+valor, list valor (y el indice de la fila elegida), set miembro,
     * zset miembro+score, string el texto entero. Los streams solo se leen.
     */
    private void configureEditBar(GetKeyValueResultDto keyValue) {
        String type = keyValue.getType();
        boolean isEditable = !RedisKeyEnum.TYPE_STREAM.equals(type) && !keyValue.isBinary();
        editBar.setVisible(isEditable);
        editBar.setManaged(isEditable);
        if (!isEditable) return;
        boolean isString = RedisKeyEnum.TYPE_STRING.equals(type);
        boolean hasField = RedisKeyEnum.TYPE_HASH.equals(type) || RedisKeyEnum.TYPE_LIST.equals(type);
        itemFieldField.setVisible(hasField);
        itemFieldField.setManaged(hasField);
        itemFieldField.setPromptText(RedisKeyEnum.TYPE_LIST.equals(type) ? __("índice (vacío = añadir al final)") : __("campo"));
        itemValueField.setVisible(!isString);
        itemValueField.setManaged(!isString);
        itemValueField.setPromptText(RedisKeyEnum.TYPE_SET.equals(type) || RedisKeyEnum.TYPE_ZSET.equals(type) ? __("miembro") : __("valor"));
        boolean isZset = RedisKeyEnum.TYPE_ZSET.equals(type);
        itemScoreField.setVisible(isZset);
        itemScoreField.setManaged(isZset);
        removeItemButton.setVisible(!isString);
        removeItemButton.setManaged(!isString);
        saveItemButton.setText(isString ? __("Guardar valor") : __("Guardar"));
        itemFieldField.clear();
        itemValueField.clear();
        itemScoreField.clear();
    }

    private void fillEditFields(List<String> row) {
        if (row == null || currentValue == null) return;
        switch (currentValue.getType()) {
            case RedisKeyEnum.TYPE_HASH, RedisKeyEnum.TYPE_LIST -> {
                itemFieldField.setText(row.get(0));
                itemValueField.setText(row.size() > 1 ? row.get(1) : "");
            }
            case RedisKeyEnum.TYPE_SET -> itemValueField.setText(row.get(0));
            case RedisKeyEnum.TYPE_ZSET -> {
                itemValueField.setText(row.get(0));
                itemScoreField.setText(row.size() > 1 ? row.get(1) : "");
            }
            default -> {
            }
        }
    }

    @FXML
    private void onSaveItem() {
        if (currentValue == null) return;
        String key = currentValue.getKey();
        Map<String, Object> change = switch (currentValue.getType()) {
            case RedisKeyEnum.TYPE_STRING -> Primitives.of("operation", RedisKeyEnum.OP_STRING_SET, "value", stringValueArea.getText());
            case RedisKeyEnum.TYPE_HASH -> Primitives.of("operation", RedisKeyEnum.OP_HASH_SET, "field", itemFieldField.getText(), "value", itemValueField.getText());
            case RedisKeyEnum.TYPE_LIST -> itemFieldField.getText().isBlank()
                ? Primitives.of("operation", RedisKeyEnum.OP_LIST_PUSH, "value", itemValueField.getText())
                : Primitives.of("operation", RedisKeyEnum.OP_LIST_SET, "index", itemFieldField.getText(), "value", itemValueField.getText());
            case RedisKeyEnum.TYPE_SET -> Primitives.of("operation", RedisKeyEnum.OP_SET_ADD, "value", itemValueField.getText());
            case RedisKeyEnum.TYPE_ZSET -> Primitives.of("operation", RedisKeyEnum.OP_ZSET_ADD, "value", itemValueField.getText(), "score", itemScoreField.getText());
            default -> null;
        };
        if (change != null) applyChange(key, change, () -> loadValue(key));
    }

    @FXML
    private void onRemoveItem() {
        if (currentValue == null) return;
        String key = currentValue.getKey();
        List<String> selectedRow = valueTableView.getSelectionModel().getSelectedItem();
        Map<String, Object> change = switch (currentValue.getType()) {
            case RedisKeyEnum.TYPE_HASH -> Primitives.of("operation", RedisKeyEnum.OP_HASH_DELETE, "field", selectedRow != null ? selectedRow.get(0) : itemFieldField.getText());
            case RedisKeyEnum.TYPE_LIST -> Primitives.of("operation", RedisKeyEnum.OP_LIST_REMOVE, "value", selectedRow != null ? selectedRow.get(1) : itemValueField.getText());
            case RedisKeyEnum.TYPE_SET -> Primitives.of("operation", RedisKeyEnum.OP_SET_REMOVE, "value", selectedRow != null ? selectedRow.get(0) : itemValueField.getText());
            case RedisKeyEnum.TYPE_ZSET -> Primitives.of("operation", RedisKeyEnum.OP_ZSET_REMOVE, "value", selectedRow != null ? selectedRow.get(0) : itemValueField.getText());
            default -> null;
        };
        if (change != null) applyChange(key, change, () -> loadValue(key));
    }

    @FXML
    private void onSetTtl() {
        if (currentValue == null) return;
        String key = currentValue.getKey();
        TextInputDialog ttlInputDialog = new TextInputDialog(currentValue.getTtlMs() > 0 ? String.valueOf(TimeUnit.MILLISECONDS.toSeconds(currentValue.getTtlMs())) : "");
        ThemeComponent.getInstance().applyTo(ttlInputDialog);
        ttlInputDialog.setTitle(__("Caducidad"));
        ttlInputDialog.setHeaderText(__("Segundos hasta que {0} caduque (vacío = no caduca nunca)", key));
        Optional<String> seconds = ttlInputDialog.showAndWait();
        if (seconds.isEmpty()) return;
        String ttlSeconds = seconds.get().trim();
        applyChange(key, ttlSeconds.isEmpty()
            ? Primitives.of("operation", RedisKeyEnum.OP_PERSIST)
            : Primitives.of("operation", RedisKeyEnum.OP_EXPIRE, "ttlSeconds", ttlSeconds), () -> loadValue(key));
    }

    @FXML
    private void onRenameKey() {
        if (currentValue == null) return;
        String key = currentValue.getKey();
        TextInputDialog newKeyInputDialog = new TextInputDialog(key);
        ThemeComponent.getInstance().applyTo(newKeyInputDialog);
        newKeyInputDialog.setTitle(__("Renombrar clave"));
        newKeyInputDialog.setHeaderText(__("Nuevo nombre para {0} (si ya existe, se sobrescribe)", key));
        Optional<String> newKey = newKeyInputDialog.showAndWait();
        if (newKey.isEmpty() || newKey.get().isEmpty() || newKey.get().equals(key)) return;
        applyChange(key, Primitives.of("operation", RedisKeyEnum.OP_RENAME, "newKey", newKey.get()), () -> {
            onScan();
            loadValue(newKey.get());
        });
    }

    @FXML
    private void onDeleteKey() {
        if (currentValue == null) return;
        String key = currentValue.getKey();
        if (!isDeleteConfirmed(__("¿Borrar la clave {0}?", key))) return;
        applyChange(key, Primitives.of("operation", RedisKeyEnum.OP_DELETE), () -> {
            currentValue = null;
            showValueControls(false);
            keyNameLabel.setText(__("(borrada) {0}", key));
            stringValueArea.setVisible(false);
            valueTableView.setVisible(false);
            loadedKeys.removeIf(loadedKey -> key.equals(loadedKey.get(RedisKeyEnum.KEY)));
            rebuildTree();
        });
    }

    /**
     * Un cambio del navegador; en produccion, borrar y renombrar preguntan antes (428) y se repite confirmado.
     */
    private void applyChange(String key, Map<String, Object> change, Runnable onDone) {
        applyChange(key, change, onDone, false);
    }

    private void applyChange(String key, Map<String, Object> change, Runnable onDone, boolean isConfirmed) {
        valueStatusLabel.setText(__("Guardando..."));
        runInBackground(
            () -> ChangeKeyService.getInstance().invoke(ChangeKeyDto.fromPrimitives(Primitives.merge(Primitives.of(
                "connectionUuid", connectionUuid, "sessionKey", browserSessionKey, "key", key, "isConfirmed", isConfirmed
            ), change))),
            changeKeyResultDto -> {
                valueStatusLabel.setText(__("Hecho ({0})", changeKeyResultDto.getAffected()));
                onDone.run();
            },
            throwable -> {
                if (isPreconditionRequired(throwable) && isProductionConfirmed(throwable.getMessage())) {
                    applyChange(key, change, onDone, true);
                    return;
                }
                valueStatusLabel.setText(getErrorMessage(throwable));
            },
            browserExecutor
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
        runCommandButton.setDisable(true);
        runInBackground(
            () -> ExecuteRedisCommandService.getInstance().invoke(ExecuteRedisCommandDto.fromPrimitives(Primitives.of(
                "connectionUuid", connectionUuid, "sessionKey", consoleSessionKey, "commandLine", commandLine, "isConfirmed", isConfirmed
            ))),
            executeRedisCommandResultDto -> {
                runCommandButton.setDisable(false);
                appendConsole(commandLine, executeRedisCommandResultDto.getOutput() + "\n(" + executeRedisCommandResultDto.getElapsedMs() + " ms)");
                consolePromptLabel.setText("db" + executeRedisCommandResultDto.getCurrentDatabase() + ">");
            },
            throwable -> {
                runCommandButton.setDisable(false);
                if (isPreconditionRequired(throwable) && isProductionConfirmed(throwable.getMessage())) {
                    runCommand(commandLine, true);
                    return;
                }
                appendConsole(commandLine, SharedConfigEnum.CONSOLE_ERROR_PREFIX + getErrorMessage(throwable));
            },
            consoleExecutor
        );
    }

    private void appendConsole(String commandLine, String output) {
        consoleOutputArea.appendText((consoleOutputArea.getText().isEmpty() ? "" : "\n\n") + consolePromptLabel.getText() + " " + commandLine + "\n" + output);
    }

    private void showConsoleHistory(int step) {
        if (consoleHistory.isEmpty()) return;
        consoleHistoryIndex = Math.max(0, Math.min(consoleHistory.size(), consoleHistoryIndex + step));
        consoleInputField.setText(consoleHistoryIndex < consoleHistory.size() ? consoleHistory.get(consoleHistoryIndex) : "");
        consoleInputField.positionCaret(consoleInputField.getText().length());
    }

    private void showValueControls(boolean isVisible) {
        keyTypeLabel.setVisible(isVisible);
        if (!isVisible) keyInfoLabel.setText("");
        for (Button button : List.of(ttlButton, renameButton, deleteKeyButton, reloadValueButton)) button.setDisable(!isVisible);
        if (!isVisible) {
            editBar.setVisible(false);
            editBar.setManaged(false);
        }
    }

    private String getTtlText(long ttlMs) {
        if (ttlMs < 0) return __("sin caducidad");
        long seconds = TimeUnit.MILLISECONDS.toSeconds(ttlMs);
        if (seconds < 120) return __("caduca en {0} s", seconds);
        if (seconds < 7200) return __("caduca en {0} min", seconds / 60);
        return __("caduca en {0} h", seconds / 3600);
    }

    private boolean isPreconditionRequired(Throwable throwable) {
        return throwable instanceof InterfaceException interfaceException && interfaceException.getCode() == ExceptionEnum.CODE_PRECONDITION_REQUIRED;
    }

    /**
     * Aviso de produccion: el boton por defecto es Cancelar, asi un Enter por inercia no lanza el FLUSHDB.
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

    /**
     * Cada sesion de Jedis va en su cola de un hilo: una conexion de Jedis no admite dos hilos a la vez (con un SCAN en
     * pipeline a medias, otra peticion daba "Cannot use Jedis when in Pipeline").
     */
    private <T> void runInBackground(Supplier<T> work, Consumer<T> onSucceeded, Consumer<Throwable> onFailed, ExecutorService sessionExecutor) {
        Task<T> backgroundTask = new Task<>() {
            @Override
            protected T call() {
                return work.get();
            }
        };
        backgroundTask.setOnSucceeded(event -> onSucceeded.accept(backgroundTask.getValue()));
        backgroundTask.setOnFailed(event -> onFailed.accept(backgroundTask.getException()));
        sessionExecutor.execute(backgroundTask);
    }

    private void awaitTermination(ExecutorService sessionExecutor) {
        try {
            sessionExecutor.awaitTermination(CLOSE_TIMEOUT_S, TimeUnit.SECONDS);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private ExecutorService getSessionExecutor(String threadName) {
        return Executors.newSingleThreadExecutor(runnable -> {
            Thread daemonThread = new Thread(runnable, threadName);
            daemonThread.setDaemon(true);
            return daemonThread;
        });
    }

    /**
     * Un nodo del arbol: una carpeta (prefijo, fullKey null, con su numero de claves) o una clave con su tipo y TTL.
     */
    private record KeyNode(String label, String fullKey, String type, long ttlMs, int count) {
    }

    private final class KeyTreeCell extends TreeCell<KeyNode> {

        @Override
        protected void updateItem(KeyNode keyNode, boolean isEmpty) {
            super.updateItem(keyNode, isEmpty);
            if (isEmpty || keyNode == null) {
                setText(null);
                setGraphic(null);
                setTooltip(null);
                return;
            }
            setMnemonicParsing(false);
            if (keyNode.fullKey() == null) {
                setText(keyNode.label() + " (" + keyNode.count() + ")");
                setGraphic(AppIcon.of(AppIconEnum.FOLDER));
                setTooltip(null);
                return;
            }
            setText(keyNode.label().isEmpty() ? __("(vacío)") : keyNode.label());
            setGraphic(AppIcon.of(getTypeIcon(keyNode.type())));
            setTooltip(new Tooltip(keyNode.fullKey() + "\n" + keyNode.type() + " · " + getTtlText(keyNode.ttlMs())));
        }

        private String getTypeIcon(String type) {
            return switch (type) {
                case RedisKeyEnum.TYPE_STRING -> AppIconEnum.TEXT;
                case RedisKeyEnum.TYPE_HASH -> AppIconEnum.TABLE;
                case RedisKeyEnum.TYPE_LIST -> AppIconEnum.QUERY;
                case RedisKeyEnum.TYPE_SET, RedisKeyEnum.TYPE_ZSET -> AppIconEnum.COLUMN;
                default -> AppIconEnum.MESSAGES;
            };
        }
    }
}
