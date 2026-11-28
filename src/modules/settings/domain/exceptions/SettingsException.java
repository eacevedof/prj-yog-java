package modules.settings.domain.exceptions;

import modules.shared.domain.enums.ExceptionEnum;
import modules.shared.infrastructure.exceptions.InterfaceException;

public final class SettingsException extends RuntimeException implements InterfaceException {

    private final int code;

    private SettingsException(String message, int code) {
        super(message);
        this.code = code;
    }

    public static SettingsException badRequestCustom(String message) {
        return new SettingsException(message, ExceptionEnum.CODE_BAD_REQUEST);
    }

    @Override
    public int getCode() {
        return code;
    }
}
