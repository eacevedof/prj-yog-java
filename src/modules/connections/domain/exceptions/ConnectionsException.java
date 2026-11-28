package modules.connections.domain.exceptions;

import modules.shared.domain.enums.ExceptionEnum;
import modules.shared.infrastructure.exceptions.InterfaceException;

public final class ConnectionsException extends RuntimeException implements InterfaceException {

    private final int code;

    private ConnectionsException(String message, int code) {
        super(message);
        this.code = code;
    }

    /**
     * Se lanza en el sitio: throw ConnectionsException.notFoundCustom(...)
     */
    public static ConnectionsException badRequestCustom(String message) {
        return new ConnectionsException(message, ExceptionEnum.CODE_BAD_REQUEST);
    }

    public static ConnectionsException notFoundCustom(String message) {
        return new ConnectionsException(message, ExceptionEnum.CODE_NOT_FOUND);
    }

    public static ConnectionsException conflictCustom(String message) {
        return new ConnectionsException(message, ExceptionEnum.CODE_CONFLICT);
    }

    public static ConnectionsException serviceUnavailableCustom(String message) {
        return new ConnectionsException(message, ExceptionEnum.CODE_SERVICE_UNAVAILABLE);
    }

    @Override
    public int getCode() {
        return code;
    }
}
