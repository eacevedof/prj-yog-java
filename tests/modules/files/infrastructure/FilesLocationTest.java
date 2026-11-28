package modules.files.infrastructure;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import testcase.AbstractTestCase;

import modules.files.domain.enums.FilesFormatEnum;
import modules.files.domain.enums.FilesKeyEnum;
import modules.files.infrastructure.components.JsonTableConverter;
import modules.files.infrastructure.repositories.FilesReaderDiskRepository;
import modules.files.infrastructure.components.JsonLineParseException;

/**
 * Motor "Ficheros": la ruta puede ser una carpeta o un fichero concreto, y un JSON roto dice en que linea.
 */
final class FilesLocationTest extends AbstractTestCase {

    @TempDir
    private Path folder;

    private final FilesReaderDiskRepository filesReaderDiskRepository = FilesReaderDiskRepository.getInstance();
    private final JsonTableConverter jsonTableConverter = JsonTableConverter.getInstance();

    @Test
    void testAFilePathIsOnlyThatTableAndItsFolderHoldsIt() throws IOException {
        Path usersCsv = Files.writeString(folder.resolve("users.csv"), "id\n1\n", StandardCharsets.UTF_8);
        Files.writeString(folder.resolve("orders.json"), "[]", StandardCharsets.UTF_8);

        List<Map<String, Object>> folderFiles = filesReaderDiskRepository.getDataFiles(folder.toString());
        List<Map<String, Object>> singleFiles = filesReaderDiskRepository.getDataFiles(usersCsv.toString());

        assertEquals(2, folderFiles.size());
        assertEquals(1, singleFiles.size());
        assertEquals("users.csv", singleFiles.getFirst().get(FilesKeyEnum.FILE_NAME));
        assertEquals(folder.toString(), filesReaderDiskRepository.getFolder(usersCsv.toString()));
        assertEquals(folder.toString(), filesReaderDiskRepository.getFolder(folder.toString()));
        assertTrue(filesReaderDiskRepository.isFolderOrDataFile(usersCsv.toString()));
        assertFalse(filesReaderDiskRepository.isFolderOrDataFile(folder.resolve("missing.csv").toString()));
    }

    @Test
    void testBrokenJsonArraySaysTheLineOfTheFailingObject() {
        String brokenJson = "\n[\n    {\"id\": 1},\n    {\"id\": 2, \"name\": \"Bea\"},\n    {\"id\": tru}\n]\n";

        JsonLineParseException jsonLineParseException = assertThrows(
            JsonLineParseException.class, () -> jsonTableConverter.getTable(brokenJson, FilesFormatEnum.JSON_ARRAY)
        );

        assertEquals(5, jsonLineParseException.getLineNumber());
        assertTrue(jsonLineParseException.getDetail().contains("tru"));
    }

    @Test
    void testBrokenJsonLinesSaysItsLine() {
        String brokenJsonLines = "{\"id\": 1}\n\n{\"id\": }\n";

        JsonLineParseException jsonLineParseException = assertThrows(
            JsonLineParseException.class, () -> jsonTableConverter.getTable(brokenJsonLines, FilesFormatEnum.JSON_LINES)
        );

        assertEquals(3, jsonLineParseException.getLineNumber());
    }
}
