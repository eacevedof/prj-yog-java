package modules.dbcopy.domain.services;

import java.util.regex.Pattern;

/**
 * Un nombre de BD MySQL que se puede crear sin sorpresas: letras, digitos, _, $ y -, hasta 64 caracteres.
 */
public final class DatabaseNameValidator {

    private static final Pattern DATABASE_NAME_PATTERN = Pattern.compile("[A-Za-z0-9_$-]{1,64}");

    private DatabaseNameValidator() {
    }

    public static DatabaseNameValidator getInstance() {
        return new DatabaseNameValidator();
    }

    public boolean isValid(String databaseName) {
        return databaseName != null && DATABASE_NAME_PATTERN.matcher(databaseName).matches();
    }
}
