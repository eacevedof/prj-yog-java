package modules.shared.infrastructure.components.icons;

import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
import javafx.scene.text.Font;
import javafx.scene.text.FontPosture;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;

/**
 * Icono vectorial de 16x16 dibujado con formas: hace el papel de los iconos de SQLyog (enchufe verde para conectar,
 * play azul para ejecutar, cilindro para BD, rejilla para tabla...) con un dibujo propio. Al ser vectorial se ve
 * nitido a cualquier escala y no lleva ficheros de imagen.
 * <p>
 * Desde FXML: {@code <graphic><AppIcon name="execute"/></graphic>}. Desde codigo: {@code AppIcon.of(AppIconEnum.EXECUTE)}.
 */
public final class AppIcon extends Pane {

    private static final double SIZE = 16;

    private static final String BLUE = "#1e88e5";
    private static final String BLUE_DARK = "#1565c0";
    private static final String BLUE_LIGHT = "#90caf9";
    private static final String GREEN = "#43a047";
    private static final String GREEN_DARK = "#2e7d32";
    private static final String RED = "#e53935";
    private static final String RED_DARK = "#c62828";
    private static final String ORANGE = "#fb8c00";
    private static final String ORANGE_DARK = "#e65100";
    private static final String YELLOW = "#ffca28";
    private static final String YELLOW_DARK = "#f9a825";
    private static final String PURPLE = "#8e24aa";
    private static final String GRAY = "#9e9e9e";
    private static final String GRAY_DARK = "#616161";
    private static final String SLATE = "#546e7a";
    private static final String WHITE = "#ffffff";

    private String name = "";

    public AppIcon() {
        setMinSize(SIZE, SIZE);
        setPrefSize(SIZE, SIZE);
        setMaxSize(SIZE, SIZE);
        getStyleClass().add("app-icon");
    }

    public static AppIcon of(String name) {
        AppIcon appIcon = new AppIcon();
        appIcon.setName(name);
        return appIcon;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name == null ? "" : name;
        getChildren().setAll(getShapes(this.name));
    }

