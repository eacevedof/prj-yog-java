package modules.query.domain.services;

/**
 * JSON de una celda formateado (feature 43), sin librerias: un analizador descendente que valida y re-escribe con
 * sangria de 2 espacios. Solo objetos y arrays cuentan como JSON (un numero o una cadena sueltos se ven tal cual).
 * Las cadenas se copian sin tocar (escapes incluidos).
 */
public final class JsonFormatter {

    private static final String INDENT = "  ";

    private String json;
    private int position;
    private StringBuilder output;

    private JsonFormatter() {
    }

    public static JsonFormatter getInstance() {
        return new JsonFormatter();
    }

    public boolean isJson(String value) {
        return getPrettyJson(value) != null;
    }

    /**
     * El JSON formateado, o null si el valor no es un objeto/array JSON valido.
     */
    public String getPrettyJson(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        if (trimmed.isEmpty() || (trimmed.charAt(0) != '{' && trimmed.charAt(0) != '[')) return null;

        json = trimmed;
        position = 0;
        output = new StringBuilder();
        try {
            writeValue(0);
            skipWhitespace();
            return position == json.length() ? output.toString() : null;
        }
        catch (IllegalArgumentException | StringIndexOutOfBoundsException e) {
            return null;
        }
    }

    private void writeValue(int depth) {
        skipWhitespace();
        char current = json.charAt(position);
        switch (current) {
            case '{' -> writeContainer(depth, '}', true);
            case '[' -> writeContainer(depth, ']', false);
            case '"' -> writeString();
            default -> writeLiteral();
        }
    }

    private void writeContainer(int depth, char closing, boolean isObject) {
        output.append(json.charAt(position));
        position++;
        skipWhitespace();
        if (json.charAt(position) == closing) {
            output.append(closing);
            position++;
            return;
        }
        while (true) {
            output.append('\n').append(INDENT.repeat(depth + 1));
            if (isObject) {
                skipWhitespace();
                if (json.charAt(position) != '"') throw new IllegalArgumentException("key expected");
                writeString();
                skipWhitespace();
                expect(':');
                output.append(": ");
            }
            writeValue(depth + 1);
            skipWhitespace();
            char separator = json.charAt(position);
            if (separator == ',') {
                output.append(',');
                position++;
                continue;
            }
            if (separator != closing) throw new IllegalArgumentException("unexpected " + separator);
            position++;
            output.append('\n').append(INDENT.repeat(depth)).append(closing);
            return;
        }
    }

    private void writeString() {
        int start = position;
        position++;
        while (json.charAt(position) != '"') {
            position += json.charAt(position) == '\\' ? 2 : 1;
        }
        position++;
        output.append(json, start, position);
    }

    /**
     * Numeros, true, false y null.
     */
    private void writeLiteral() {
        int start = position;
        while (position < json.length() && ",}] \t\r\n".indexOf(json.charAt(position)) == -1) {
            position++;
        }
        String literal = json.substring(start, position);
        if (!literal.equals("true") && !literal.equals("false") && !literal.equals("null")
            && !literal.matches("-?(?:0|[1-9]\\d*)(?:\\.\\d+)?(?:[eE][+-]?\\d+)?")) {
            throw new IllegalArgumentException("invalid literal " + literal);
        }
        output.append(literal);
    }

    private void expect(char expected) {
        if (json.charAt(position) != expected) throw new IllegalArgumentException(expected + " expected");
        position++;
    }

    private void skipWhitespace() {
        while (position < json.length() && Character.isWhitespace(json.charAt(position))) {
            position++;
        }
    }
}
