package modules.query.domain.services;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import modules.shared.domain.services.SqlIdentifierQuoter;
import modules.shared.domain.services.SqlLiteralQuoter;

import modules.query.domain.enums.ResultExportFormatEnum;
import modules.query.domain.entities.ResultExportOptionsEntity;

/**
 * Un resultado (nombres de columna y filas de texto, null = NULL) pasado a CSV, JSON, HTML, XML, Excel XML o INSERTs
 * de SQL (feature 9, dialogo "Exportar como"). Sin ficheros: devuelve el texto. Las opciones (separadores de CSV,
 * decimales de Excel, filas por INSERT...) llegan en ResultExportOptionsEntity.
 */
public final class ResultExportFormatter {

    private static final String NEW_LINE = "\n";
    // un numero "de verdad" para Excel: sin ceros a la izquierda (un codigo postal 08001 es texto) y sin perder precision
    private static final Pattern EXCEL_NUMBER_PATTERN = Pattern.compile("-?(?:0|[1-9]\\d{0,14})(?:\\.\\d{1,15})?");
    private static final Pattern EXCEL_SHEET_FORBIDDEN_PATTERN = Pattern.compile("[\\[\\]:*?/\\\\]");
    private static final int EXCEL_SHEET_NAME_MAX = 31;
    private static final int EXCEL_PIXELS_PER_CHAR = 7;

    private final SqlIdentifierQuoter sqlIdentifierQuoter;
    private final SqlLiteralQuoter sqlLiteralQuoter;

    private ResultExportFormatter() {
        this.sqlIdentifierQuoter = SqlIdentifierQuoter.getInstance();
        this.sqlLiteralQuoter = SqlLiteralQuoter.getInstance();
    }

    public static ResultExportFormatter getInstance() {
        return new ResultExportFormatter();
    }

    /**
     * Con las opciones por defecto (CSV con comas y comillas dobles, cabecera, NULL como \N, un INSERT por fila).
     * tableName: la tabla de los INSERT del formato SQL y la hoja de Excel.
     * @throws IllegalArgumentException si el formato no es de ResultExportFormatEnum
     */
    public String getFormatted(String format, String tableName, List<String> columnNames, List<List<String>> rows) {
        return getFormatted(format, columnNames, rows, ResultExportOptionsEntity.fromPrimitives(Map.of("tableName", tableName)));
    }

    /**
     * @throws IllegalArgumentException si el formato no es de ResultExportFormatEnum
     */
    public String getFormatted(String format, List<String> columnNames, List<List<String>> rows, ResultExportOptionsEntity options) {
        return switch (format) {
            case ResultExportFormatEnum.CSV -> getCsv(columnNames, rows, options);
            case ResultExportFormatEnum.JSON -> getJson(columnNames, rows);
            case ResultExportFormatEnum.XML -> getXml(columnNames, rows, options);
            case ResultExportFormatEnum.HTML -> getHtml(columnNames, rows, options);
            case ResultExportFormatEnum.EXCEL_XML -> getExcelXml(columnNames, rows, options);
            case ResultExportFormatEnum.SQL -> getSql(columnNames, rows, options);
            default -> throw new IllegalArgumentException("Unknown export format " + format);
        };
    }

    /**
     * Como el CSV de SQLyog: los campos van entre comillas solo si hace falta (llevan el separador, la comilla, un
     * salto o el escape); NULL sale con su texto (\N) y sin comillas.
     */
    private String getCsv(List<String> columnNames, List<List<String>> rows, ResultExportOptionsEntity options) {
        StringBuilder csv = new StringBuilder();
        if (options.isHeaderIncluded()) {
            List<String> header = new ArrayList<>();
            for (String columnName : columnNames) header.add(getCsvCell(columnName, options));
            csv.append(String.join(options.getFieldsTerminatedBy(), header)).append(options.getLinesTerminatedBy());
        }
        for (List<String> row : rows) {
            List<String> cells = new ArrayList<>();
            for (int i = 0; i < columnNames.size(); i++) {
                String value = i < row.size() ? row.get(i) : null;
                cells.add(value == null ? options.getNullReplacedBy() : getCsvCell(value, options));
            }
            csv.append(String.join(options.getFieldsTerminatedBy(), cells)).append(options.getLinesTerminatedBy());
        }
        return csv.toString();
    }

