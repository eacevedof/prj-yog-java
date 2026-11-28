package modules.query.infrastructure.components;

import java.util.ArrayList;
import java.util.List;

/**
 * El resultado en texto plano, como el cliente mysql: una tabla con bordes +---+ que se puede copiar y pegar en un
 * ticket o un chat. Las celdas largas se cortan a MAX_CELL_WIDTH y los saltos de linea se ven como espacios.
 */
public final class ResultTextFormatter {

    public static final String NULL_TEXT = "(NULL)";
    private static final int MAX_CELL_WIDTH = 60;
    private static final String ELLIPSIS = "...";

    private ResultTextFormatter() {
    }

    public static ResultTextFormatter getInstance() {
        return new ResultTextFormatter();
    }

    public String getTable(List<String> columnNames, List<List<String>> rows) {
        int columnCount = columnNames.size();
        int[] widths = new int[columnCount];
        for (int i = 0; i < columnCount; i++) {
            widths[i] = getCellText(columnNames.get(i)).length();
        }
        List<List<String>> cellTexts = new ArrayList<>();
        for (List<String> row : rows) {
            List<String> rowTexts = new ArrayList<>(columnCount);
            for (int i = 0; i < columnCount; i++) {
                String cellText = getCellText(i < row.size() ? row.get(i) : null);
                widths[i] = Math.max(widths[i], cellText.length());
                rowTexts.add(cellText);
            }
            cellTexts.add(rowTexts);
        }

        String separator = getSeparator(widths);
        StringBuilder table = new StringBuilder();
        table.append(separator).append('\n');
        table.append(getRow(columnNames.stream().map(this::getCellText).toList(), widths)).append('\n');
        table.append(separator).append('\n');
        for (List<String> rowTexts : cellTexts) {
            table.append(getRow(rowTexts, widths)).append('\n');
        }
        table.append(separator).append('\n');
        return table.toString();
    }

    private String getCellText(String value) {
        if (value == null) return NULL_TEXT;
        String oneLine = value.replace("\r\n", " ").replace('\n', ' ').replace('\r', ' ').replace('\t', ' ');
        if (oneLine.length() <= MAX_CELL_WIDTH) return oneLine;
        return oneLine.substring(0, MAX_CELL_WIDTH - ELLIPSIS.length()) + ELLIPSIS;
    }

    private String getSeparator(int[] widths) {
        StringBuilder separator = new StringBuilder("+");
        for (int width : widths) {
            separator.append("-".repeat(width + 2)).append('+');
        }
        return separator.toString();
    }

    private String getRow(List<String> cellTexts, int[] widths) {
        StringBuilder row = new StringBuilder("|");
        for (int i = 0; i < widths.length; i++) {
            String cellText = cellTexts.get(i);
            row.append(' ').append(cellText).append(" ".repeat(widths[i] - cellText.length())).append(" |");
        }
        return row.toString();
    }
}
