package modules.query.domain.enums;

import java.util.List;
import java.util.Map;

/**
 * Formatos de "Exportar como..." (feature 9): el valor es la extension del fichero, salvo Excel XML (SpreadsheetML de
 * Excel 2003), que se guarda como .xml.
 */
public final class ResultExportFormatEnum {

    public static final String CSV = "csv";
    public static final String JSON = "json";
    public static final String HTML = "html";
    public static final String XML = "xml";
    public static final String EXCEL_XML = "excelxml";
    public static final String SQL = "sql";

    public static final List<String> VALUES = List.of(CSV, JSON, HTML, XML, EXCEL_XML, SQL);

    /**
     * Lo que se ve en el dialogo.
     */
    public static final Map<String, String> LABELS = Map.of(
        CSV, "CSV",
        JSON, "JSON",
        HTML, "HTML",
        XML, "XML",
        EXCEL_XML, "Excel XML",
        SQL, "SQL"
    );

    private ResultExportFormatEnum() {
    }
}
