package modules.explorer.infrastructure.controllers;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.StringConverter;

import modules.shared.domain.enums.ExceptionEnum;
import modules.shared.domain.services.SqlDialect;
import modules.shared.application.Primitives;
import modules.shared.infrastructure.components.theme.ThemeComponent;
import modules.shared.infrastructure.controllers.AbstractFxController;
import modules.shared.infrastructure.exceptions.InterfaceException;

import core.config.TranslatorResourceBundle;
import static core.config.Global.__;

import modules.explorer.domain.entities.DatabaseCreationEntity;
import modules.explorer.domain.services.DatabaseCreateSqlBuilder;
import modules.explorer.application.createdatabase.CreateDatabaseDto;
import modules.explorer.application.createdatabase.CreateDatabaseResultDto;
import modules.explorer.application.getcreatedatabaseoptions.GetCreateDatabaseOptionsDto;
import modules.explorer.application.getcreatedatabaseoptions.GetCreateDatabaseOptionsResultDto;
import modules.explorer.application.createdatabase.CreateDatabaseService;
import modules.explorer.application.getcreatedatabaseoptions.GetCreateDatabaseOptionsService;

/**
 * El "Create Database" de SQLyog: nombre y lo que acepta el CREATE del motor, con el SQL que se va a ejecutar a la
 * vista. MySQL/MariaDB: juego de caracteres, cotejamiento, cifrado por defecto (MySQL 8) y comentario (MariaDB 10.5+);
 * PostgreSQL: el esquema y su propietario; SQL Server: cotejamiento. Lo que el servidor no da sale desactivado. Al
 * crear, onCreated recibe el nombre (el arbol recarga sus BD).
 */
public final class CreateDatabaseDialogController extends AbstractFxController {

    private static final String PATH_DIALOG = "/modules/explorer/infrastructure/views/create-database-dialog.fxml";
    private static final List<String> ENCRYPTION_VALUES = List.of("", DatabaseCreationEntity.ENCRYPTION_YES, DatabaseCreationEntity.ENCRYPTION_NO);
    // el cotejamiento que se propone para cada juego (si el servidor lo tiene); para el resto, el del servidor o el primero
    private static final Map<String, String> PREFERRED_COLLATIONS = Map.of("utf8mb4", "utf8mb4_general_ci");

    @FXML
    private Label connectionNameLabel;
    @FXML
    private Label databaseNameLabel;
    @FXML
    private TextField databaseNameField;
    @FXML
    private Label characterSetLabel;
    @FXML
    private ComboBox<String> characterSetComboBox;
    @FXML
    private Label collationLabel;
    @FXML
    private ComboBox<String> collationComboBox;
    @FXML
    private Label encryptionLabel;
    @FXML
    private ComboBox<String> encryptionComboBox;
    @FXML
    private Label commentLabel;
    @FXML
    private TextField commentField;
    @FXML
    private Label ownerLabel;
    @FXML
    private ComboBox<String> ownerComboBox;
    @FXML
    private CheckBox ifNotExistsCheckBox;
    @FXML
    private Label engineHintLabel;
    @FXML
    private TextArea sqlTextArea;
    @FXML
    private Label statusLabel;
    @FXML
    private Button createButton;
    @FXML
    private Button closeButton;

    private String connectionUuid = "";
    private String sessionKey = "";
    private String dbEngine = "";
    private Map<String, List<String>> collationsByCharacterSet = Map.of();
    private Consumer<String> onCreated = databaseName -> { };

