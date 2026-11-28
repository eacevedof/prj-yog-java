package modules.explorer.application.altertable;

import java.util.Map;

import static modules.shared.application.Primitives.getBool;
import static modules.shared.application.Primitives.getTrimmedString;

import modules.explorer.domain.entities.TableDefinitionEntity;

/**
 * Guardar el editor de tablas: la tabla como estaba al abrirlo ("original") y como queda ("edited"), y si ya se
 * confirmo produccion.
 */
public final class AlterTableDto {

    private final String connectionUuid;
    private final String sessionKey;
    private final String databaseName;
    private final TableDefinitionEntity original;
    private final TableDefinitionEntity edited;
    private final boolean isConfirmed;

    private AlterTableDto(Map<String, Object> primitives) {
        this.connectionUuid = getTrimmedString(primitives.get("connectionUuid"));
        this.sessionKey = getTrimmedString(primitives.get("sessionKey"));
        this.databaseName = getTrimmedString(primitives.get("databaseName"));
        this.original = primitives.get("original") instanceof TableDefinitionEntity tableDefinition ? tableDefinition : null;
        this.edited = primitives.get("edited") instanceof TableDefinitionEntity tableDefinition ? tableDefinition : null;
        this.isConfirmed = getBool(primitives.get("isConfirmed"));
    }

    public static AlterTableDto fromPrimitives(Map<String, Object> primitives) {
        return new AlterTableDto(primitives);
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

    public TableDefinitionEntity getOriginal() {
        return original;
    }

    public TableDefinitionEntity getEdited() {
        return edited;
    }

    public boolean isConfirmed() {
        return isConfirmed;
    }
}
