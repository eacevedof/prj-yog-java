package core.boot;

import java.io.IOException;
import java.io.UncheckedIOException;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import modules.shared.infrastructure.components.fonts.FontsComponent;
import modules.shared.infrastructure.components.icons.AppLogo;
import modules.shared.infrastructure.components.theme.ThemeComponent;
import modules.shared.infrastructure.factories.DbFactory;

import modules.connections.infrastructure.factories.MongoSessionFactory;
import modules.connections.infrastructure.factories.RedisSessionFactory;
import modules.connections.infrastructure.factories.TargetSessionFactory;

import core.config.TranslatorResourceBundle;

/**
 * La Application de JavaFX: tiene que ser publica y con constructor sin argumentos (la instancia JavaFX).
 */
public final class DesktopApplication extends Application {

    public static final String APP_TITLE = "multi-dbs";
    public static final String PATH_STYLES = ThemeComponent.PATH_STYLES;
    private static final String PATH_MAIN_WINDOW = "/modules/workspace/infrastructure/views/main-window.fxml";

    @Override
    public void start(Stage stage) {
        ErrorLogComponent.getInstance().install();
        FontsComponent.getInstance().loadAll();
        Parent root;
        try {
            root = new FXMLLoader(DesktopApplication.class.getResource(PATH_MAIN_WINDOW), TranslatorResourceBundle.getInstance()).load();
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        Scene scene = new Scene(root, 1200, 760);
        ThemeComponent.getInstance().applyTo(scene);
        stage.setTitle(APP_TITLE);
        AppLogo.applyToAllWindows();
        stage.setScene(scene);
        stage.show();
    }

    @Override
    public void stop() {
        TargetSessionFactory.getInstance().closeAll();
        RedisSessionFactory.getInstance().closeAll();
        MongoSessionFactory.getInstance().closeAll();
        DbFactory.getInstance().closeAll();
    }
}