    private String getCsvCell(String value, ResultExportOptionsEntity options) {
        String enclosedBy = options.getEnclosedBy();
        String escapedBy = options.getEscapedBy();
        String fieldsTerminatedBy = options.getFieldsTerminatedBy();
        if (enclosedBy.isEmpty()) {
            // sin comillas solo queda escapar lo que romperia la linea
            if (escapedBy.isEmpty()) return value;
            String escaped = value.replace(escapedBy, escapedBy + escapedBy);
            if (!fieldsTerminatedBy.isEmpty()) escaped = escaped.replace(fieldsTerminatedBy, escapedBy + fieldsTerminatedBy);
            return escaped.replace("\n", escapedBy + "\n").replace("\r", escapedBy + "\r");
        }
        boolean isEscapeOwn = !escapedBy.isEmpty() && !escapedBy.equals(enclosedBy);
        boolean isEnclosed = (!fieldsTerminatedBy.isEmpty() && value.contains(fieldsTerminatedBy))
            || value.contains(enclosedBy)
            || value.contains("\n")
            || value.contains("\r")
            || (!options.getLinesTerminatedBy().isEmpty() && value.contains(options.getLinesTerminatedBy()))
            || (isEscapeOwn && value.contains(escapedBy));
        if (!isEnclosed) return value;
        String escaped = isEscapeOwn
            ? value.replace(escapedBy, escapedBy + escapedBy).replace(enclosedBy, escapedBy + enclosedBy)
            : value.replace(enclosedBy, enclosedBy + enclosedBy);
        return enclosedBy + escaped + enclosedBy;
    }

