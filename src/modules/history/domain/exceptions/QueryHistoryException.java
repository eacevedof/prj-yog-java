package modules.history.domain.exceptions;

import modules.shared.domain.enums.ExceptionEnum;
import modules.shared.infrastructure.exceptions.InterfaceException;

public final class QueryHistoryException extends RuntimeException implements InterfaceException {

    private final int code;

    public QueryHistoryException(String message, int code) {
        super(message);
        this.code = code;
    }

    public static QueryHistoryException badRequestCustom(String message) {
        return new QueryHistoryException(message, ExceptionEnum.CODE_BAD_REQUEST);
    }

    @Override
    public int getCode() {
        return code;
    }
}