    private static Node[] getShapes(String name) {
        return switch (name) {
            case AppIconEnum.CONNECT -> new Node[]{getPlug()};
            case AppIconEnum.CONNECTION_NEW -> new Node[]{getPlug(), getPlusBadge()};
            case AppIconEnum.DISCONNECT -> new Node[]{getPlug(GRAY, GRAY_DARK), getStroked("M2.5 13.5 L13.5 2.5", RED, 1.8)};
            case AppIconEnum.EDIT -> new Node[]{
                getFilled("M3 13 L4 9.5 L11 2.5 L13.5 5 L6.5 12 Z", ORANGE, ORANGE_DARK),
                getFilled("M3 13 L4 9.5 L6.5 12 Z", "#ffe0b2", ORANGE_DARK),
            };
            case AppIconEnum.COPY -> new Node[]{
                getFilled("M5.5 1.5 H13.5 V11 H5.5 Z", WHITE, BLUE),
                getFilled("M2.5 4.5 H10.5 V14.5 H2.5 Z", "#e3f2fd", BLUE),
                getStroked("M4.5 8 H8.5 M4.5 10.5 H8.5", BLUE_LIGHT, 1),
            };
            case AppIconEnum.DELETE -> new Node[]{
                getFilled("M4 5.5 H12 L11.3 14.5 H4.7 Z", GRAY, GRAY_DARK),
                getStroked("M2.5 4 H13.5 M6.5 4 V2.5 H9.5 V4", GRAY_DARK, 1.4),
                getStroked("M6.5 7.5 V12.5 M9.5 7.5 V12.5", WHITE, 1.1),
            };
            case AppIconEnum.REFRESH -> new Node[]{
                getStroked("M13 8 A5 5 0 1 1 11.5 4.4", GREEN, 2),
                getFilled("M13.5 1.5 V6 H9 Z", GREEN, GREEN),
            };
            case AppIconEnum.EXECUTE -> new Node[]{
                getCircle(BLUE, BLUE_DARK),
                getFilled("M6.3 4.8 V11.2 L11.3 8 Z", WHITE, null),
            };
            case AppIconEnum.EXECUTE_ALL -> new Node[]{
                getCircle(BLUE, BLUE_DARK),
                getFilled("M4.3 5 V11 L8.3 8 Z M8.3 5 V11 L12.3 8 Z", WHITE, null),
            };
            case AppIconEnum.CANCEL -> new Node[]{
                getCircle(RED, RED_DARK),
                getFilled("M5.5 5.5 H10.5 V10.5 H5.5 Z", WHITE, null),
            };
            case AppIconEnum.HISTORY -> new Node[]{
                getCircle(WHITE, SLATE),
                getStroked("M8 4.5 V8 L10.5 10", RED, 1.4),
            };
            case AppIconEnum.INFO -> new Node[]{
                getCircle(BLUE, BLUE_DARK),
                getFilled("M7.1 6.8 H8.9 V12 H7.1 Z M8 3.6 A1.05 1.05 0 1 1 7.99 3.6 Z", WHITE, null),
            };
            case AppIconEnum.DATABASE -> new Node[]{
                getFilled("M2 4 V12 A6 2.6 0 0 0 14 12 V4 Z", BLUE, BLUE_DARK),
                getStroked("M2 8 A6 2.6 0 0 0 14 8", BLUE_LIGHT, 1),
                getFilled("M2 4 A6 2.4 0 1 0 14 4 A6 2.4 0 1 0 2 4 Z", "#64b5f6", BLUE_DARK),
            };
            case AppIconEnum.DATABASE_EXPORT -> append(getDatabase(), getArrowBadge(true));
            case AppIconEnum.DATABASE_IMPORT -> append(getDatabase(), getArrowBadge(false));
            case AppIconEnum.DATABASE_NEW -> append(getDatabase(), getPlusBadge());
            case AppIconEnum.TABLE -> getGrid("#42a5f5");
            case AppIconEnum.VIEW -> getGrid("#66bb6a");
            case AppIconEnum.ADD_ROW -> append(getGrid("#42a5f5"), getPlusBadge());
            case AppIconEnum.PROCEDURE -> new Node[]{
                getFilled("M1.5 3.5 Q1.5 1.5 3.5 1.5 H12.5 Q14.5 1.5 14.5 3.5 V12.5 Q14.5 14.5 12.5 14.5 H3.5 Q1.5 14.5 1.5 12.5 Z", PURPLE, null),
                getStroked("M4.5 5 L7.5 8 L4.5 11 M8.5 11.5 H11.5", WHITE, 1.6),
            };
            case AppIconEnum.FUNCTION -> new Node[]{
                getFilled("M1.5 3.5 Q1.5 1.5 3.5 1.5 H12.5 Q14.5 1.5 14.5 3.5 V12.5 Q14.5 14.5 12.5 14.5 H3.5 Q1.5 14.5 1.5 12.5 Z", ORANGE, null),
                getText("f", 12, WHITE, 5.2, 12.2),
            };
            case AppIconEnum.TRIGGER -> new Node[]{
                getFilled("M9.5 1 L3 9 H7.2 L6 15 L13 6.5 H8.8 Z", YELLOW, YELLOW_DARK),
            };
            case AppIconEnum.COLUMN -> new Node[]{
                getFilled("M2.5 5.5 H13.5 V10.5 H2.5 Z", BLUE_LIGHT, BLUE),
                getStroked("M6 5.5 V10.5 M10 5.5 V10.5", BLUE, 1),
            };
            case AppIconEnum.KEY -> new Node[]{
                getStroked("M8.5 8 H14.5 M12.5 8 V11 M14.5 8 V10.5", YELLOW_DARK, 1.8),
                getStroked("M8.5 8 A3 3 0 1 1 2.5 8 A3 3 0 1 1 8.5 8 Z", YELLOW_DARK, 1.8),
            };
            case AppIconEnum.FOLDER -> new Node[]{
                getFilled("M1.5 3.5 H6.5 L8 5 H14.5 V13.5 H1.5 Z", YELLOW, YELLOW_DARK),
                getStroked("M1.5 6.5 H14.5", YELLOW_DARK, 0.8),
            };
            case AppIconEnum.QUERY -> getSheet(BLUE);
            case AppIconEnum.TEXT -> getSheet(GRAY);
            case AppIconEnum.MESSAGES -> new Node[]{
                getFilled("M1.5 2.5 H14.5 V11 H7 L4 14 V11 H1.5 Z", "#fff8e1", YELLOW_DARK),
                getStroked("M4 5.5 H12 M4 8 H10", YELLOW_DARK, 1),
            };
            case AppIconEnum.SET_NULL -> new Node[]{
                getStroked("M13 8 A5 5 0 1 1 3 8 A5 5 0 1 1 13 8 Z M3.8 12.2 L12.2 3.8", GRAY_DARK, 1.5),
            };
            case AppIconEnum.SAVE -> new Node[]{
                getFilled("M2 3 Q2 2 3 2 H12 L14 4 V13 Q14 14 13 14 H3 Q2 14 2 13 Z", BLUE, BLUE_DARK),
                getFilled("M4.5 2 H11 V6 H4.5 Z", WHITE, null),
                getFilled("M9 2.8 H10.3 V5.2 H9 Z", BLUE_DARK, null),
                getFilled("M4 9 H12 V14 H4 Z", "#e3f2fd", null),
            };
            case AppIconEnum.DISCARD -> new Node[]{
                getStroked("M5 5 H10 A3.5 3.5 0 0 1 10 12 H5", ORANGE, 2),
                getFilled("M1.5 5 L6 1.5 V8.5 Z", ORANGE, null),
            };
            case AppIconEnum.PASTE -> new Node[]{
                getFilled("M3 3 H13 V14.5 H3 Z", "#a1887f", "#6d4c41"),
                getFilled("M5 5 H11 V13 H5 Z", WHITE, null),
                getFilled("M6 1.5 H10 V4 H6 Z", GRAY, GRAY_DARK),
            };
            case AppIconEnum.SEARCH -> new Node[]{
                getStroked("M10.5 6.5 A4 4 0 1 1 2.5 6.5 A4 4 0 1 1 10.5 6.5 Z", SLATE, 1.6),
                getStroked("M9.5 9.5 L14 14", SLATE, 2.2),
            };
            case AppIconEnum.CLEAR_FILTER -> new Node[]{
                getFilled("M1.5 2.5 H12.5 L8.5 7.5 V13 L5.5 11.5 V7.5 Z", GRAY, SLATE),
                getStroked("M10 10 L14.5 14.5 M14.5 10 L10 14.5", RED, 1.8),
            };
            case AppIconEnum.CLOSE -> new Node[]{getStroked("M4 4 L12 12 M12 4 L4 12", GRAY_DARK, 1.8)};
            case AppIconEnum.EXIT -> new Node[]{
                getStroked("M5 4.5 A5 5 0 1 0 11 4.5", RED, 1.8),
                getStroked("M8 1.8 V8", RED, 1.8),
            };
            case AppIconEnum.DARK_THEME -> new Node[]{
                getFilled("M10.5 1.8 A6.4 6.4 0 1 0 14.2 11.2 A5.2 5.2 0 0 1 10.5 1.8 Z", "#5c6bc0", "#3949ab"),
            };
            case AppIconEnum.ERROR -> new Node[]{
                getCircle(RED, RED_DARK),
                getStroked("M8 4.2 V8.8 M8 11.6 V11.8", WHITE, 1.9),
            };
            case AppIconEnum.WARNING -> new Node[]{
                getFilled("M8 1.3 L15 14.2 H1 Z", ORANGE, ORANGE_DARK),
                getStroked("M8 5.6 V9.6 M8 12 V12.2", WHITE, 1.8),
            };
            case AppIconEnum.GLOBE -> new Node[]{
                getCircle("#e3f2fd", BLUE),
                getStroked("M1.5 8 H14.5 M8 1.5 Q4 8 8 14.5 Q12 8 8 1.5 M2.6 4.8 H13.4 M2.6 11.2 H13.4", BLUE, 1),
            };
            default -> new Node[0];
        };
    }

