package modules.shared.infrastructure;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import javafx.application.Platform;
import javafx.scene.control.ComboBox;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import testcase.AbstractTestCase;

import modules.shared.infrastructure.components.combobox.FilterableComboBoxComponent;

/**
 * El selector de BD activa que tambien busca: lo escrito y confirmado elige la BD exacta o la primera que lo contenga;
 * si no coincide nada vuelve a la que habia, y lo que se fija desde fuera no cuenta como elegido.
 */
final class FilterableComboBoxComponentTest extends AbstractTestCase {

    @BeforeAll
    static void startJavaFx() {
        try {
            Platform.startup(() -> { });
        }
        catch (IllegalStateException e) {
            // ya arrancado por otra clase de test
        }
    }

    @Test
    void testTypedTextChoosesTheExactOrFirstMatchingItemAndOtherwiseRestores() throws Exception {
        ComboBox<String> comboBox = onFx(ComboBox::new);
        List<String> chosen = new ArrayList<>();
        FilterableComboBoxComponent filterableComboBoxComponent = onFx(() -> {
            FilterableComboBoxComponent component = FilterableComboBoxComponent.getInstance(comboBox);
            component.setOnChosen(chosen::add);
            component.setItems(List.of("crm", "db_mypromos_dev", "db_mypromos_prod"));
            component.showValue("crm");
            return component;
        });

        onFx(() -> {
            comboBox.setValue("PROMOS");
            return null;
        });
        onFx(() -> {
            comboBox.setValue("nada-parecido");
            return null;
        });
        onFx(() -> {
            filterableComboBoxComponent.showValue("crm");
            return null;
        });

        assertEquals(List.of("db_mypromos_dev"), chosen);
        assertEquals("crm", onFx(comboBox::getValue));
        assertEquals("crm", onFx(() -> comboBox.getEditor().getText()));
        assertEquals(3, onFx(() -> comboBox.getItems().size()));
    }

    private static <T> T onFx(Callable<T> work) throws Exception {
        CompletableFuture<T> future = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                future.complete(work.call());
            }
            catch (Throwable e) {
                future.completeExceptionally(e);
            }
        });
        return future.get(20, TimeUnit.SECONDS);
    }
}
