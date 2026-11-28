package modules.query.domain.entities;

import java.util.Map;

import static modules.shared.application.Primitives.getInt;

/**
 * Las opciones del dialogo "Exportar como" (como el Export As de SQLyog). Los separadores de CSV llegan escritos como
 * en SQLyog (\t, \n, \r\n, \\, \N) o "&lt;NONE&gt;" (vacio) y aqui ya estan convertidos a sus caracteres.
 * <ul>
 * <li>CSV: fieldsTerminatedBy, enclosedBy (vacio = sin comillas), escapedBy (vacio o igual a enclosedBy = la comilla
 * doblada), linesTerminatedBy, nullReplacedBy y si va la cabecera con los nombres de columna.</li>
 * <li>Excel XML: el ancho maximo de una columna de texto (en caracteres) y los decimales de las numericas.</li>
 * <li>SQL: la tabla de los INSERT y cuantas filas por INSERT (1 = una sentencia por fila) y, si la rejilla es
 * de una tabla, su CREATE TABLE para ponerlo delante (vacio = sin el).</li>
 * <li>charset: el nombre que va en la cabecera de XML, HTML y Excel XML.</li>
 * </ul>
 */
public final class ResultExportOptionsEntity {

    public static final String NONE = "<NONE>";
    public static final String DEFAULT_FIELDS_TERMINATED_BY = ",";
    public static final String DEFAULT_ENCLOSED_BY = "\"";
    public static final String DEFAULT_ESCAPED_BY = "\"";
    public static final String DEFAULT_LINES_TERMINATED_BY = "\n";
    public static final String DEFAULT_NULL_REPLACED_BY = "\\N";
    public static final int DEFAULT_TEXT_SIZE_LIMIT = 50;
    public static final int DEFAULT_DECIMAL_PLACES = 2;
    public static final int DEFAULT_ROWS_PER_INSERT = 1;
    public static final String DEFAULT_CHARSET = "UTF-8";

    private final String fieldsTerminatedBy;
    private final String enclosedBy;
    private final String escapedBy;
    private final String linesTerminatedBy;
    private final String nullReplacedBy;
    private final boolean isHeaderIncluded;
    private final int textSizeLimit;
    private final int decimalPlaces;
    private final String tableName;
    private final int rowsPerInsert;
    private final String charset;
    private final String createTableSql;

    private ResultExportOptionsEntity(Map<String, Object> primitives) {
        this.fieldsTerminatedBy = getUnescaped(primitives.getOrDefault("fieldsTerminatedBy", DEFAULT_FIELDS_TERMINATED_BY));
        this.enclosedBy = getUnescaped(primitives.getOrDefault("enclosedBy", DEFAULT_ENCLOSED_BY));
        this.escapedBy = getUnescaped(primitives.getOrDefault("escapedBy", DEFAULT_ESCAPED_BY));
        this.linesTerminatedBy = getUnescaped(primitives.getOrDefault("linesTerminatedBy", DEFAULT_LINES_TERMINATED_BY));
        this.nullReplacedBy = getUnescapedNull(primitives.getOrDefault("nullReplacedBy", DEFAULT_NULL_REPLACED_BY));
        this.isHeaderIncluded = !Boolean.FALSE.equals(primitives.get("isHeaderIncluded"));
        this.textSizeLimit = Math.max(0, getInt(primitives.getOrDefault("textSizeLimit", DEFAULT_TEXT_SIZE_LIMIT)));
        this.decimalPlaces = Math.max(0, Math.min(10, getInt(primitives.getOrDefault("decimalPlaces", DEFAULT_DECIMAL_PLACES))));
        this.tableName = String.valueOf(primitives.getOrDefault("tableName", "")).trim();
        this.rowsPerInsert = Math.max(1, getInt(primitives.getOrDefault("rowsPerInsert", DEFAULT_ROWS_PER_INSERT)));
        String charsetName = String.valueOf(primitives.getOrDefault("charset", DEFAULT_CHARSET)).trim();
        this.charset = charsetName.isEmpty() ? DEFAULT_CHARSET : charsetName;
        Object createTableSqlValue = primitives.get("createTableSql");
        this.createTableSql = createTableSqlValue == null ? "" : String.valueOf(createTableSqlValue).trim();
    }

    public static ResultExportOptionsEntity fromPrimitives(Map<String, Object> primitives) {
        return new ResultExportOptionsEntity(primitives);
    }

    /**
     * "\t" -> tabulador, "\n" -> salto, "\\" -> barra, "&lt;NONE&gt;" o vacio -> nada. El resto, tal cual.
     */
    public static String getUnescaped(Object escapedValue) {
        String value = escapedValue == null ? "" : String.valueOf(escapedValue);
        if (value.isEmpty() || NONE.equalsIgnoreCase(value.trim())) return "";
        StringBuilder unescaped = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (current != '\\' || i + 1 >= value.length()) {
                unescaped.append(current);
                continue;
            }
            char next = value.charAt(++i);
            switch (next) {
                case 't' -> unescaped.append('\t');
                case 'n' -> unescaped.append('\n');
                case 'r' -> unescaped.append('\r');
                case '0' -> unescaped.append('\0');
                case '\\' -> unescaped.append('\\');
                default -> unescaped.append('\\').append(next);
            }
        }
        return unescaped.toString();
    }

    /**
     * El texto de un separador para el dialogo: lo contrario de getUnescaped.
     */
    public static String getEscaped(String value) {
        if (value == null || value.isEmpty()) return NONE;
        return value.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n").replace("\r", "\\r");
    }

    /**
     * El NULL de CSV: \N se queda como \N (lo que entiende LOAD DATA), no como salto de linea.
     */
    private static String getUnescapedNull(Object escapedValue) {
        String value = escapedValue == null ? "" : String.valueOf(escapedValue);
        return "\\N".equals(value) ? value : getUnescaped(value);
    }

    public String getFieldsTerminatedBy() {
        return fieldsTerminatedBy;
    }

    public String getEnclosedBy() {
        return enclosedBy;
    }

    public String getEscapedBy() {
        return escapedBy;
    }

    public String getLinesTerminatedBy() {
        return linesTerminatedBy;
    }

    public String getNullReplacedBy() {
        return nullReplacedBy;
    }

    public boolean isHeaderIncluded() {
        return isHeaderIncluded;
    }

    public int getTextSizeLimit() {
        return textSizeLimit;
    }

    public int getDecimalPlaces() {
        return decimalPlaces;
    }

    public String getTableName() {
        return tableName;
    }

    public int getRowsPerInsert() {
        return rowsPerInsert;
    }

    public String getCharset() {
        return charset;
    }

    public String getCreateTableSql() {
        return createTableSql;
    }
}
