package modules.query.infrastructure.components;

import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.stage.Popup;

import org.fxmisc.richtext.CodeArea;

import modules.shared.domain.services.SqlIdentifierQuoter;
import modules.shared.infrastructure.components.theme.ThemeComponent;

import static core.config.Global.__;

import modules.query.domain.enums.SqlKeywordsEnum;
import modules.query.domain.entities.SqlCompletionContextEntity;
import modules.query.domain.entities.SqlStatementEntity;
import modules.query.domain.services.SqlCompletionAnalyzer;
import modules.query.domain.services.SqlStatementSplitter;

/**
 * El autocompletado del editor (features 14 y 41): Ctrl+Space lo abre y escribir un "." tambien. Mientras esta abierto
 * se refiltra con cada tecla; flechas para elegir, Enter o Tab para aceptar, Esc para cerrar. Cuando llegan tablas o
 * columnas del servidor (SchemaCacheComponent), la lista se rehace sola.
 */
public final class CompletionPopupComponent {

    private static final Pattern PLAIN_IDENTIFIER_PATTERN = Pattern.compile("[A-Za-z_$][\\w$]*");
    private static final int VISIBLE_ROWS = 12;

    private final CodeArea sqlCodeArea;
    private final Supplier<SchemaCacheComponent> schemaCacheSupplier;
    private final Supplier<String> activeDatabaseSupplier;
    private final Supplier<String> dbEngineSupplier;
    private final Popup popup = new Popup();
    private final ListView<CompletionProvider.Candidate> candidatesListView = new ListView<>();
    private SqlCompletionContextEntity currentContext;

    public CompletionPopupComponent(
        CodeArea sqlCodeArea,
        Supplier<SchemaCacheComponent> schemaCacheSupplier,
        Supplier<String> activeDatabaseSupplier,
        Supplier<String> dbEngineSupplier
    ) {
        this.sqlCodeArea = sqlCodeArea;
        this.schemaCacheSupplier = schemaCacheSupplier;
        this.activeDatabaseSupplier = activeDatabaseSupplier;
        this.dbEngineSupplier = dbEngineSupplier;

        candidatesListView.getStyleClass().add("completion-list");
        candidatesListView.setPrefWidth(420);
        candidatesListView.setFocusTraversable(false);
        candidatesListView.setCellFactory(listView -> new CandidateCell());
        candidatesListView.setOnMouseClicked(mouseEvent -> {
            if (mouseEvent.getButton() == MouseButton.PRIMARY && mouseEvent.getClickCount() == 2) accept();
        });
        popup.getContent().add(candidatesListView);
        popup.setAutoHide(true);
    }

    public void install() {
        sqlCodeArea.addEventFilter(KeyEvent.KEY_PRESSED, this::onKeyPressed);
        sqlCodeArea.addEventHandler(KeyEvent.KEY_TYPED, keyEvent -> {
            if (".".equals(keyEvent.getCharacter())) Platform.runLater(this::show);
        });
        sqlCodeArea.textProperty().addListener((observable, oldText, newText) -> {
            if (popup.isShowing()) Platform.runLater(this::refresh);
        });
        sqlCodeArea.focusedProperty().addListener((observable, wasFocused, isFocused) -> {
            if (!isFocused) popup.hide();
        });
    }

    public boolean isShowing() {
        return popup.isShowing();
    }

    public void show() {
        SchemaCacheComponent schemaCache = schemaCacheSupplier.get();
        if (schemaCache == null) return;
        schemaCache.setOnLoaded(() -> {
            if (popup.isShowing()) refresh();
        });
        candidatesListView.getStylesheets().setAll(ThemeComponent.getInstance().getStylesheets());
        if (!refresh()) return;
        sqlCodeArea.getCaretBounds().ifPresent(caretBounds -> popup.show(sqlCodeArea, caretBounds.getMinX(), caretBounds.getMaxY() + 2));
    }

    /**
     * false (y se cierra) si no queda ningun candidato.
     */
    private boolean refresh() {
        SchemaCacheComponent schemaCache = schemaCacheSupplier.get();
        if (schemaCache == null) return false;
        currentContext = getContext();
        List<CompletionProvider.Candidate> candidates = CompletionProvider.getInstance().getCandidates(
            currentContext, activeDatabaseSupplier.get(), schemaCache
        );
        if (candidates.isEmpty()) {
            popup.hide();
            return false;
        }
        candidatesListView.getItems().setAll(candidates);
        candidatesListView.getSelectionModel().selectFirst();
        candidatesListView.scrollTo(0);
        candidatesListView.setPrefHeight(Math.min(candidates.size(), VISIBLE_ROWS) * 24 + 4);
        return true;
    }

