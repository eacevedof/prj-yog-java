package modules.files.application.savefilesfolder;

import java.util.List;
import java.util.Map;

import static modules.shared.application.Primitives.getInt;

public final class SaveFilesFolderResultDto {

    private final List<String> savedFileNames;
    private final List<String> createdFileNames;
    private final int unchangedCount;

    @SuppressWarnings("unchecked")
    private SaveFilesFolderResultDto(Map<String, Object> primitives) {
        this.savedFileNames = List.copyOf((List<String>) primitives.get("savedFileNames"));
        this.createdFileNames = List.copyOf((List<String>) primitives.get("createdFileNames"));
        this.unchangedCount = getInt(primitives.get("unchangedCount"));
    }

    public static SaveFilesFolderResultDto fromPrimitives(Map<String, Object> primitives) {
        return new SaveFilesFolderResultDto(primitives);
    }

    /**
     * Los ficheros reescritos porque su tabla cambio.
     */
    public List<String> getSavedFileNames() {
        return savedFileNames;
    }

    /**
     * Los .csv nuevos de las tablas creadas con SQL.
     */
    public List<String> getCreatedFileNames() {
        return createdFileNames;
    }

    public int getUnchangedCount() {
        return unchangedCount;
    }
}
