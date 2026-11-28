package modules.dbcopy.domain.exceptions;

import modules.shared.domain.enums.ExceptionEnum;
import modules.shared.infrastructure.exceptions.InterfaceException;

public final class DbCopyException extends RuntimeException implements InterfaceException {

    private final int code;

    public DbCopyException(String message, int code) {
        super(message);
        this.code = code;
    }

    public static DbCopyException badRequestCustom(String message) {
        return new DbCopyException(message, ExceptionEnum.CODE_BAD_REQUEST);
    }

    public static DbCopyException preconditionRequiredCustom(String message) {
        return new DbCopyException(message, ExceptionEnum.CODE_PRECONDITION_REQUIRED);
    }

    public static DbCopyException serviceUnavailableCustom(String message) {
        return new DbCopyException(message, ExceptionEnum.CODE_SERVICE_UNAVAILABLE);
    }

    @Override
    public int getCode() {
        return code;
    }
}
