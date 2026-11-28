package modules.explorer.application.alterdatabase;

import java.util.Map;

import static modules.shared.application.Primitives.getBool;
import static modules.shared.application.Primitives.getTrimmedString;

import modules.explorer.domain.entities.DatabaseOptionsEntity;

/**
 * Guardar el "Alter Database": la BD como estaba al abrir el dialogo ("original") y como queda ("edited"), y si ya se
 * confirmo produccion.
 */
public final class AlterDatabaseDto {

    private final String connectionUuid;
    private final String sessionKey;
    private final DatabaseOptionsEntity original;
    private final DatabaseOptionsEntity edited;
    private final boolean isConfirmed;

    private AlterDatabaseDto(Map<String, Object> primitives) {
        this.connectionUuid = getTrimmedString(primitives.get("connectionUuid"));
        this.sessionKey = getTrimmedString(primitives.get("sessionKey"));
        this.original = primitives.get("original") instanceof DatabaseOptionsEntity options ? options : null;
        this.edited = primitives.get("edited") instanceof DatabaseOptionsEntity options ? options : null;
        this.isConfirmed = getBool(primitives.get("isConfirmed"));
    }

    public static AlterDatabaseDto fromPrimitives(Map<String, Object> primitives) {
        return new AlterDatabaseDto(primitives);
    }

    public String getConnectionUuid() {
        return connectionUuid;
    }

    public String getSessionKey() {
        return sessionKey;
    }

    public DatabaseOptionsEntity getOriginal() {
        return original;
    }

    public DatabaseOptionsEntity getEdited() {
        return edited;
    }

    public boolean isConfirmed() {
        return isConfirmed;
    }
}
