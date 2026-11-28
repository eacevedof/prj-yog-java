package modules.connections.infrastructure.controllers;

import java.io.File;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.paint.Color;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import modules.shared.application.Primitives;
import modules.shared.infrastructure.controllers.AbstractFxController;
import static modules.shared.application.Primitives.getBool;
import static modules.shared.application.Primitives.getTrimmedString;

import static core.config.Global.__;

import modules.connections.domain.enums.ConnectionsConfigEnum;
import modules.connections.domain.enums.ConnectionsKeyEnum;
import modules.connections.domain.enums.DbEngineEnum;
import modules.connections.domain.enums.TunnelFormKeyEnum;
import modules.connections.application.createconnection.CreateConnectionDto;
import modules.connections.application.getconnection.GetConnectionDto;
import modules.connections.application.testconnection.TestConnectionDto;
import modules.connections.application.testconnection.TestConnectionResultDto;
import modules.connections.application.updateconnection.UpdateConnectionDto;
import modules.connections.application.createconnection.CreateConnectionService;
import modules.connections.application.getconnection.GetConnectionService;
import modules.connections.application.testconnection.TestConnectionService;
import modules.connections.application.updateconnection.UpdateConnectionService;

/**
 * Alta y edicion de una conexion. En edicion la password guardada no se muestra: el campo queda vacio y solo se
 * cambia si se escribe en el (isPasswordChanged).
 */
public final class ConnectionDialogController extends AbstractFxController {

    private static final String STYLE_MESSAGE_ERROR = "form-message-error";
    private static final String STYLE_MESSAGE_OK = "form-message-ok";

    @FXML
    private TextField connectionNameField;
    @FXML
    private ComboBox<String> dbEngineComboBox;
    @FXML
    private Label databaseNameLabel;
    @FXML
    private Button sqliteFileButton;
    @FXML
    private TextField hostField;
    @FXML
    private TextField portField;
    @FXML
    private TextField userNameField;
    @FXML
    private PasswordField userPasswordField;
    @FXML
    private TextField databaseNameField;
    @FXML
    private CheckBox useColorCheckBox;
    @FXML
    private ColorPicker colorPicker;
    @FXML
    private CheckBox isProductionCheckBox;
    @FXML
    private Label messageLabel;
    @FXML
    private Button testButton;
    @FXML
    private TabPane connectionTabPane;
    @FXML
    private Tab sshTab;
    @FXML
    private Tab httpTab;
    @FXML
    private Label tunnelSummaryLabel;
    @FXML
    private CheckBox sshEnabledCheckBox;
    @FXML
    private TextField sshHostField;
    @FXML
    private TextField sshPortField;
    @FXML
    private TextField sshUserField;
    @FXML
    private PasswordField sshPasswordField;
    @FXML
    private TextField sshPrivateKeyPathField;
    @FXML
    private PasswordField sshPassphraseField;
    @FXML
    private Button sshPrivateKeyButton;
    @FXML
    private CheckBox httpEnabledCheckBox;
    @FXML
    private TextField httpUrlField;
    @FXML
    private TextField httpTimeoutField;

    private String connectionUuid = "";
    private boolean isPasswordChanged;
    private boolean isSshSecretsChanged;
    private boolean isHttpUrlChanged;
    private Consumer<String> onSaved;

    @FXML
    private void initialize() {
        colorPicker.disableProperty().bind(useColorCheckBox.selectedProperty().not());
        initDbEngineComboBox();
        userPasswordField.textProperty().addListener((observable, oldText, newText) -> isPasswordChanged = true);
        sshPasswordField.textProperty().addListener((observable, oldText, newText) -> isSshSecretsChanged = true);
        sshPassphraseField.textProperty().addListener((observable, oldText, newText) -> isSshSecretsChanged = true);
        httpUrlField.textProperty().addListener((observable, oldText, newText) -> isHttpUrlChanged = true);
        for (Node sshField : List.of(sshHostField, sshPortField, sshUserField, sshPasswordField, sshPrivateKeyPathField, sshPassphraseField, sshPrivateKeyButton)) {
            sshField.disableProperty().bind(sshEnabledCheckBox.selectedProperty().not());
        }
        for (Node httpField : List.of(httpUrlField, httpTimeoutField)) {
            httpField.disableProperty().bind(httpEnabledCheckBox.selectedProperty().not());
        }
        // un tunel u otro, como en SQLyog
        sshEnabledCheckBox.selectedProperty().addListener((observable, wasSelected, isSelected) -> {
            if (isSelected) httpEnabledCheckBox.setSelected(false);
            refreshTunnelSummary();
        });
        httpEnabledCheckBox.selectedProperty().addListener((observable, wasSelected, isSelected) -> {
            if (isSelected) sshEnabledCheckBox.setSelected(false);
            refreshTunnelSummary();
        });
        refreshTunnelSummary();
    }