    /**
     * Enchufe verde inclinado: abrir una conexion (el papel del "Connect" de SQLyog).
     */
    private static Node getPlug() {
        return getPlug(GREEN, GREEN_DARK);
    }

    /**
     * El enchufe de una conexion en el panel, del color de su entorno (sin color, el verde de "Conectar").
     */
    public static AppIcon ofPlug(String colorHex) {
        AppIcon appIcon = new AppIcon();
        if (colorHex == null || colorHex.isBlank()) {
            appIcon.setName(AppIconEnum.CONNECT);
            return appIcon;
        }
        appIcon.name = AppIconEnum.CONNECT;
        Color fill = Color.web(colorHex);
        appIcon.getChildren().setAll(getPlug(getHex(fill), getHex(fill.darker())));
        return appIcon;
    }

    private static String getHex(Color color) {
        return String.format("#%02x%02x%02x",
            (int) Math.round(color.getRed() * 255), (int) Math.round(color.getGreen() * 255), (int) Math.round(color.getBlue() * 255));
    }

    private static Node getPlug(String fill, String stroke) {
        Group plug = new Group(
            getStroked("M6 1 V5 M10 1 V5", stroke, 1.6),
            getFilled("M3.5 5 H12.5 V8 Q12.5 12 8 12 Q3.5 12 3.5 8 Z", fill, stroke),
            getStroked("M8 12 V15.5", stroke, 1.8)
        );
        plug.setRotate(45);
        return plug;
    }