    private String getJson(List<String> columnNames, List<List<String>> rows) {
        StringBuilder json = new StringBuilder("[");
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            json.append(rowIndex == 0 ? NEW_LINE : "," + NEW_LINE).append("  {");
            List<String> row = rows.get(rowIndex);
            for (int i = 0; i < columnNames.size(); i++) {
                String value = i < row.size() ? row.get(i) : null;
                json.append(i == 0 ? "" : ", ")
                    .append(getJsonString(columnNames.get(i)))
                    .append(": ")
                    .append(value == null ? "null" : getJsonString(value));
            }
            json.append("}");
        }
        return json.append(rows.isEmpty() ? "]" : NEW_LINE + "]").append(NEW_LINE).toString();
    }

    private String getJsonString(String value) {
        StringBuilder escaped = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            switch (current) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (current < 0x20) escaped.append(String.format("\\u%04x", (int) current));
                    else escaped.append(current);
                }
            }
        }
        return escaped.append("\"").toString();
    }

    /**
     * Un <row> por fila y un <field name="columna"> por celda; NULL lleva xsi:nil="true".
     */
    private String getXml(List<String> columnNames, List<List<String>> rows, ResultExportOptionsEntity options) {
        StringBuilder xml = new StringBuilder(getXmlDeclaration(options)).append(NEW_LINE)
            .append("<resultset xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">").append(NEW_LINE);
        for (List<String> row : rows) {
            xml.append("  <row>").append(NEW_LINE);
            for (int i = 0; i < columnNames.size(); i++) {
                String value = i < row.size() ? row.get(i) : null;
                xml.append("    <field name=\"").append(getXmlEscaped(columnNames.get(i))).append("\"");
                xml.append(value == null ? " xsi:nil=\"true\"/>" : ">" + getXmlEscaped(value) + "</field>").append(NEW_LINE);
            }
            xml.append("  </row>").append(NEW_LINE);
        }
        return xml.append("</resultset>").append(NEW_LINE).toString();
    }

    private String getXmlDeclaration(ResultExportOptionsEntity options) {
        return "<?xml version=\"1.0\" encoding=\"" + getXmlEscaped(options.getCharset()) + "\"?>";
    }

    private String getXmlEscaped(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private String getHtml(List<String> columnNames, List<List<String>> rows, ResultExportOptionsEntity options) {
        StringBuilder html = new StringBuilder("<!DOCTYPE html>").append(NEW_LINE)
            .append("<html><head><meta charset=\"").append(getXmlEscaped(options.getCharset())).append("\"><title>multidbs</title>")
            .append("<style>table{border-collapse:collapse;font-family:sans-serif;font-size:13px}")
            .append("th,td{border:1px solid #ccc;padding:3px 6px}th{background:#eee}.null{color:#999;font-style:italic}</style>")
            .append("</head><body>").append(NEW_LINE).append("<table>").append(NEW_LINE).append("<tr>");
        for (String columnName : columnNames) {
            html.append("<th>").append(getXmlEscaped(columnName)).append("</th>");
        }
        html.append("</tr>").append(NEW_LINE);
        for (List<String> row : rows) {
            html.append("<tr>");
            for (int i = 0; i < columnNames.size(); i++) {
                String value = i < row.size() ? row.get(i) : null;
                html.append(value == null ? "<td class=\"null\">(NULL)</td>" : "<td>" + getXmlEscaped(value) + "</td>");
            }
            html.append("</tr>").append(NEW_LINE);
        }
        return html.append("</table>").append(NEW_LINE).append("</body></html>").append(NEW_LINE).toString();
    }

    /**
     * SpreadsheetML (Excel 2003 XML): Excel y LibreOffice lo abren sin librerias. Cabecera en negrita; los numeros como
     * Number (los decimales con el formato de decimalPlaces); NULL, celda vacia. El ancho de cada columna sigue a su
     * texto mas largo, hasta textSizeLimit caracteres (0 = sin tope).
     */
    private String getExcelXml(List<String> columnNames, List<List<String>> rows, ResultExportOptionsEntity options) {
        String decimalFormat = options.getDecimalPlaces() == 0 ? "0" : "0." + "0".repeat(options.getDecimalPlaces());
        StringBuilder excel = new StringBuilder(getXmlDeclaration(options)).append(NEW_LINE)
            .append("<?mso-application progid=\"Excel.Sheet\"?>").append(NEW_LINE)
            .append("<Workbook xmlns=\"urn:schemas-microsoft-com:office:spreadsheet\" xmlns:ss=\"urn:schemas-microsoft-com:office:spreadsheet\">").append(NEW_LINE)
            .append(" <Styles>").append(NEW_LINE)
            .append("  <Style ss:ID=\"header\"><Font ss:Bold=\"1\"/></Style>").append(NEW_LINE)
            .append("  <Style ss:ID=\"decimal\"><NumberFormat ss:Format=\"").append(decimalFormat).append("\"/></Style>").append(NEW_LINE)
            .append(" </Styles>").append(NEW_LINE)
            .append(" <Worksheet ss:Name=\"").append(getXmlEscaped(getExcelSheetName(options.getTableName()))).append("\">").append(NEW_LINE)
            .append("  <Table>").append(NEW_LINE);
        for (int i = 0; i < columnNames.size(); i++) {
            excel.append("   <Column ss:Width=\"").append(getExcelColumnWidth(columnNames, rows, i, options.getTextSizeLimit())).append("\"/>").append(NEW_LINE);
        }
        if (options.isHeaderIncluded()) {
            excel.append("   <Row>");
            for (String columnName : columnNames) {
                excel.append("<Cell ss:StyleID=\"header\"><Data ss:Type=\"String\">").append(getXmlEscaped(columnName)).append("</Data></Cell>");
            }
            excel.append("</Row>").append(NEW_LINE);
        }
        for (List<String> row : rows) {
            excel.append("   <Row>");
            for (int i = 0; i < columnNames.size(); i++) {
                String value = i < row.size() ? row.get(i) : null;
                if (value == null) {
                    excel.append("<Cell/>");
                    continue;
                }
                if (EXCEL_NUMBER_PATTERN.matcher(value).matches()) {
                    excel.append(value.contains(".") ? "<Cell ss:StyleID=\"decimal\">" : "<Cell>")
                        .append("<Data ss:Type=\"Number\">").append(value).append("</Data></Cell>");
                    continue;
                }
                excel.append("<Cell><Data ss:Type=\"String\">").append(getXmlEscaped(value)).append("</Data></Cell>");
            }
            excel.append("</Row>").append(NEW_LINE);
        }
        return excel.append("  </Table>").append(NEW_LINE).append(" </Worksheet>").append(NEW_LINE).append("</Workbook>").append(NEW_LINE).toString();
    }

    private String getExcelSheetName(String tableName) {
        String sheetName = EXCEL_SHEET_FORBIDDEN_PATTERN.matcher(tableName == null ? "" : tableName).replaceAll("_").trim();
        if (sheetName.isEmpty()) sheetName = "multidbs";
        return sheetName.length() > EXCEL_SHEET_NAME_MAX ? sheetName.substring(0, EXCEL_SHEET_NAME_MAX) : sheetName;
    }

    private int getExcelColumnWidth(List<String> columnNames, List<List<String>> rows, int columnIndex, int textSizeLimit) {
        int maxLength = columnNames.get(columnIndex).length();
        for (List<String> row : rows) {
            String value = columnIndex < row.size() ? row.get(columnIndex) : null;
            if (value != null) maxLength = Math.max(maxLength, value.length());
        }
        int limitedLength = textSizeLimit > 0 ? Math.min(maxLength, textSizeLimit) : maxLength;
        return Math.max(4, limitedLength) * EXCEL_PIXELS_PER_CHAR + 10;
    }

    /**
     * INSERTs de rowsPerInsert filas (1 = una sentencia por fila); los valores siempre como texto ('42'): MySQL los
     * convierte al tipo de la columna. Con createTableSql, el CREATE TABLE va delante.
     */
    private String getSql(List<String> columnNames, List<List<String>> rows, ResultExportOptionsEntity options) {
        List<String> quotedColumns = new ArrayList<>();
        for (String columnName : columnNames) {
            quotedColumns.add(sqlIdentifierQuoter.getQuoted(columnName));
        }
        String insertPrefix = "INSERT INTO " + sqlIdentifierQuoter.getQuoted(options.getTableName()) + " (" + String.join(", ", quotedColumns) + ") VALUES ";
        int rowsPerInsert = options.getRowsPerInsert();
        StringBuilder sql = new StringBuilder();
        String createTableSql = options.getCreateTableSql();
        if (!createTableSql.isEmpty()) {
            sql.append(createTableSql).append(createTableSql.endsWith(";") ? "" : ";").append(NEW_LINE).append(NEW_LINE);
        }
        for (int start = 0; start < rows.size(); start += rowsPerInsert) {
            List<String> tuples = new ArrayList<>();
            for (List<String> row : rows.subList(start, Math.min(rows.size(), start + rowsPerInsert))) {
                List<String> literals = new ArrayList<>();
                for (int i = 0; i < columnNames.size(); i++) {
                    literals.add(sqlLiteralQuoter.getLiteral(i < row.size() ? row.get(i) : null));
                }
                tuples.add("(" + String.join(", ", literals) + ")");
            }
            sql.append(insertPrefix).append(rowsPerInsert == 1 ? "" : NEW_LINE)
                .append(String.join("," + NEW_LINE, tuples)).append(";").append(NEW_LINE);
        }
        return sql.toString();
    }
}
