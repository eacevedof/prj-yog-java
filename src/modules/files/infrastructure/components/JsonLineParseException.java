package modules.files.infrastructure.components;

import org.bson.json.JsonParseException;

/**
 * Un JSON roto con la linea donde empieza el objeto que falla (el lector de BSON no da la posicion): asi el mensaje
 * de la carga dice fichero, linea y que esperaba.
 */
public final class JsonLineParseException extends JsonParseException {

    private final int lineNumber;
    private final String detail;

    public JsonLineParseException(int lineNumber, String detail, Throwable cause) {
        super(detail);
        initCause(cause);
        this.lineNumber = lineNumber;
        this.detail = detail;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public String getDetail() {
        return detail;
    }
}
