package modules.workspace.domain.exceptions;

import modules.shared.domain.enums.ExceptionEnum;
import modules.shared.infrastructure.exceptions.InterfaceException;

public final class WorkspaceException extends RuntimeException implements InterfaceException {

    private final int code;

    private WorkspaceException(String message, int code) {
        super(message);
        this.code = code;
    }

    public static WorkspaceException badRequestCustom(String message) {
        return new WorkspaceException(message, ExceptionEnum.CODE_BAD_REQUEST);
    }

    @Override
    public int getCode() {
        return code;
    }
}
