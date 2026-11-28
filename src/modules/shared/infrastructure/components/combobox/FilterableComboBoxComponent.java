package modules.shared.infrastructure.components.combobox;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import javafx.application.Platform;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;

/**
 * Un desplegable de texto que tambien busca: se escribe en el (fondo de caja de texto) y la lista se queda con lo que
 * contiene lo escrito, sin distinguir mayusculas. Enter, o elegir en la lista, da por elegido el valor si existe o, si
 * no, el primero que coincida; si no coincide nada, vuelve al que habia. Lo que se fija desde fuera con showValue no
 * cuenta como elegido.
 */
public final class FilterableComboBoxComponent {

    private final ComboBox<String> comboBox;
    private final List<String> allItems = new ArrayList<>();
    private Consumer<String> onChosen = chosen -> {};
    private String shownValue;
    private boolean isUpdating;

    private FilterableComboBoxComponent(ComboBox<String> comboBox) {
        this.comboBox = comboBox;
        comboBox.setEditable(true);
        TextField editor = comboBox.getEditor();
        editor.textProperty().addListener((observable, oldText, newText) -> filterItems(newText));
        editor.focusedProperty().addListener((observable, wasFocused, isFocused) -> {
            if (!isFocused) Platform.runLater(this::restoreShownValue);
        });
        comboBox.valueProperty().addListener((observable, oldValue, newValue) -> choose(newValue));
    }

    public static FilterableComboBoxComponent getInstance(ComboBox<String> comboBox) {
        return new FilterableComboBoxComponent(comboBox);
    }

    public void setOnChosen(Consumer<String> onChosen) {
        this.onChosen = onChosen;
    }

    public void setItems(List<String> items) {
        allItems.clear();
        allItems.addAll(items);
        isUpdating = true;
        comboBox.getItems().setAll(items);
        isUpdating = false;
        restoreShownValue();
    }

    /**
     * El valor que se ensena sin avisar a setOnChosen (null = ninguno).
     */
    public void showValue(String value) {
        shownValue = value;
        restoreShownValue();
    }

    private void filterItems(String text) {
        if (isUpdating || !comboBox.getEditor().isFocused()) return;
        String needle = text == null ? "" : text.toLowerCase(Locale.ROOT);
        List<String> matches = allItems.stream().filter(item -> item.toLowerCase(Locale.ROOT).contains(needle)).toList();
        TextField editor = comboBox.getEditor();
        int caretPosition = editor.getCaretPosition();
        isUpdating = true;
        // cambiar la lista puede vaciar la seleccion y con ella el texto: se deja como estaba
        comboBox.getItems().setAll(matches);
        editor.setText(text);
        editor.positionCaret(caretPosition);
        isUpdating = false;
        if (matches.isEmpty()) {
            comboBox.hide();
            return;
        }
        comboBox.hide();
        comboBox.show();
    }

    private void choose(String value) {
        if (isUpdating || value == null) return;
        String chosen = getChosen(value);
        if (chosen == null || chosen.equals(shownValue)) {
            Platform.runLater(this::restoreShownValue);
            return;
        }
        shownValue = chosen;
        Platform.runLater(this::restoreShownValue);
        onChosen.accept(chosen);
    }

    private String getChosen(String value) {
        if (allItems.contains(value)) return value;
        String needle = value.toLowerCase(Locale.ROOT);
        return allItems.stream().filter(item -> item.toLowerCase(Locale.ROOT).contains(needle)).findFirst().orElse(null);
    }

    private void restoreShownValue() {
        isUpdating = true;
        if (comboBox.getItems().size() != allItems.size()) comboBox.getItems().setAll(allItems);
        comboBox.setValue(shownValue);
        comboBox.getEditor().setText(shownValue == null ? "" : shownValue);
        isUpdating = false;
    }
}
