package modules.explorer.application.getdatabaseoptions;

import java.util.List;
import java.util.Map;

import modules.explorer.domain.entities.DatabaseOptionsEntity;

public final class GetDatabaseOptionsResultDto {

    private final DatabaseOptionsEntity databaseOptions;
    private final Map<String, List<String>> collationsByCharacterSet;

    @SuppressWarnings("unchecked")
    private GetDatabaseOptionsResultDto(Map<String, Object> primitives) {
        this.databaseOptions = primitives.get("databaseOptions") instanceof DatabaseOptionsEntity options ? options : null;
        this.collationsByCharacterSet = primitives.get("collationsByCharacterSet") instanceof Map<?, ?> collations
            ? (Map<String, List<String>>) collations
            : Map.of();
    }

    public static GetDatabaseOptionsResultDto fromPrimitives(Map<String, Object> primitives) {
        return new GetDatabaseOptionsResultDto(primitives);
    }

    public DatabaseOptionsEntity getDatabaseOptions() {
        return databaseOptions;
    }

    /**
     * [juego de caracteres => [cotejamientos...]], el de por defecto de cada juego el primero.
     */
    public Map<String, List<String>> getCollationsByCharacterSet() {
        return collationsByCharacterSet;
    }
}
