package modules.redis.domain.exceptions;

import modules.shared.domain.enums.ExceptionEnum;
import modules.shared.infrastructure.exceptions.InterfaceException;

public final class RedisException extends RuntimeException implements InterfaceException {

    private final int code;

    public RedisException(String message, int code) {
        super(message);
        this.code = code;
    }

    public static RedisException badRequestCustom(String message) {
        return new RedisException(message, ExceptionEnum.CODE_BAD_REQUEST);
    }

    public static RedisException notFoundCustom(String message) {
        return new RedisException(message, ExceptionEnum.CODE_NOT_FOUND);
    }

    public static RedisException serviceUnavailableCustom(String message) {
        return new RedisException(message, ExceptionEnum.CODE_SERVICE_UNAVAILABLE);
    }

    /**
     * 428: en una conexion de produccion, la UI pregunta y repite con isConfirmed.
     */
    public static RedisException preconditionRequiredCustom(String message) {
        return new RedisException(message, ExceptionEnum.CODE_PRECONDITION_REQUIRED);
    }

    @Override
    public int getCode() {
        return code;
    }
}
