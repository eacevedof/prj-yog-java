package modules.files.infrastructure.repositories;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Escribe un fichero de la carpeta sin dejarlo a medias: primero a un temporal junto a el y luego lo sustituye de un
 * golpe. Si algo falla, el fichero de antes sigue intacto.
 */
public final class FilesWriterDiskRepository {

    private static final String TEMP_SUFFIX = ".multidbs-tmp";

    private FilesWriterDiskRepository() {
    }

    public static FilesWriterDiskRepository getInstance() {
        return new FilesWriterDiskRepository();
    }

    /**
     * @throws IOException si no se puede escribir
     */
    public void writeText(String folder, String fileName, String text) throws IOException {
        Path filePath = Path.of(folder, fileName);
        Path tempPath = Path.of(folder, fileName + TEMP_SUFFIX);
        Files.writeString(tempPath, text, StandardCharsets.UTF_8);
        Files.move(tempPath, filePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }
}
