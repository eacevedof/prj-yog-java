package modules.files.domain.exceptions;

import modules.shared.domain.enums.ExceptionEnum;
import modules.shared.infrastructure.exceptions.InterfaceException;

public final class FilesException extends RuntimeException implements InterfaceException {

    private final int code;

    public FilesException(String message, int code) {
        super(message);
        this.code = code;
    }

    public static FilesException badRequestCustom(String message) {
        return new FilesException(message, ExceptionEnum.CODE_BAD_REQUEST);
    }

    public static FilesException notFoundCustom(String message) {
        return new FilesException(message, ExceptionEnum.CODE_NOT_FOUND);
    }

    @Override
    public int getCode() {
        return code;
    }
}
