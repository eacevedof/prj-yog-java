package modules.shared.infrastructure.components.icons;

import java.util.ArrayList;
import java.util.List;

import javafx.collections.ListChangeListener;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Ellipse;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.transform.Scale;
import javafx.stage.Stage;
import javafx.stage.Window;

/**
 * El logo de multidbs, "1 gestor, n motores": un cilindro de BD grande en el centro (el gestor) unido a cinco nodos de
 * colores alrededor (los motores: MySQL/MariaDB, PostgreSQL, SQLite, Redis y MongoDB). Solo colores, sin logos de
 * terceros. Vectorial, dibujado en un lienzo de 64x64 y escalado; sin ficheros de imagen.
 */
public final class AppLogo {

    private static final double CANVAS = 64;
    private static final double CENTER = CANVAS / 2;
    private static final double ORBIT_RADIUS = 23;
    private static final double NODE_RADIUS = 5;
    // un color por motor, en el orden de las agujas del reloj desde arriba
    private static final List<String> ENGINE_COLORS = List.of("#f29111", "#4f8cc9", "#7fd3f7", "#e53935", "#43a047");
    private static final List<Integer> WINDOW_ICON_SIZES = List.of(16, 24, 32, 48, 64, 128, 256);

    private AppLogo() {
    }

    /**
     * El logo de size x size pixeles.
     */
    public static Node of(double size) {
        Group logo = new Group(getShapes().toArray(Node[]::new));
        double scale = size / CANVAS;
        logo.getTransforms().add(new Scale(scale, scale, 0, 0));
        return new Group(logo);
    }

    /**
     * Los iconos de la ventana (barra de tareas, Alt+Tab): el sistema elige el tamaño que mejor le encaja. Hay que
     * llamarlo desde el hilo de JavaFX.
     */
    public static List<Image> getWindowIcons() {
        List<Image> windowIcons = new ArrayList<>();
        SnapshotParameters snapshotParameters = new SnapshotParameters();
        snapshotParameters.setFill(Color.TRANSPARENT);
        for (int size : WINDOW_ICON_SIZES) {
            WritableImage snapshot = of(size).snapshot(snapshotParameters, new WritableImage(size, size));
            // copia en memoria: la imagen de snapshot() no le vale a Windows como icono (salia el de Java)
            WritableImage windowIcon = new WritableImage(size, size);
            windowIcon.getPixelWriter().setPixels(0, 0, size, size, snapshot.getPixelReader(), 0, 0);
            windowIcons.add(windowIcon);
        }
        return windowIcons;
    }

    /**
     * Pone el logo a toda ventana que se abra (principal, dialogos, alertas, visor de celda) sin tocar cada una.
     * Desde el hilo de JavaFX, una vez al arrancar.
     */
    public static void applyToAllWindows() {
        List<Image> windowIcons = getWindowIcons();
        Window.getWindows().addListener((ListChangeListener<Window>) change -> {
            while (change.next()) {
                for (Window window : change.getAddedSubList()) {
                    if (window instanceof Stage stage && stage.getIcons().isEmpty()) stage.getIcons().setAll(windowIcons);
                }
            }
        });
    }

    private static List<Node> getShapes() {
        List<Node> shapes = new ArrayList<>();

        Rectangle background = new Rectangle(0, 0, CANVAS, CANVAS);
        background.setArcWidth(28);
        background.setArcHeight(28);
        background.setFill(new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
            new Stop(0, Color.web("#24507f")), new Stop(1, Color.web("#122a45"))));
        shapes.add(background);

        List<double[]> nodeCenters = new ArrayList<>();
        for (int i = 0; i < ENGINE_COLORS.size(); i++) {
            double angle = Math.toRadians(-90 + i * 360.0 / ENGINE_COLORS.size());
            nodeCenters.add(new double[]{CENTER + ORBIT_RADIUS * Math.cos(angle), CENTER + ORBIT_RADIUS * Math.sin(angle)});
        }
        // los cables, debajo de todo lo demas
        for (double[] nodeCenter : nodeCenters) {
            Line cable = new Line(CENTER, CENTER, nodeCenter[0], nodeCenter[1]);
            cable.setStroke(Color.web("#ffffff", 0.55));
            cable.setStrokeWidth(2);
            cable.setStrokeLineCap(StrokeLineCap.ROUND);
            shapes.add(cable);
        }
        for (int i = 0; i < nodeCenters.size(); i++) {
            Circle engineNode = new Circle(nodeCenters.get(i)[0], nodeCenters.get(i)[1], NODE_RADIUS, Color.web(ENGINE_COLORS.get(i)));
            engineNode.setStroke(Color.WHITE);
            engineNode.setStrokeWidth(1.5);
            shapes.add(engineNode);
        }

        // el gestor: cilindro de BD
        SVGPath cylinderBody = new SVGPath();
        cylinderBody.setContent("M22 23 V41 A10 4 0 0 0 42 41 V23 Z");
        cylinderBody.setFill(new LinearGradient(0, 0, 1, 0, true, CycleMethod.NO_CYCLE,
            new Stop(0, Color.web("#ffffff")), new Stop(1, Color.web("#cfe3f7"))));
        shapes.add(cylinderBody);
        for (double bandY : new double[]{29, 35}) {
            SVGPath band = new SVGPath();
            band.setContent("M22 " + bandY + " A10 4 0 0 0 42 " + bandY);
            band.setFill(Color.TRANSPARENT);
            band.setStroke(Color.web("#4f8cc9"));
            band.setStrokeWidth(1.4);
            shapes.add(band);
        }
        Ellipse cylinderTop = new Ellipse(CENTER, 23, 10, 4);
        cylinderTop.setFill(Color.web("#90caf9"));
        cylinderTop.setStroke(Color.web("#ffffff"));
        cylinderTop.setStrokeWidth(1);
        shapes.add(cylinderTop);
        return shapes;
    }
}
