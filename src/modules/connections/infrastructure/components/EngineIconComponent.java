package modules.connections.infrastructure.components;

import java.util.Map;

import javafx.scene.Node;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.FillRule;
import javafx.scene.shape.SVGPath;

import modules.connections.domain.enums.DbEngineEnum;

/**
 * El motor de una conexion en el panel (16x16): la silueta de su mascota o logotipo (delfin de MySQL, elefante de
 * PostgreSQL, cubo de Redis, hoja de MongoDB, pluma de SQLite), de un solo color, el del texto (clase engine-icon en
 * styles.css y dark.css). Dibujo propio con formas, sin ficheros de imagen.
 */
public final class EngineIconComponent {

    private static final double SIZE = 16;
    private static final String STYLE_ENGINE_ICON = "engine-icon";

    private static final String DOLPHIN = "M1 7 C2.5 5.2 5 4 8 4.2 L10 2 L10.6 4.6 C12.8 5.4 14.3 7.2 15 9.5 L15.8 12.5"
        + " L14 11.2 L12.4 12.8 L13 10.2 C11.5 8 9 7 6.5 7 C5.5 7 4.8 7.4 4.2 7.9 L3.2 9.3 L3 7.6 C2.3 7.6 1.6 7.4 1 7 Z";
    private static final String ELEPHANT = "M6 2 C9 1 13 1.5 14.5 4 C15.5 6 15 9 13 10.5 V14.5 H11 V11.5 H9 V14.5 H7 V11"
        + " C5.5 10.5 4.5 9.5 4 8 C3.2 9.5 2.8 11.5 3.5 13 C3.9 13.8 4.8 13.6 4.6 12.8 L5.6 12.6 C6 14.6 3.4 15.6 2.4 13.6"
        + " C1.4 11.6 2 8 3.2 5.5 C3.8 3.8 4.8 2.5 6 2 Z M6.2 4.6 A0.8 0.8 0 1 0 6.21 4.6 Z"
        + " M8.6 3.6 C11 3.3 12.6 4.6 12.3 6.8 C12 8.6 10.5 9.2 9.2 8.8 C9.7 7 9.5 5.2 8.6 3.6 Z";
    private static final String CUBE = "M8 1 L15 4 L8 7 L1 4 Z M1 6.5 L8 9.5 L15 6.5 V8 L8 11 L1 8 Z"
        + " M1 10.5 L8 13.5 L15 10.5 V12 L8 15 L1 12 Z";
    private static final String LEAF = "M8 1 C11 4 12 7 11 10 C10.4 12 9 13 8.5 13.5 V15.5 H7.5 V13.5"
        + " C7 13 5.6 12 5 10 C4 7 5 4 8 1 Z";
    private static final String FEATHER = "M14 1 C9.5 2 5.5 6 4.5 11.5 L3 15 L4.6 15 L5.4 12.6 C9.5 12 12.8 8 14 1 Z";
    private static final String CYLINDER = "M2 3.5 C2 1.5 14 1.5 14 3.5 V12.5 C14 14.5 2 14.5 2 12.5 Z"
        + " M3.5 4 C3.5 5.3 12.5 5.3 12.5 4 C12.5 2.9 3.5 2.9 3.5 4 Z";
    private static final String FOLDER = "M1 3 H6 L7.5 4.5 H15 V14 H1 Z";
    private static final String UNKNOWN = "M8 1 A7 7 0 1 0 8.01 1 Z M8 3.5 A4.5 4.5 0 1 1 7.99 3.5 Z";

    private static final Map<String, String> SHAPES = Map.of(
        DbEngineEnum.MYSQL, DOLPHIN,
        DbEngineEnum.PGSQL, ELEPHANT,
        DbEngineEnum.MSSQL, CYLINDER,
        DbEngineEnum.SQLITE, FEATHER,
        DbEngineEnum.REDIS, CUBE,
        DbEngineEnum.MONGODB, LEAF,
        DbEngineEnum.FILES, FOLDER
    );

    private EngineIconComponent() {
    }

    public static EngineIconComponent getInstance() {
        return new EngineIconComponent();
    }

    /**
     * Un motor desconocido sale como un anillo.
     */
    public Node getIcon(String dbEngine) {
        SVGPath silhouette = new SVGPath();
        silhouette.setContent(SHAPES.getOrDefault(dbEngine, UNKNOWN));
        silhouette.setFillRule(FillRule.EVEN_ODD);
        silhouette.getStyleClass().add(STYLE_ENGINE_ICON);
        StackPane iconPane = new StackPane(silhouette);
        iconPane.setMinSize(SIZE, SIZE);
        iconPane.setPrefSize(SIZE, SIZE);
        iconPane.setMaxSize(SIZE, SIZE);
        return iconPane;
    }
}
