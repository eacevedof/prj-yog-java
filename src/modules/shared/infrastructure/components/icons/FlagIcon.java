package modules.shared.infrastructure.components.icons;

import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.StrokeLineCap;

import modules.shared.domain.enums.LanguageEnum;

/**
 * La bandera de cada idioma del selector (22x15), dibujada con formas: Windows no pinta los emoji de banderas.
 * Ingles: Reino Unido; español: España; neerlandes: Paises Bajos; papiamento: Curazao.
 */
public final class FlagIcon {

    private static final double WIDTH = 22;
    private static final double HEIGHT = 15;

    private FlagIcon() {
    }

    public static Node of(String language) {
        Group flag = switch (language) {
            case LanguageEnum.ES -> getSpain();
            case LanguageEnum.NL -> getNetherlands();
            case LanguageEnum.PAP -> getCuracao();
            default -> getUnitedKingdom();
        };
        Rectangle clip = new Rectangle(WIDTH, HEIGHT);
        clip.setArcWidth(3);
        clip.setArcHeight(3);
        flag.setClip(clip);
        Rectangle border = getRect(0, 0, WIDTH, HEIGHT, Color.TRANSPARENT);
        border.setStroke(Color.web("#000000", 0.25));
        border.setArcWidth(3);
        border.setArcHeight(3);
        return new Group(flag, border);
    }

    private static Group getSpain() {
        return new Group(
            getRect(0, 0, WIDTH, HEIGHT, Color.web("#c60b1e")),
            getRect(0, HEIGHT / 4, WIDTH, HEIGHT / 2, Color.web("#ffc400"))
        );
    }

    private static Group getNetherlands() {
        return new Group(
            getRect(0, 0, WIDTH, HEIGHT / 3, Color.web("#ae1c28")),
            getRect(0, HEIGHT / 3, WIDTH, HEIGHT / 3, Color.WHITE),
            getRect(0, HEIGHT * 2 / 3, WIDTH, HEIGHT / 3 + 0.5, Color.web("#21468b"))
        );
    }

    /**
     * Azul con una franja amarilla por debajo del centro y dos estrellas blancas arriba a la izquierda.
     */
    private static Group getCuracao() {
        return new Group(
            getRect(0, 0, WIDTH, HEIGHT, Color.web("#002b7f")),
            getRect(0, HEIGHT * 5 / 8, WIDTH, HEIGHT / 8, Color.web("#f9e814")),
            getStar(3.2, 3.0, 1.6),
            getStar(6.6, 5.6, 2.2)
        );
    }

    private static Group getUnitedKingdom() {
        Group flag = new Group(getRect(0, 0, WIDTH, HEIGHT, Color.web("#012169")));
        flag.getChildren().addAll(getStripe(0, 0, WIDTH, HEIGHT, 3, Color.WHITE), getStripe(0, HEIGHT, WIDTH, 0, 3, Color.WHITE));
        flag.getChildren().addAll(getStripe(0, 0, WIDTH, HEIGHT, 1, Color.web("#c8102e")), getStripe(0, HEIGHT, WIDTH, 0, 1, Color.web("#c8102e")));
        flag.getChildren().addAll(
            getRect(WIDTH / 2 - 2.5, 0, 5, HEIGHT, Color.WHITE),
            getRect(0, HEIGHT / 2 - 2.5, WIDTH, 5, Color.WHITE),
            getRect(WIDTH / 2 - 1.5, 0, 3, HEIGHT, Color.web("#c8102e")),
            getRect(0, HEIGHT / 2 - 1.5, WIDTH, 3, Color.web("#c8102e"))
        );
        return flag;
    }

    private static Rectangle getRect(double x, double y, double width, double height, Color color) {
        Rectangle rect = new Rectangle(x, y, width, height);
        rect.setFill(color);
        return rect;
    }

    private static Line getStripe(double startX, double startY, double endX, double endY, double width, Color color) {
        Line stripe = new Line(startX, startY, endX, endY);
        stripe.setStroke(color);
        stripe.setStrokeWidth(width);
        stripe.setStrokeLineCap(StrokeLineCap.BUTT);
        return stripe;
    }

    /**
     * Estrella de cinco puntas blanca, centrada en (centerX, centerY).
     */
    private static SVGPath getStar(double centerX, double centerY, double radius) {
        StringBuilder path = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            double pointRadius = i % 2 == 0 ? radius : radius * 0.42;
            double angle = Math.toRadians(-90 + i * 36);
            path.append(i == 0 ? "M" : "L")
                .append(String.format(java.util.Locale.ROOT, "%.2f %.2f ", centerX + pointRadius * Math.cos(angle), centerY + pointRadius * Math.sin(angle)));
        }
        SVGPath star = new SVGPath();
        star.setContent(path.append("Z").toString());
        star.setFill(Color.WHITE);
        return star;
    }
}
