package modules.mongo.domain.exceptions;

import modules.shared.domain.enums.ExceptionEnum;
import modules.shared.infrastructure.exceptions.InterfaceException;

/**
 * Errores del modulo mongo (el nombre evita confundirla con com.mongodb.MongoException del driver).
 */
public final class MongoModuleException extends RuntimeException implements InterfaceException {

    private final int code;

    public MongoModuleException(String message, int code) {
        super(message);
        this.code = code;
    }

    public static MongoModuleException badRequestCustom(String message) {
        return new MongoModuleException(message, ExceptionEnum.CODE_BAD_REQUEST);
    }

    public static MongoModuleException notFoundCustom(String message) {
        return new MongoModuleException(message, ExceptionEnum.CODE_NOT_FOUND);
    }

    public static MongoModuleException serviceUnavailableCustom(String message) {
        return new MongoModuleException(message, ExceptionEnum.CODE_SERVICE_UNAVAILABLE);
    }

    /**
     * 428: en una conexion de produccion, la UI pregunta y repite con isConfirmed.
     */
    public static MongoModuleException preconditionRequiredCustom(String message) {
        return new MongoModuleException(message, ExceptionEnum.CODE_PRECONDITION_REQUIRED);
    }

    @Override
    public int getCode() {
        return code;
    }
}
