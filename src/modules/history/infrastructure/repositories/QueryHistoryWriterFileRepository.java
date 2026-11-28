package modules.history.infrastructure.repositories;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * El historial exportado a un fichero .sql (UTF-8, se sobrescribe si existe).
 */
public final class QueryHistoryWriterFileRepository {

    private QueryHistoryWriterFileRepository() {
    }

    public static QueryHistoryWriterFileRepository getInstance() {
        return new QueryHistoryWriterFileRepository();
    }

    /**
     * @throws IOException si no se puede escribir: sube tal cual hasta el controller
     */
    public void writeScript(Path filePath, String script) throws IOException {
        Files.writeString(filePath, script, StandardCharsets.UTF_8);
    }
}
