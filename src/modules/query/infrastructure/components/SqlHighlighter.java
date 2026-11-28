package modules.query.infrastructure.components;

import java.util.Collection;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

import modules.query.domain.enums.SqlKeywordsEnum;

/**
 * El resaltado del editor: palabras clave, cadenas, `identificadores`, numeros y comentarios. Cada tramo lleva una
 * clase CSS (sql-keyword, sql-string...) definida en styles.css.
 */
public final class SqlHighlighter {

    private static final Pattern SQL_PATTERN = Pattern.compile(
        "(?<COMMENT>--[ \\t][^\\n]*|--$|#[^\\n]*|/\\*(?s:.*?)(?:\\*/|\\z))"
            + "|(?<STRING>'(?:[^'\\\\]|\\\\.|'')*'?|\"(?:[^\"\\\\]|\\\\.|\"\")*\"?)"
            + "|(?<IDENTIFIER>`[^`]*`?)"
            + "|(?<KEYWORD>\\b(?:" + String.join("|", SqlKeywordsEnum.KEYWORDS) + ")\\b)"
            + "|(?<NUMBER>\\b\\d+(?:\\.\\d+)?\\b)",
        Pattern.CASE_INSENSITIVE | Pattern.MULTILINE
    );

    private SqlHighlighter() {
    }

    public static SqlHighlighter getInstance() {
        return new SqlHighlighter();
    }

    public StyleSpans<Collection<String>> getStyleSpans(String text) {
        Matcher matcher = SQL_PATTERN.matcher(text);
        StyleSpansBuilder<Collection<String>> styleSpansBuilder = new StyleSpansBuilder<>();
        int lastEnd = 0;
        while (matcher.find()) {
            styleSpansBuilder.add(Collections.emptyList(), matcher.start() - lastEnd);
            styleSpansBuilder.add(Collections.singleton(getStyleClass(matcher)), matcher.end() - matcher.start());
            lastEnd = matcher.end();
        }
        styleSpansBuilder.add(Collections.emptyList(), text.length() - lastEnd);
        return styleSpansBuilder.create();
    }

    private String getStyleClass(Matcher matcher) {
        if (matcher.group("COMMENT") != null) return "sql-comment";
        if (matcher.group("STRING") != null) return "sql-string";
        if (matcher.group("IDENTIFIER") != null) return "sql-identifier";
        if (matcher.group("KEYWORD") != null) return "sql-keyword";
        return "sql-number";
    }
}