    /**
     * Circulo verde con un + abajo a la derecha: "nuevo".
     */
    private static Node getPlusBadge() {
        return new Group(
            getFilled("M15.8 12 A3.8 3.8 0 1 1 8.2 12 A3.8 3.8 0 1 1 15.8 12 Z", GREEN_DARK, WHITE),
            getStroked("M12 10 V14 M10 12 H14", WHITE, 1.4)
        );
    }

    private static Node[] getDatabase() {
        return new Node[]{
            getFilled("M2 4 V12 A6 2.6 0 0 0 14 12 V4 Z", BLUE, BLUE_DARK),
            getStroked("M2 8 A6 2.6 0 0 0 14 8", BLUE_LIGHT, 1),
            getFilled("M2 4 A6 2.4 0 1 0 14 4 A6 2.4 0 1 0 2 4 Z", "#64b5f6", BLUE_DARK),
        };
    }

    /**
     * Circulo verde con una flecha abajo a la derecha: hacia abajo, sacar (volcar a .sql); hacia arriba, meter (ejecutar
     * un .sql), como los iconos de SQLyog.
     */
    private static Node getArrowBadge(boolean isDown) {
        return new Group(
            getFilled("M15.8 12 A3.8 3.8 0 1 1 8.2 12 A3.8 3.8 0 1 1 15.8 12 Z", GREEN_DARK, WHITE),
            getStroked(isDown ? "M12 8.8 V15 M9.6 12.6 L12 15 L14.4 12.6" : "M12 15.2 V9 M9.6 11.4 L12 9 L14.4 11.4", WHITE, 1.5)
        );
    }

    private static Node[] getGrid(String headerColor) {
        return new Node[]{
            getFilled("M1.5 2.5 H14.5 V13.5 H1.5 Z", WHITE, SLATE),
            getFilled("M1.5 2.5 H14.5 V5.5 H1.5 Z", headerColor, SLATE),
            getStroked("M1.5 9.5 H14.5 M6 5.5 V13.5 M10 5.5 V13.5", "#90a4ae", 1),
        };
    }

    private static Node[] getSheet(String lineColor) {
        return new Node[]{
            getFilled("M3 1.5 H10 L13 4.5 V14.5 H3 Z", WHITE, SLATE),
            getStroked("M5 7 H11 M5 9.5 H11 M5 12 H9", lineColor, 1.2),
        };
    }

    private static Node getCircle(String fill, String stroke) {
        return getFilled("M14.5 8 A6.5 6.5 0 1 1 1.5 8 A6.5 6.5 0 1 1 14.5 8 Z", fill, stroke);
    }

    private static SVGPath getFilled(String content, String fill, String stroke) {
        SVGPath svgPath = new SVGPath();
        svgPath.setContent(content);
        svgPath.setFill(Color.web(fill));
        if (stroke != null) {
            svgPath.setStroke(Color.web(stroke));
            svgPath.setStrokeWidth(1);
            svgPath.setStrokeLineJoin(StrokeLineJoin.ROUND);
        }
        return svgPath;
    }

    private static SVGPath getStroked(String content, String stroke, double strokeWidth) {
        SVGPath svgPath = new SVGPath();
        svgPath.setContent(content);
        svgPath.setFill(null);
        svgPath.setStroke(Color.web(stroke));
        svgPath.setStrokeWidth(strokeWidth);
        svgPath.setStrokeLineCap(StrokeLineCap.ROUND);
        svgPath.setStrokeLineJoin(StrokeLineJoin.ROUND);
        return svgPath;
    }

    private static Text getText(String value, double fontSize, String fill, double x, double y) {
        Text text = new Text(x, y, value);
        text.setFont(Font.font("Georgia", FontWeight.BOLD, FontPosture.ITALIC, fontSize));
        text.setFill(Color.web(fill));
        return text;
    }

    private static Node[] append(Node[] nodes, Node extraNode) {
        Node[] allNodes = new Node[nodes.length + 1];
        System.arraycopy(nodes, 0, allNodes, 0, nodes.length);
        allNodes[nodes.length] = extraNode;
        return allNodes;
    }
}
