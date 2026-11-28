package modules.query.domain.exceptions;

import modules.shared.domain.enums.ExceptionEnum;
import modules.shared.infrastructure.exceptions.InterfaceException;

/**
 * Entradas invalidas o confirmacion pendiente: un error de SQL no es una excepcion, es un resultado (se ve en Mensajes).
 */
public final class QueryException extends RuntimeException implements InterfaceException {

    private final int code;

    public QueryException(String message, int code) {
        super(message);
        this.code = code;
    }

    public static QueryException badRequestCustom(String message) {
        return new QueryException(message, ExceptionEnum.CODE_BAD_REQUEST);
    }

    /**
     * En produccion hay sentencias destructivas sin confirmar: la UI pregunta y repite con isConfirmed.
     */
    public static QueryException preconditionRequiredCustom(String message) {
        return new QueryException(message, ExceptionEnum.CODE_PRECONDITION_REQUIRED);
    }

    @Override
    public int getCode() {
        return code;
    }
}
