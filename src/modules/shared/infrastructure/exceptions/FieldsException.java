package modules.shared.infrastructure.exceptions;

import java.util.List;

import modules.shared.domain.enums.ExceptionEnum;

public final class FieldsException extends RuntimeException implements InterfaceException {

    private final int code;
    private final List<String> errors;

    private FieldsException(String message, List<String> errors) {
        super(message);
        this.code = ExceptionEnum.CODE_BAD_REQUEST;
        this.errors = List.copyOf(errors);
    }

    /**
     * Se lanza en el sitio: throw FieldsException.badRequestCustom(...) (el flujo de Java necesita ver el throw).
     */
    public static FieldsException badRequestCustom(String message, List<String> errors) {
        return new FieldsException(message, errors);
    }

    @Override
    public int getCode() {
        return code;
    }

    /**
     * Un error "campo: mensaje" por regla incumplida: el formulario los pinta junto a su campo.
     */
    public List<String> getErrors() {
        return errors;
    }
}
