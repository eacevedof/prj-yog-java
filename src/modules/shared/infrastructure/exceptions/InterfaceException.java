package modules.shared.infrastructure.exceptions;

/**
 * Lo que tiene toda excepcion controlada (de dominio o FieldsException): su mensaje y un codigo. La UI la muestra
 * tal cual; cualquier otra excepcion es un fallo inesperado.
 */
public interface InterfaceException {

    String getMessage();

    int getCode();
}
