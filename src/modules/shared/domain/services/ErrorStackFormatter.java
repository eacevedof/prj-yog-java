package modules.shared.domain.services;

import java.util.List;

/**
 * Junta los errores de validacion en un solo mensaje, uno por linea.
 */
public final class ErrorStackFormatter {

    private ErrorStackFormatter() {
    }

    public static ErrorStackFormatter getInstance() {
        return new ErrorStackFormatter();
    }

    /**
     * "titulo:\n- error 1\n- error 2": el mensaje de una FieldsException.
     */
    public String getErrorStackMessage(String title, List<String> errors) {
        StringBuilder message = new StringBuilder(title).append(":");
        for (String error : errors) {
            message.append("\n- ").append(error);
        }
        return message.toString();
    }
}
