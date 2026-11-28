package modules.explorer.application.createtable;

import java.util.Map;

import static modules.shared.application.Primitives.getBool;
import static modules.shared.application.Primitives.getTrimmedString;

import modules.explorer.domain.entities.TableDefinitionEntity;

/**
 * Crear una tabla desde el editor de tablas: la BD donde va, la tabla tal como se ha definido ("edited") y si ya se
 * confirmo produccion.
 */
public final class CreateTableDto {

    private final String connectionUuid;
    private final String sessionKey;
    private final String databaseName;
    private final TableDefinitionEntity edited;
    private final boolean isConfirmed;

    private CreateTableDto(Map<String, Object> primitives) {
        this.connectionUuid = getTrimmedString(primitives.get("connectionUuid"));
        this.sessionKey = getTrimmedString(primitives.get("sessionKey"));
        this.databaseName = getTrimmedString(primitives.get("databaseName"));
        this.edited = primitives.get("edited") instanceof TableDefinitionEntity tableDefinition ? tableDefinition : null;
        this.isConfirmed = getBool(primitives.get("isConfirmed"));
    }

    public static CreateTableDto fromPrimitives(Map<String, Object> primitives) {
        return new CreateTableDto(primitives);
    }

    public String getConnectionUuid() {
        return connectionUuid;
    }

    public String getSessionKey() {
        return sessionKey;
    }

    public String getDatabaseName() {
        return databaseName;
    }

    public TableDefinitionEntity getEdited() {
        return edited;
    }

    public boolean isConfirmed() {
        return isConfirmed;
    }
}