    /**
     * Abre el dialogo (no bloquea) y lee las opciones del servidor en segundo plano.
     */
    public static void open(Window owner, String connectionUuid, String sessionKey, String connectionName, Consumer<String> onCreated) {
        FXMLLoader fxmlLoader = new FXMLLoader(CreateDatabaseDialogController.class.getResource(PATH_DIALOG), TranslatorResourceBundle.getInstance());
        Parent dialogRoot;
        try {
            dialogRoot = fxmlLoader.load();
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        CreateDatabaseDialogController createDatabaseDialogController = fxmlLoader.getController();
        Stage dialogStage = new Stage();
        dialogStage.initOwner(owner);
        dialogStage.initModality(Modality.WINDOW_MODAL);
        dialogStage.setTitle(__("Crear BD"));
        Scene dialogScene = new Scene(dialogRoot);
        ThemeComponent.getInstance().applyTo(dialogScene);
        dialogStage.setScene(dialogScene);
        createDatabaseDialogController.setup(connectionUuid, sessionKey, connectionName, onCreated);
        dialogStage.show();
        createDatabaseDialogController.databaseNameField.requestFocus();
    }

    private void setup(String connectionUuid, String sessionKey, String connectionName, Consumer<String> onCreated) {
        this.connectionUuid = connectionUuid;
        this.sessionKey = sessionKey;
        this.onCreated = onCreated;
        connectionNameLabel.setText(connectionName);
        encryptionComboBox.getItems().setAll(ENCRYPTION_VALUES);
        encryptionComboBox.setConverter(new EncryptionConverter());
        encryptionComboBox.setValue("");
        createButton.setDisable(true);
        setStatus(__("Leyendo las opciones del servidor..."));
        GetCreateDatabaseOptionsDto getCreateDatabaseOptionsDto = GetCreateDatabaseOptionsDto.fromPrimitives(Primitives.of(
            "connectionUuid", connectionUuid, "sessionKey", sessionKey
        ));
        Task<GetCreateDatabaseOptionsResultDto> task = new Task<>() {
            @Override
            protected GetCreateDatabaseOptionsResultDto call() throws SQLException {
                return GetCreateDatabaseOptionsService.getInstance().invoke(getCreateDatabaseOptionsDto);
            }
        };
        task.setOnSucceeded(event -> showOptions(task.getValue()));
        task.setOnFailed(event -> setErrorStatus(getErrorMessage(task.getException())));
        startDaemon(task, "create-database-read");
    }

    private void showOptions(GetCreateDatabaseOptionsResultDto getCreateDatabaseOptionsResultDto) {
        dbEngine = getCreateDatabaseOptionsResultDto.getDbEngine();
        switch (dbEngine) {
            case SqlDialect.MYSQL -> showMysqlOptions(getCreateDatabaseOptionsResultDto);
            case SqlDialect.PGSQL -> showPgsqlOptions(getCreateDatabaseOptionsResultDto);
            default -> showMssqlOptions(getCreateDatabaseOptionsResultDto);
        }
        databaseNameField.textProperty().addListener((observable, oldValue, newValue) -> refreshSqlPreview());
        collationComboBox.valueProperty().addListener((observable, oldValue, newValue) -> refreshSqlPreview());
        encryptionComboBox.valueProperty().addListener((observable, oldValue, newValue) -> refreshSqlPreview());
        commentField.textProperty().addListener((observable, oldValue, newValue) -> refreshSqlPreview());
        ownerComboBox.valueProperty().addListener((observable, oldValue, newValue) -> refreshSqlPreview());
        ownerComboBox.getEditor().textProperty().addListener((observable, oldValue, newValue) -> refreshSqlPreview());
        ifNotExistsCheckBox.selectedProperty().addListener((observable, oldValue, newValue) -> refreshSqlPreview());
        refreshSqlPreview();
        setStatus("");
    }

    private void showMysqlOptions(GetCreateDatabaseOptionsResultDto getCreateDatabaseOptionsResultDto) {
        collationsByCharacterSet = getCreateDatabaseOptionsResultDto.getCollationsByCharacterSet();
        removeRows(ownerLabel, ownerComboBox);
        characterSetComboBox.getItems().setAll(collationsByCharacterSet.keySet());
        characterSetComboBox.setValue(getCreateDatabaseOptionsResultDto.getDefaultCharacterSet());
        collationComboBox.getItems().setAll(collationsByCharacterSet.getOrDefault(getCreateDatabaseOptionsResultDto.getDefaultCharacterSet(), List.of()));
        collationComboBox.setValue(getPreferredCollation(getCreateDatabaseOptionsResultDto.getDefaultCharacterSet(), getCreateDatabaseOptionsResultDto.getDefaultCollation()));
        characterSetComboBox.valueProperty().addListener((observable, oldCharacterSet, newCharacterSet) -> {
            // al cambiar de juego, su cotejamiento por defecto (el primero)
            List<String> collations = collationsByCharacterSet.getOrDefault(newCharacterSet, List.of());
            collationComboBox.getItems().setAll(collations);
            collationComboBox.setValue(getPreferredCollation(newCharacterSet, collations.isEmpty() ? "" : collations.getFirst()));
            refreshSqlPreview();
        });
        setOptionSupported(encryptionComboBox, getCreateDatabaseOptionsResultDto.isEncryptionSupported());
        setOptionSupported(commentField, getCreateDatabaseOptionsResultDto.isCommentSupported());
        engineHintLabel.setText(__("El juego de caracteres y el cotejamiento son los que toman las tablas nuevas de la BD."));
    }

    /**
     * utf8mb4_general_ci para utf8mb4 si el servidor lo tiene; si no, fallbackCollation.
     */
    private String getPreferredCollation(String characterSet, String fallbackCollation) {
        String preferredCollation = PREFERRED_COLLATIONS.get(characterSet);
        boolean isAvailable = preferredCollation != null && collationsByCharacterSet.getOrDefault(characterSet, List.of()).contains(preferredCollation);
        return isAvailable ? preferredCollation : fallbackCollation;
    }

    private void showPgsqlOptions(GetCreateDatabaseOptionsResultDto getCreateDatabaseOptionsResultDto) {
        removeRows(characterSetLabel, characterSetComboBox, collationLabel, collationComboBox, encryptionLabel, encryptionComboBox,
            commentLabel, commentField);
        databaseNameLabel.setText(__("Esquema"));
        ownerComboBox.getItems().setAll(getCreateDatabaseOptionsResultDto.getOwners());
        ownerComboBox.setValue(getCreateDatabaseOptionsResultDto.getDefaultOwner());
        engineHintLabel.setText(__("En PostgreSQL cada BD del árbol es un esquema de la base de datos de la conexión: se crea con CREATE SCHEMA."));
    }

    private void showMssqlOptions(GetCreateDatabaseOptionsResultDto getCreateDatabaseOptionsResultDto) {
        removeRows(characterSetLabel, characterSetComboBox, encryptionLabel, encryptionComboBox, commentLabel, commentField,
            ownerLabel, ownerComboBox);
        collationComboBox.getItems().setAll(getCreateDatabaseOptionsResultDto.getCollations());
        collationComboBox.setValue(getCreateDatabaseOptionsResultDto.getDefaultCollation());
        engineHintLabel.setText(__("Los ficheros de datos y de log van a la carpeta por defecto del servidor."));
    }

    /**
     * Las filas que el motor no usa salen del formulario (ocultas dejarian el hueco del vgap del GridPane).
     */
    private void removeRows(Node... nodes) {
        if (!(nodes[0].getParent() instanceof GridPane formGrid)) return;
        formGrid.getChildren().removeAll(nodes);
        List<Integer> usedRows = formGrid.getChildren().stream().map(node -> getRowIndex(node)).distinct().sorted().toList();
        for (Node node : formGrid.getChildren()) GridPane.setRowIndex(node, usedRows.indexOf(getRowIndex(node)));
    }

    private int getRowIndex(Node node) {
        Integer rowIndex = GridPane.getRowIndex(node);
        return rowIndex == null ? 0 : rowIndex;
    }

    private boolean isInForm(Node node) {
        return node.getParent() != null && !node.isDisabled();
    }

    /**
     * Lo que el servidor no da: desactivado y con el porque en el tooltip.
     */
    private void setOptionSupported(Control control, boolean isSupported) {
        control.setDisable(!isSupported);
        if (!isSupported) control.setTooltip(new Tooltip(__("Este servidor no tiene esta opción")));
    }

    private DatabaseCreationEntity getDatabaseCreationEntity() {
        return DatabaseCreationEntity.fromPrimitives(getCreationPrimitives());
    }

    /**
     * Solo lo que se ve y esta activo: el resto va vacio para que no se escriba en el CREATE.
     */
    private Map<String, Object> getCreationPrimitives() {
        return Primitives.of(
            "databaseName", databaseNameField.getText(),
            "characterSet", getVisibleValue(characterSetComboBox),
            "collation", getVisibleValue(collationComboBox),
            "encryption", getVisibleValue(encryptionComboBox),
            "comment", isInForm(commentField) ? commentField.getText() : "",
            "owner", isInForm(ownerComboBox) ? ownerComboBox.getEditor().getText() : "",
            "isIfNotExists", ifNotExistsCheckBox.isSelected()
        );
    }

    private String getVisibleValue(ComboBox<String> comboBox) {
        if (!isInForm(comboBox) || comboBox.getValue() == null) return "";
        return comboBox.getValue();
    }

    private void refreshSqlPreview() {
        if (dbEngine.isEmpty()) return;
        String createSql = DatabaseCreateSqlBuilder.getInstance(dbEngine).getCreateSql(getDatabaseCreationEntity());
        sqlTextArea.setText(createSql.isEmpty() ? "-- " + __("Escribe el nombre de la BD") : createSql + ";");
        createButton.setDisable(createSql.isEmpty());
    }

    @FXML
    private void onCreate() {
        if (dbEngine.isEmpty()) return;
        createDatabase(false);
    }

    private void createDatabase(boolean isConfirmed) {
        Map<String, Object> creationPrimitives = getCreationPrimitives();
        CreateDatabaseDto createDatabaseDto = CreateDatabaseDto.fromPrimitives(Primitives.merge(creationPrimitives, Primitives.of(
            "connectionUuid", connectionUuid, "sessionKey", sessionKey, "isConfirmed", isConfirmed
        )));
        createButton.setDisable(true);
        setStatus(__("Creando..."));
        Task<CreateDatabaseResultDto> task = new Task<>() {
            @Override
            protected CreateDatabaseResultDto call() throws SQLException {
                return CreateDatabaseService.getInstance().invoke(createDatabaseDto);
            }
        };
        task.setOnSucceeded(event -> {
            onCreated.accept(task.getValue().getDatabaseName());
            ((Stage) closeButton.getScene().getWindow()).close();
        });
        task.setOnFailed(event -> {
            createButton.setDisable(false);
            Throwable throwable = task.getException();
            if (throwable instanceof InterfaceException interfaceException && interfaceException.getCode() == ExceptionEnum.CODE_PRECONDITION_REQUIRED) {
                setStatus("");
                if (isConfirmed(throwable.getMessage() + ".\n\n" + __("¿Crear igualmente?"))) createDatabase(true);
                return;
            }
            setErrorStatus(getErrorMessage(throwable));
        });
        startDaemon(task, "create-database-save");
    }

    @FXML
    private void onClose() {
        ((Stage) closeButton.getScene().getWindow()).close();
    }

    private void setStatus(String status) {
        statusLabel.getStyleClass().remove("form-message-error");
        statusLabel.setText(status);
    }

    private void setErrorStatus(String status) {
        setStatus(status);
        statusLabel.getStyleClass().add("form-message-error");
    }

    private void startDaemon(Task<?> task, String threadName) {
        Thread thread = new Thread(task, threadName);
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * "" = lo que diga el servidor (no se escribe ENCRYPTION).
     */
    private final class EncryptionConverter extends StringConverter<String> {

        @Override
        public String toString(String encryption) {
            if (DatabaseCreationEntity.ENCRYPTION_YES.equals(encryption)) return __("Sí: tablas nuevas cifradas (ENCRYPTION 'Y')");
            if (DatabaseCreationEntity.ENCRYPTION_NO.equals(encryption)) return __("No (ENCRYPTION 'N')");
            return __("Lo que diga el servidor");
        }

        @Override
        public String fromString(String text) {
            return "";
        }
    }
}
