package modules.files.domain.services;

import java.util.ArrayList;
import java.util.List;

import modules.files.domain.enums.FilesFormatEnum;
import modules.files.domain.entities.FileTableEntity;

/**
 * Un CSV (o TSV) como tabla: la primera linea es la cabecera; campos entre comillas dobles con "" dentro y saltos de
 * linea permitidos (RFC 4180). El separador de un .csv se adivina por la cabecera (coma, punto y coma o tabulador).
 * Una fila mas corta se completa con NULL; una mas larga anade columnas.
 */
public final class CsvTableReader {

    private static final char QUOTE = '"';
    private static final List<Character> CANDIDATE_DELIMITERS = List.of(',', ';', '\t');

    private CsvTableReader() {
    }

    public static CsvTableReader getInstance() {
        return new CsvTableReader();
    }

    public FileTableEntity getTable(String text, String extension) {
        TextLayoutDetector textLayoutDetector = TextLayoutDetector.getInstance();
        String body = textLayoutDetector.getBody(text);
        char delimiter = FilesFormatEnum.EXTENSION_TSV.equals(extension) ? '\t' : getDelimiter(body);
        List<List<String>> records = getRecords(body, delimiter);

        List<String> header = records.isEmpty() ? List.of() : records.getFirst();
        int columnsCount = header.size();
        for (List<String> csvRecord : records) columnsCount = Math.max(columnsCount, csvRecord.size());
        List<String> rawNames = new ArrayList<>(header);
        while (rawNames.size() < columnsCount) rawNames.add("");
        List<String> columnNames = ColumnNamesBuilder.getInstance().getColumnNames(rawNames);

        List<List<String>> textRows = records.size() < 2 ? List.of() : records.subList(1, records.size());
        ColumnKindResolver columnKindResolver = ColumnKindResolver.getInstance();
        List<String> columnKinds = new ArrayList<>();
        for (int columnIndex = 0; columnIndex < columnsCount; columnIndex++) {
            List<String> columnValues = new ArrayList<>();
            for (List<String> textRow : textRows) columnValues.add(columnIndex < textRow.size() ? textRow.get(columnIndex) : null);
            columnKinds.add(columnKindResolver.getKind(columnValues));
        }
        List<List<Object>> rows = new ArrayList<>();
        for (List<String> textRow : textRows) {
            List<Object> row = new ArrayList<>();
            for (int columnIndex = 0; columnIndex < columnsCount; columnIndex++) {
                String value = columnIndex < textRow.size() ? textRow.get(columnIndex) : null;
                row.add(columnKindResolver.getValue(columnKinds.get(columnIndex), value));
            }
            rows.add(row);
        }
        return new FileTableEntity(
            FilesFormatEnum.CSV, String.valueOf(delimiter), textLayoutDetector.getLineSeparator(body), textLayoutDetector.hasBom(text),
            columnNames, columnKinds, List.of(), rows
        );
    }

    /**
     * El candidato que mas aparece en la primera linea (fuera de comillas); sin ninguno, coma.
     */
    private char getDelimiter(String body) {
        char bestDelimiter = ',';
        int bestCount = 0;
        for (char candidate : CANDIDATE_DELIMITERS) {
            int count = 0;
            boolean isQuoted = false;
            for (int i = 0; i < body.length(); i++) {
                char character = body.charAt(i);
                if (character == QUOTE) isQuoted = !isQuoted;
                if (!isQuoted && (character == '\n' || character == '\r')) break;
                if (!isQuoted && character == candidate) count++;
            }
            if (count > bestCount) {
                bestCount = count;
                bestDelimiter = candidate;
            }
        }
        return bestDelimiter;
    }

    private List<List<String>> getRecords(String body, char delimiter) {
        List<List<String>> records = new ArrayList<>();
        List<String> csvRecord = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean isQuoted = false;
        boolean isFieldStarted = false;
        int i = 0;
        while (i < body.length()) {
            char character = body.charAt(i);
            if (isQuoted) {
                if (character == QUOTE && i + 1 < body.length() && body.charAt(i + 1) == QUOTE) {
                    field.append(QUOTE);
                    i += 2;
                    continue;
                }
                if (character == QUOTE) isQuoted = false;
                else field.append(character);
                i++;
                continue;
            }
            if (character == QUOTE && field.isEmpty()) {
                isQuoted = true;
                isFieldStarted = true;
            }
            else if (character == delimiter) {
                csvRecord.add(field.toString());
                field.setLength(0);
                isFieldStarted = true;
            }
            else if (character == '\r' || character == '\n') {
                if (isFieldStarted || !field.isEmpty() || !csvRecord.isEmpty()) {
                    csvRecord.add(field.toString());
                    records.add(csvRecord);
                }
                csvRecord = new ArrayList<>();
                field.setLength(0);
                isFieldStarted = false;
                if (character == '\r' && i + 1 < body.length() && body.charAt(i + 1) == '\n') i++;
            }
            else {
                field.append(character);
                isFieldStarted = true;
            }
            i++;
        }
        if (isFieldStarted || !field.isEmpty() || !csvRecord.isEmpty()) {
            csvRecord.add(field.toString());
            records.add(csvRecord);
        }
        return records;
    }
}
