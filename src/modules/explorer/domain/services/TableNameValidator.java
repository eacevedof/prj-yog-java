package modules.explorer.domain.services;

import java.util.regex.Pattern;

import modules.shared.domain.services.SqlDialect;

/**
 * Los nombres de tabla que crea multidbs (duplicar, renombrar): solo letras ASCII, digitos y _, sin empezar por digito
 * y hasta 64 caracteres (el maximo de MySQL). Ni espacios ni caracteres especiales: asi el nombre vale sin comillas en
 * cualquier motor y en cualquier script. En SQL Server, "esquema.tabla" con las dos partes asi.
 */
public final class TableNameValidator {

    private static final Pattern NAME_PATTERN = Pattern.compile("[A-Za-z_][A-Za-z0-9_]{0,63}");

    private final SqlDialect sqlDialect;

    private TableNameValidator(String dbEngine) {
        this.sqlDialect = SqlDialect.getInstance(dbEngine);
    }

    public static TableNameValidator getInstance(String dbEngine) {
        return new TableNameValidator(dbEngine);
    }

    public boolean isValid(String tableName) {
        if (tableName == null) return false;
        if (!sqlDialect.isMssql()) return NAME_PATTERN.matcher(tableName).matches();
        String[] parts = tableName.split("\\.", -1);
        if (parts.length > 2) return false;
        for (String part : parts) {
            if (!NAME_PATTERN.matcher(part).matches()) return false;
        }
        return true;
    }

    /**
     * Un nombre de columna: la misma regla, sin esquema en ningun motor.
     */
    public boolean isValidColumn(String columnName) {
        return columnName != null && NAME_PATTERN.matcher(columnName).matches();
    }
}
