package modules.files.domain.services;

import java.util.ArrayList;
import java.util.List;

import modules.files.domain.enums.FilesConfigEnum;
import modules.files.domain.entities.FileTableEntity;

/**
 * La tabla de vuelta a CSV como estaba: mismo separador, mismos saltos de linea y BOM si lo tenia. Un campo va entre
 * comillas solo si lleva el separador, comillas o un salto; NULL se escribe vacio.
 */
public final class CsvTableWriter {

    private static final String QUOTE = "\"";

    private CsvTableWriter() {
    }

    public static CsvTableWriter getInstance() {
        return new CsvTableWriter();
    }

    public String getText(FileTableEntity fileTableEntity) {
        String delimiter = fileTableEntity.getCsvDelimiter();
        String lineSeparator = fileTableEntity.getLineSeparator();
        ColumnKindResolver columnKindResolver = ColumnKindResolver.getInstance();
        StringBuilder text = new StringBuilder();
        if (fileTableEntity.hasBom()) text.append(FilesConfigEnum.UTF8_BOM);
        text.append(getLine(fileTableEntity.getColumnNames(), delimiter)).append(lineSeparator);
        for (List<Object> row : fileTableEntity.getRows()) {
            List<String> fields = new ArrayList<>();
            for (Object value : row) fields.add(columnKindResolver.getText(value));
            text.append(getLine(fields, delimiter)).append(lineSeparator);
        }
        return text.toString();
    }

    private String getLine(List<String> fields, String delimiter) {
        List<String> quotedFields = new ArrayList<>();
        for (String field : fields) quotedFields.add(getQuoted(field, delimiter));
        return String.join(delimiter, quotedFields);
    }

    private String getQuoted(String field, String delimiter) {
        boolean isQuoteNeeded = field.contains(delimiter) || field.contains(QUOTE) || field.contains("\n") || field.contains("\r");
        return isQuoteNeeded ? QUOTE + field.replace(QUOTE, QUOTE + QUOTE) + QUOTE : field;
    }
}
