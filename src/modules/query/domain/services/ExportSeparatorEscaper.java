package modules.query.domain.services;

import modules.query.domain.entities.ResultExportOptionsEntity;

/**
 * Los separadores de CSV del dialogo "Exportar como" se escriben como en SQLyog (\t, \n, \r\n, \\, \N o
 * "&lt;NONE&gt;"): de esa notacion a sus caracteres y vuelta.
 */
public final class ExportSeparatorEscaper {

    private ExportSeparatorEscaper() {
    }

    public static ExportSeparatorEscaper getInstance() {
        return new ExportSeparatorEscaper();
    }

    /**
     * "\t" -> tabulador, "\n" -> salto, "\\" -> barra, "&lt;NONE&gt;" o vacio -> nada. El resto, tal cual.
     */
    public String getUnescaped(Object escapedValue) {
        String value = escapedValue == null ? "" : String.valueOf(escapedValue);
        if (value.isEmpty() || ResultExportOptionsEntity.NONE.equalsIgnoreCase(value.trim())) return "";
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
    public String getEscaped(String value) {
        if (value == null || value.isEmpty()) return ResultExportOptionsEntity.NONE;
        return value.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n").replace("\r", "\\r");
    }

    /**
     * El NULL de CSV: \N se queda como \N (lo que entiende LOAD DATA), no como salto de linea.
     */
    public String getUnescapedNull(Object escapedValue) {
        String value = escapedValue == null ? "" : String.valueOf(escapedValue);
        return "\\N".equals(value) ? value : getUnescaped(value);
    }
}