    /**
     * La sentencia donde esta el cursor; si el cursor ya paso su ";", se empieza una nueva (contexto vacio).
     */
    private SqlCompletionContextEntity getContext() {
        String text = sqlCodeArea.getText();
        int caret = sqlCodeArea.getCaretPosition();
        SqlStatementEntity statement = SqlStatementSplitter.getInstance(dbEngineSupplier.get()).getStatementAt(text, caret);
        SqlCompletionAnalyzer sqlCompletionAnalyzer = SqlCompletionAnalyzer.getInstance();
        if (statement == null || caret < statement.getStartIndex() || caret > statement.getEndIndex()
            || (caret == statement.getEndIndex() && statement.getEndIndex() > statement.getStartIndex() + statement.getSql().length())) {
            int lineStart = text.lastIndexOf('\n', Math.max(0, caret - 1)) + 1;
            String sinceLineStart = text.substring(Math.min(lineStart, caret), caret);
            int semicolon = sinceLineStart.lastIndexOf(';');
            String region = semicolon == -1 ? sinceLineStart : sinceLineStart.substring(semicolon + 1);
            return sqlCompletionAnalyzer.getContext(region, region.length());
        }
        int start = statement.getStartIndex();
        int end = Math.max(caret, start + statement.getSql().length());
        return sqlCompletionAnalyzer.getContext(text.substring(start, end), caret - start);
    }

    private void onKeyPressed(KeyEvent keyEvent) {
        if (keyEvent.getCode() == KeyCode.SPACE && keyEvent.isShortcutDown()) {
            show();
            keyEvent.consume();
            return;
        }
        if (!popup.isShowing()) return;
        switch (keyEvent.getCode()) {
            case UP -> moveSelection(-1);
            case DOWN -> moveSelection(1);
            case PAGE_UP -> moveSelection(-VISIBLE_ROWS);
            case PAGE_DOWN -> moveSelection(VISIBLE_ROWS);
            case ENTER, TAB -> accept();
            case ESCAPE -> popup.hide();
            case LEFT, RIGHT, HOME, END -> {
                popup.hide();
                return;
            }
            default -> {
                return;
            }
        }
        keyEvent.consume();
    }

    private void moveSelection(int delta) {
        int size = candidatesListView.getItems().size();
        if (size == 0) return;
        int index = Math.max(0, Math.min(size - 1, candidatesListView.getSelectionModel().getSelectedIndex() + delta));
        candidatesListView.getSelectionModel().select(index);
        candidatesListView.scrollTo(Math.max(0, index - VISIBLE_ROWS / 2));
    }

    /**
     * Sustituye lo escrito por el candidato; los nombres raros (espacios, guiones, palabras reservadas) van entre
     * comillas invertidas.
     */
    private void accept() {
        CompletionProvider.Candidate candidate = candidatesListView.getSelectionModel().getSelectedItem();
        popup.hide();
        if (candidate == null || currentContext == null) return;
        String text = CompletionProvider.KIND_KEYWORD.equals(candidate.kind()) ? candidate.text() : getInsertableName(candidate.text());
        int caret = sqlCodeArea.getCaretPosition();
        sqlCodeArea.replaceText(Math.max(0, caret - currentContext.getRawPrefixLength()), caret, text);
    }

    private String getInsertableName(String name) {
        boolean isPlain = PLAIN_IDENTIFIER_PATTERN.matcher(name).matches() && !SqlKeywordsEnum.KEYWORDS.contains(name.toLowerCase(Locale.ROOT));
        return isPlain ? name : SqlIdentifierQuoter.getInstance(dbEngineSupplier.get()).getQuoted(name);
    }

    /**
     * nombre a la izquierda; tipo y tabla en gris a la derecha.
     */
    private static final class CandidateCell extends ListCell<CompletionProvider.Candidate> {

        @Override
        protected void updateItem(CompletionProvider.Candidate candidate, boolean isEmpty) {
            super.updateItem(candidate, isEmpty);
            if (isEmpty || candidate == null) {
                setGraphic(null);
                setText(null);
                return;
            }
            Label nameLabel = new Label(candidate.text());
            nameLabel.setMnemonicParsing(false);
            Label kindLabel = new Label(candidate.detail().isEmpty() ? __(candidate.kind()) : __(candidate.kind()) + " · " + candidate.detail());
            kindLabel.setMnemonicParsing(false);
            kindLabel.getStyleClass().add("completion-kind");
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            setText(null);
            setGraphic(new HBox(12, nameLabel, spacer, kindLabel));
        }
    }
}
