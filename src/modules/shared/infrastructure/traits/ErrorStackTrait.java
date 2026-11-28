package modules.shared.infrastructure.traits;

import java.util.List;

public final class ErrorStackTrait {

    private ErrorStackTrait() {
    }

    public static ErrorStackTrait getInstance() {
        return new ErrorStackTrait();
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
