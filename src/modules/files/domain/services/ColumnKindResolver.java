package modules.files.domain.services;

import java.math.BigDecimal;
import java.util.List;
import java.util.regex.Pattern;

import modules.files.domain.enums.FilesColumnKindEnum;

/**
 * El tipo de una columna de texto (CSV): entera si todos sus valores lo son, real si todos son decimales que se
 * vuelven a escribir igual, texto si no. Asi "price &gt; 10" compara numeros, y "007" o "1.50" no cambian al guardar.
 * Los valores vacios no cuentan: son NULL.
 */
public final class ColumnKindResolver {

    private static final Pattern INTEGER_PATTERN = Pattern.compile("-?(0|[1-9]\\d{0,17})");
    private static final Pattern REAL_PATTERN = Pattern.compile("-?\\d+\\.\\d+");

    private ColumnKindResolver() {
    }

    public static ColumnKindResolver getInstance() {
        return new ColumnKindResolver();
    }

    public String getKind(List<String> values) {
        boolean hasValue = false;
        boolean isInteger = true;
        boolean isReal = true;
        for (String value : values) {
            if (value == null || value.isEmpty()) continue;
            hasValue = true;
            if (!INTEGER_PATTERN.matcher(value).matches()) isInteger = false;
            if (!isRealKeptAsWritten(value)) isReal = false;
            if (!isInteger && !isReal) return FilesColumnKindEnum.STRING;
        }
        if (!hasValue) return FilesColumnKindEnum.STRING;
        if (isInteger) return FilesColumnKindEnum.INTEGER;
        return FilesColumnKindEnum.REAL;
    }

    /**
     * El valor ya tipado para SQLite: Long, Double o el texto; vacio = null. En una columna real, un entero sigue
     * siendo entero (7 no pasa a 7.0 al guardar).
     */
    public Object getValue(String kind, String value) {
        if (value == null || value.isEmpty()) return null;
        boolean isNumber = FilesColumnKindEnum.INTEGER.equals(kind) || FilesColumnKindEnum.REAL.equals(kind);
        if (isNumber && INTEGER_PATTERN.matcher(value).matches()) return Long.parseLong(value);
        if (FilesColumnKindEnum.REAL.equals(kind)) return Double.parseDouble(value);
        return value;
    }

    /**
     * Un valor de SQLite como texto: los reales sin notacion cientifica (1.0E10 -> 10000000000.0).
     */
    public String getText(Object value) {
        if (value == null) return "";
        if (value instanceof Double doubleValue) {
            String text = Double.toString(doubleValue);
            return text.contains("E") ? BigDecimal.valueOf(doubleValue).toPlainString() : text;
        }
        return String.valueOf(value);
    }

    private boolean isRealKeptAsWritten(String value) {
        if (INTEGER_PATTERN.matcher(value).matches()) return true;
        if (!REAL_PATTERN.matcher(value).matches()) return false;
        return Double.toString(Double.parseDouble(value)).equals(value);
    }
}
