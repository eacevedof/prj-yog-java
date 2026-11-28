package modules.explorer.domain.exceptions;

import modules.shared.domain.enums.ExceptionEnum;
import modules.shared.infrastructure.exceptions.InterfaceException;

public final class ExplorerException extends RuntimeException implements InterfaceException {

    private final int code;

    public ExplorerException(String message, int code) {
        super(message);
        this.code = code;
    }

    public static ExplorerException badRequestCustom(String message) {
        return new ExplorerException(message, ExceptionEnum.CODE_BAD_REQUEST);
    }

    public static ExplorerException notFoundCustom(String message) {
        return new ExplorerException(message, ExceptionEnum.CODE_NOT_FOUND);
    }

    public static ExplorerException serviceUnavailableCustom(String message) {
        return new ExplorerException(message, ExceptionEnum.CODE_SERVICE_UNAVAILABLE);
    }

    public static ExplorerException preconditionRequiredCustom(String message) {
        return new ExplorerException(message, ExceptionEnum.CODE_PRECONDITION_REQUIRED);
    }

    @Override
    public int getCode() {
        return code;
    }
}