    /**
     * connectionUuid vacio = alta. onSaved recibe el uuid guardado (el panel recarga y lo selecciona).
     * @throws modules.connections.domain.exceptions.ConnectionsException si la conexion ya no existe
     */
    public void loadConnection(String connectionUuid, Consumer<String> onSaved) {
        this.connectionUuid = connectionUuid;
        this.onSaved = onSaved;
        if (connectionUuid.isEmpty()) {
            dbEngineComboBox.setValue(DbEngineEnum.DEFAULT);
            portField.setText(String.valueOf(DbEngineEnum.MYSQL_DEFAULT_PORT));
            sshPortField.setText(String.valueOf(ConnectionsConfigEnum.DEFAULT_SSH_PORT));
            httpTimeoutField.setText(String.valueOf(ConnectionsConfigEnum.DEFAULT_HTTP_TIMEOUT_S));
            isPasswordChanged = true;
            isSshSecretsChanged = true;
            isHttpUrlChanged = true;
            return;
        }

        Map<String, Object> connection = GetConnectionService.getInstance().invoke(
            GetConnectionDto.fromPrimitives(Primitives.of("connectionUuid", connectionUuid))
        ).getConnection();
        connectionNameField.setText(getTrimmedString(connection.get(ConnectionsKeyEnum.CONNECTION_NAME)));
        dbEngineComboBox.setValue(getTrimmedString(connection.get(ConnectionsKeyEnum.DB_ENGINE)));
        hostField.setText(getTrimmedString(connection.get(ConnectionsKeyEnum.HOST)));
        portField.setText(getTrimmedString(connection.get(ConnectionsKeyEnum.PORT)));
        userNameField.setText(getTrimmedString(connection.get(ConnectionsKeyEnum.USER_NAME)));
        userPasswordField.setPromptText(__("(sin cambios)"));
        databaseNameField.setText(getTrimmedString(connection.get(ConnectionsKeyEnum.DATABASE_NAME)));
        String colorHex = getTrimmedString(connection.get(ConnectionsKeyEnum.COLOR_HEX));
        useColorCheckBox.setSelected(!colorHex.isEmpty());
        if (!colorHex.isEmpty()) {
            colorPicker.setValue(Color.web(colorHex));
        }
        isProductionCheckBox.setSelected(getBool(connection.get(ConnectionsKeyEnum.IS_PRODUCTION)));
        loadSshTunnel(connection);
        loadHttpTunnel(connection);
        isPasswordChanged = false;
        isSshSecretsChanged = false;
        isHttpUrlChanged = false;
    }

    /**
     * Feature 12. Como la password de la BD, la password y la passphrase SSH guardadas no se muestran.
     */
    private void loadSshTunnel(Map<String, Object> connection) {
        boolean isSshEnabled = getBool(connection.get(ConnectionsKeyEnum.SSH_ENABLED));
        sshEnabledCheckBox.setSelected(isSshEnabled);
        sshHostField.setText(getTrimmedString(connection.get(ConnectionsKeyEnum.SSH_HOST)));
        sshPortField.setText(getTrimmedString(connection.get(ConnectionsKeyEnum.SSH_PORT)));
        sshUserField.setText(getTrimmedString(connection.get(ConnectionsKeyEnum.SSH_USER)));
        sshPrivateKeyPathField.setText(getTrimmedString(connection.get(ConnectionsKeyEnum.SSH_PRIVATE_KEY_PATH)));
        sshPasswordField.setPromptText(__("(sin cambios)"));
        sshPassphraseField.setPromptText(__("(sin cambios)"));
    }

    /**
     * Feature 13. La URL lleva el token de acceso: como una password, la guardada no se muestra.
     */
    private void loadHttpTunnel(Map<String, Object> connection) {
        httpEnabledCheckBox.setSelected(getBool(connection.get(ConnectionsKeyEnum.HTTP_ENABLED)));
        httpTimeoutField.setText(getTrimmedString(connection.get(ConnectionsKeyEnum.HTTP_TIMEOUT_S)));
        httpUrlField.setPromptText(__("(guardada, sin cambios)"));
    }

