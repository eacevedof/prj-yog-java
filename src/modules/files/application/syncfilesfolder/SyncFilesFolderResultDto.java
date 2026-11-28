package modules.files.application.syncfilesfolder;

import java.util.List;
import java.util.Map;

import static modules.shared.application.Primitives.getInt;

public final class SyncFilesFolderResultDto {

    private final int loadedCount;
    private final int unchangedCount;
    private final int removedCount;
    private final List<String> errors;

    @SuppressWarnings("unchecked")
    private SyncFilesFolderResultDto(Map<String, Object> primitives) {
        this.loadedCount = getInt(primitives.get("loadedCount"));
        this.unchangedCount = getInt(primitives.get("unchangedCount"));
        this.removedCount = getInt(primitives.get("removedCount"));
        this.errors = List.copyOf((List<String>) primitives.get("errors"));
    }

    public static SyncFilesFolderResultDto fromPrimitives(Map<String, Object> primitives) {
        return new SyncFilesFolderResultDto(primitives);
    }

    public int getLoadedCount() {
        return loadedCount;
    }

    public int getUnchangedCount() {
        return unchangedCount;
    }

    public int getRemovedCount() {
        return removedCount;
    }

    /**
     * Un texto por fichero que no se pudo cargar (los demas si se cargan).
     */
    public List<String> getErrors() {
        return errors;
    }
}
