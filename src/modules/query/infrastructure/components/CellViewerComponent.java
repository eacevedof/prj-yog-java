package modules.query.infrastructure.components;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import modules.shared.infrastructure.components.icons.AppIconEnum;
import modules.shared.infrastructure.components.icons.AppIcon;
import modules.shared.infrastructure.components.theme.ThemeComponent;

import static core.config.Global.__;

import modules.query.domain.services.JsonFormatter;

/**
 * El visor de una celda (feature 43): el valor entero, con sus saltos de linea, y si es JSON formateado (se puede ver
 * tal cual desmarcando "JSON formateado"). Copiar copia lo que se ve. Esc cierra.
 */
public final class CellViewerComponent {

    private CellViewerComponent() {
    }

    public static CellViewerComponent getInstance() {
        return new CellViewerComponent();
    }

    public void show(Window owner, String columnName, String value) {
        String prettyJson = JsonFormatter.getInstance().getPrettyJson(value);
        TextArea valueTextArea = new TextArea(prettyJson != null ? prettyJson : value == null ? "" : value);
        valueTextArea.setEditable(false);
        valueTextArea.setWrapText(prettyJson == null);
        valueTextArea.getStyleClass().add("result-text");

        CheckBox jsonCheckBox = new CheckBox(__("JSON formateado"));
        jsonCheckBox.setSelected(prettyJson != null);
        jsonCheckBox.setVisible(prettyJson != null);
        jsonCheckBox.selectedProperty().addListener((observable, wasSelected, isSelected) ->
            valueTextArea.setText(isSelected ? prettyJson : value)
        );

        Label infoLabel = new Label(value == null ? "NULL" : __("{0} caracteres", value.length()));
        infoLabel.getStyleClass().add("query-status");
        Button copyButton = new Button(__("Copiar"), AppIcon.of(AppIconEnum.COPY));
        copyButton.setOnAction(actionEvent -> {
            ClipboardContent clipboardContent = new ClipboardContent();
            clipboardContent.putString(valueTextArea.getText());
            Clipboard.getSystemClipboard().setContent(clipboardContent);
        });
        Button closeButton = new Button(__("Cerrar"), AppIcon.of(AppIconEnum.CLOSE));
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox buttonBar = new HBox(8, jsonCheckBox, infoLabel, spacer, copyButton, closeButton);
        buttonBar.setAlignment(Pos.CENTER_LEFT);
        buttonBar.setPadding(new Insets(8));

        BorderPane root = new BorderPane(valueTextArea);
        root.setBottom(buttonBar);
        Scene scene = new Scene(root, 640, 420);
        ThemeComponent.getInstance().applyTo(scene);
        scene.setOnKeyPressed(keyEvent -> {
            if (keyEvent.getCode() == KeyCode.ESCAPE) ((Stage) scene.getWindow()).close();
        });

        Stage stage = new Stage();
        stage.initOwner(owner);
        stage.initModality(Modality.NONE);
        stage.setTitle(__("Valor de {0}", columnName));
        stage.setScene(scene);
        closeButton.setOnAction(actionEvent -> stage.close());
        stage.show();
    }
}