    /**
     * El motor: se ve su nombre ("PostgreSQL") y se guarda su clave ("pgsql"). Al cambiarlo, el puerto pasa al del
     * motor nuevo si seguia en el del anterior (o vacio), y la BD de PostgreSQL deja de ser opcional (sin ella,
     * "postgres").
     */
    private void initDbEngineComboBox() {
        dbEngineComboBox.getItems().setAll(DbEngineEnum.VALUES);
        dbEngineComboBox.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(String dbEngine) {
                return dbEngine == null ? "" : __(DbEngineEnum.LABELS.getOrDefault(dbEngine, dbEngine));
            }

            @Override
            public String fromString(String label) {
                return DbEngineEnum.LABELS.entrySet().stream()
                    .filter(entry -> __(entry.getValue()).equals(label))
                    .map(Map.Entry::getKey)
                    .findFirst()
                    .orElse(label);
            }
        });
        dbEngineComboBox.valueProperty().addListener((observable, oldDbEngine, newDbEngine) -> {
            if (newDbEngine == null) return;
            String port = portField.getText().trim();
            int oldDefaultPort = oldDbEngine == null ? 0 : DbEngineEnum.getDefaultPort(oldDbEngine);
            if (port.isEmpty() || port.equals(String.valueOf(oldDefaultPort))) {
                int newDefaultPort = DbEngineEnum.getDefaultPort(newDbEngine);
                portField.setText(newDefaultPort == 0 ? "" : String.valueOf(newDefaultPort));
            }
            int defaultPort = DbEngineEnum.getDefaultPort(newDbEngine);
            portField.setPromptText(defaultPort == 0 ? "" : String.valueOf(defaultPort));
            boolean isPgsql = DbEngineEnum.PGSQL.equals(newDbEngine);
            boolean isFiles = DbEngineEnum.FILES.equals(newDbEngine);
            boolean isSqlite = DbEngineEnum.SQLITE.equals(newDbEngine) || isFiles;
            databaseNameField.setPromptText(isPgsql ? DbEngineEnum.PGSQL_DEFAULT_DATABASE : isSqlite ? __("C:\\datos\\app.sqlite (por el túnel: la clave)") : __("(opcional)"));
            boolean isRedis = DbEngineEnum.REDIS.equals(newDbEngine);
            databaseNameLabel.setText(isFiles ? __("Carpeta o fichero") : isSqlite ? __("Fichero") : isRedis ? __("BD (índice)") : __("Base de datos"));
            if (isFiles) databaseNameField.setPromptText(__("C:\\datos\\exportados (sus .csv, .tsv, .json y .jsonl)"));
            if (isRedis) databaseNameField.setPromptText("0");
            boolean isMongo = DbEngineEnum.MONGODB.equals(newDbEngine);
            if (isMongo) databaseNameField.setPromptText(__("(opcional: la BD de la consola)"));
            hostField.setPromptText(isMongo ? __("127.0.0.1 o mongodb+srv://cluster.ejemplo.net (sin usuario)") : "127.0.0.1");
            boolean isMssql = DbEngineEnum.MSSQL.equals(newDbEngine);
            userNameField.setPromptText(isPgsql ? "postgres" : isMssql ? "sa" : isRedis ? __("(opcional, usuario ACL)") : isMongo ? __("(opcional; se autentica en admin)") : "root");
            // el tunel PHP: todos los motores menos Ficheros (la carpeta es local)
            boolean isHttpSupported = DbEngineEnum.isHttpTunnelSupported(newDbEngine);
            if (!isHttpSupported) httpEnabledCheckBox.setSelected(false);
            httpEnabledCheckBox.setDisable(!isHttpSupported);
            userPasswordField.setDisable(isFiles);
            // en edicion, la password guardada sigue «(sin cambios)» aunque se pase por SQLite y se vuelva
            userPasswordField.setPromptText(isSqlite ? __("(solo por el túnel HTTP)") : connectionUuid.isEmpty() ? "" : __("(sin cambios)"));
            // SQLite no tiene servidor: ni host, ni puerto, ni usuario, ni SSH
            for (Node serverField : List.of(hostField, portField, userNameField)) serverField.setDisable(isSqlite);
            sqliteFileButton.setVisible(isSqlite);
            sqliteFileButton.setManaged(isSqlite);
            if (isSqlite) sshEnabledCheckBox.setSelected(false);
            sshEnabledCheckBox.setDisable(isSqlite);
            refreshTunnelSummary();
        });
    }

    /**
     * El boton junto a Fichero/Carpeta: un .sqlite en SQLite; la carpeta de los ficheros en "Ficheros".
     */
    @FXML
    private void onChooseSqliteFile() {
        if (DbEngineEnum.FILES.equals(dbEngineComboBox.getValue())) {
            chooseFilesFolder();
            return;
        }
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(__("Fichero SQLite"));
        fileChooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("SQLite", "*.sqlite", "*.sqlite3", "*.db", "*.db3"),
            new FileChooser.ExtensionFilter(__("Todos"), "*.*")
        );
        File currentFile = new File(databaseNameField.getText().trim());
        if (currentFile.getParentFile() != null && currentFile.getParentFile().isDirectory()) fileChooser.setInitialDirectory(currentFile.getParentFile());
        File sqliteFile = fileChooser.showOpenDialog(getStage());
        if (sqliteFile != null) databaseNameField.setText(sqliteFile.getAbsolutePath());
    }

    private void chooseFilesFolder() {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle(__("Carpeta con los CSV y JSON"));
        File currentFolder = new File(databaseNameField.getText().trim());
        if (currentFolder.isDirectory()) directoryChooser.setInitialDirectory(currentFolder);
        File filesFolder = directoryChooser.showDialog(getStage());
        if (filesFolder != null) databaseNameField.setText(filesFolder.getAbsolutePath());
    }

    /**
     * En la pestaña MySQL se ve por donde va la conexion, para no olvidar que hay un tunel puesto en otra pestaña.
     */
    private void refreshTunnelSummary() {
        if (DbEngineEnum.FILES.equals(dbEngineComboBox.getValue())) {
            tunnelSummaryLabel.setText(__("Ficheros: cada .csv, .tsv, .json o .jsonl de la carpeta es una tabla (si la ruta es un fichero, solo ese). Los cambios se escriben con «Guardar en ficheros»."));
            return;
        }
        boolean isSqlite = DbEngineEnum.SQLITE.equals(dbEngineComboBox.getValue());
        if (isSqlite) {
            tunnelSummaryLabel.setText(httpEnabledCheckBox.isSelected()
                ? __("SQLite por el túnel HTTP: Fichero es la clave de la lista de BDs del PHP y Password, su YOG_SQLITE_PASSWORD.")
                : __("SQLite local: Fichero es la ruta del .sqlite. Si es una carpeta, al guardar se crea dentro un .sqlite vacío."));
            return;
        }
        if (sshEnabledCheckBox.isSelected()) {
            tunnelSummaryLabel.setText(__("Va por el túnel SSH (pestaña SSH): Host y Puerto son la BD vista desde el servidor SSH."));
        }
        else if (httpEnabledCheckBox.isSelected()) {
            tunnelSummaryLabel.setText(__("Va por el túnel HTTP (pestaña HTTP): Host y Puerto son la BD vista desde el servidor del túnel."));
        }
        else {
            tunnelSummaryLabel.setText(__("Conexión directa (sin túnel)."));
        }
    }

    @FXML
    private void onChoosePrivateKey() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(__("Clave privada SSH"));
        File sshDirectory = new File(System.getProperty("user.home"), ".ssh");
        if (sshDirectory.isDirectory()) fileChooser.setInitialDirectory(sshDirectory);
        File privateKeyFile = fileChooser.showOpenDialog(getStage());
        if (privateKeyFile != null) sshPrivateKeyPathField.setText(privateKeyFile.getAbsolutePath());
    }

    @FXML
    private void onSave() {
        try {
            String savedConnectionUuid;
            if (connectionUuid.isEmpty()) {
                savedConnectionUuid = CreateConnectionService.getInstance()
                    .invoke(CreateConnectionDto.fromPrimitives(getFormPrimitives()))
                    .getConnectionUuid();
            }
            else {
                UpdateConnectionService.getInstance().invoke(UpdateConnectionDto.fromPrimitives(getFormPrimitives()));
                savedConnectionUuid = connectionUuid;
            }
            onSaved.accept(savedConnectionUuid);
            getStage().close();
        }
        catch (RuntimeException e) {
            showMessage(getErrorMessage(e), STYLE_MESSAGE_ERROR);
            selectTabWithError(getErrorMessage(e));
        }
    }

    /**
     * Hace red: va en un hilo aparte para no congelar la ventana, con el boton desactivado mientras tanto.
     */
    @FXML
    private void onTestConnection() {
        TestConnectionDto testConnectionDto = TestConnectionDto.fromPrimitives(getFormPrimitives());
        Task<TestConnectionResultDto> testConnectionTask = new Task<>() {
            @Override
            protected TestConnectionResultDto call() throws SQLException {
                return TestConnectionService.getInstance().invoke(testConnectionDto);
            }
        };
        testConnectionTask.setOnSucceeded(event -> {
            testButton.setDisable(false);
            showMessage(testConnectionTask.getValue().getMessage(), STYLE_MESSAGE_OK);
        });
        testConnectionTask.setOnFailed(event -> {
            testButton.setDisable(false);
            showMessage(getErrorMessage(testConnectionTask.getException()), STYLE_MESSAGE_ERROR);
        });

        testButton.setDisable(true);
        showMessage(__("Probando..."), STYLE_MESSAGE_OK);
        Thread testConnectionThread = new Thread(testConnectionTask, "test-connection");
        testConnectionThread.setDaemon(true);
        testConnectionThread.start();
    }

    @FXML
    private void onCancel() {
        getStage().close();
    }

    private Map<String, Object> getFormPrimitives() {
        return Primitives.of(
            "connectionUuid", connectionUuid,
            "isPasswordChanged", isPasswordChanged,
            "connectionName", connectionNameField.getText(),
            "dbEngine", dbEngineComboBox.getValue(),
            "host", hostField.getText(),
            "port", portField.getText(),
            "userName", userNameField.getText(),
            "userPassword", userPasswordField.getText(),
            "databaseName", databaseNameField.getText(),
            "colorHex", useColorCheckBox.isSelected() ? getColorHex(colorPicker.getValue()) : "",
            "isProduction", isProductionCheckBox.isSelected(),
            TunnelFormKeyEnum.SSH_ENABLED, sshEnabledCheckBox.isSelected(),
            TunnelFormKeyEnum.SSH_HOST, sshHostField.getText(),
            TunnelFormKeyEnum.SSH_PORT, sshPortField.getText(),
            TunnelFormKeyEnum.SSH_USER, sshUserField.getText(),
            TunnelFormKeyEnum.SSH_PASSWORD, sshPasswordField.getText(),
            TunnelFormKeyEnum.SSH_PRIVATE_KEY_PATH, sshPrivateKeyPathField.getText(),
            TunnelFormKeyEnum.SSH_PASSPHRASE, sshPassphraseField.getText(),
            TunnelFormKeyEnum.IS_SSH_SECRETS_CHANGED, isSshSecretsChanged,
            TunnelFormKeyEnum.HTTP_ENABLED, httpEnabledCheckBox.isSelected(),
            TunnelFormKeyEnum.HTTP_URL, httpUrlField.getText(),
            TunnelFormKeyEnum.HTTP_TIMEOUT_S, httpTimeoutField.getText(),
            TunnelFormKeyEnum.IS_HTTP_URL_CHANGED, isHttpUrlChanged
        );
    }

    private String getColorHex(Color color) {
        return "#%02x%02x%02x".formatted(
            Math.round(color.getRed() * 255),
            Math.round(color.getGreen() * 255),
            Math.round(color.getBlue() * 255)
        );
    }

    /**
     * Los errores de validacion llevan delante la columna (ssh_host: ..., http_url: ...): se abre su pestaña.
     */
    private void selectTabWithError(String errorMessage) {
        if (errorMessage.contains(ConnectionsKeyEnum.SSH_PREFIX)) connectionTabPane.getSelectionModel().select(sshTab);
        else if (errorMessage.contains(ConnectionsKeyEnum.HTTP_PREFIX)) connectionTabPane.getSelectionModel().select(httpTab);
    }

    private void showMessage(String message, String styleClass) {
        messageLabel.getStyleClass().removeAll(STYLE_MESSAGE_ERROR, STYLE_MESSAGE_OK);
        messageLabel.getStyleClass().add(styleClass);
        messageLabel.setText(message);
        // la ventana crece con el mensaje: con varios errores de validacion se cortaba en "..." y no se veia cual era
        Platform.runLater(() -> getStage().sizeToScene());
    }

    private Stage getStage() {
        return (Stage) connectionNameField.getScene().getWindow();
    }
}
