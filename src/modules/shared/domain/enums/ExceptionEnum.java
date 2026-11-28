package modules.shared.domain.enums;

/**
 * Los codigos de task-java (HTTP): aqui no hay HTTP, pero clasifican igual el error para la UI.
 */
public final class ExceptionEnum {

    public static final int CODE_BAD_REQUEST = 400;
    public static final int CODE_NOT_FOUND = 404;
    public static final int CODE_CONFLICT = 409;
    // la accion necesita una confirmacion del usuario antes de seguir (sentencias destructivas en produccion)
    public static final int CODE_PRECONDITION_REQUIRED = 428;
    public static final int CODE_INTERNAL_SERVER_ERROR = 500;
    public static final int CODE_SERVICE_UNAVAILABLE = 503;

    private ExceptionEnum() {
    }
}
