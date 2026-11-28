package modules.dbcopy.application.renamedatabase;

import java.util.Map;

import static modules.shared.application.Primitives.getBool;
import static modules.shared.application.Primitives.getTrimmedString;

public final class RenameDatabaseDto {

    private final String renameId;
    private final String connectionUuid;
    private final String databaseName;
    private final String newDatabaseName;
    private final String filePath;
    private final boolean isDropOriginal;
    private final boolean isConfirmed;

    private RenameDatabaseDto(Map<String, Object> primitives) {
        this.renameId = getTrimmedString(primitives.get("renameId"));
        this.connectionUuid = getTrimmedString(primitives.get("connectionUuid"));
        this.databaseName = getTrimmedString(primitives.get("databaseName"));
        this.newDatabaseName = getTrimmedString(primitives.get("newDatabaseName"));
        this.filePath = getTrimmedString(primitives.get("filePath"));
        this.isDropOriginal = getBool(primitives.get("isDropOriginal"));
        this.isConfirmed = getBool(primitives.get("isConfirmed"));
    }

    public static RenameDatabaseDto fromPrimitives(Map<String, Object> primitives) {
        return new RenameDatabaseDto(primitives);
    }

    /**
     * La clave del progreso en DbCopyProgressTracker (fases y objetos del volcado).
     */
    public String getRenameId() {
        return renameId;
    }

    public String getConnectionUuid() {
        return connectionUuid;
    }

    public String getDatabaseName() {
        return databaseName;
    }

    public String getNewDatabaseName() {
        return newDatabaseName;
    }

    /**
     * Donde se escribe el volcado de la original (se queda como copia de seguridad).
     */
    public String getFilePath() {
        return filePath;
    }

    /**
     * Borrar la original al acabar, solo si la nueva cuadra con ella.
     */
    public boolean isDropOriginal() {
        return isDropOriginal;
    }

    public boolean isConfirmed() {
        return isConfirmed;
    }
}
