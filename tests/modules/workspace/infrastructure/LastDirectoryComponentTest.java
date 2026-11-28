package modules.workspace.infrastructure;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import modules.shared.infrastructure.components.files.FolderOpenerComponent;

import testcase.AbstractTestCase;

import modules.workspace.domain.enums.WorkspaceKeyEnum;
import modules.workspace.infrastructure.components.LastDirectoryComponent;
import modules.workspace.infrastructure.components.LastFileComponent;

/**
 * La carpeta de Volcado SQL / Exportar como sobrevive al reinicio: manda la ultima guardada mientras exista.
 */
final class LastDirectoryComponentTest extends AbstractTestCase {

    @BeforeEach
    void bootDatabase() {
        bootFreshDatabase("LastDirectoryComponentTest");
    }

    @Test
    void testTheLastSavedFolderWinsWhileItExists() throws Exception {
        Path firstFolder = Files.createDirectories(Path.of("target", "last-dir-test", "first").toAbsolutePath());
        Path secondFolder = Files.createDirectories(Path.of("target", "last-dir-test", "second dumps").toAbsolutePath());
        LastDirectoryComponent sqlDumpDirectory = LastDirectoryComponent.getInstance(WorkspaceKeyEnum.SQL_DUMP_DIRECTORY);
        File userHome = new File(System.getProperty("user.home"));

        File beforeSaving = sqlDumpDirectory.getDirectory();
        sqlDumpDirectory.saveDirectoryOf(firstFolder.resolve("db_one.sql").toFile());
        sqlDumpDirectory.saveDirectoryOf(secondFolder.resolve("db_two.sql").toFile());
        File afterSaving = sqlDumpDirectory.getDirectory();
        File otherDialog = LastDirectoryComponent.getInstance(WorkspaceKeyEnum.RESULT_EXPORT_DIRECTORY).getDirectory();
        Files.delete(secondFolder);
        File afterDeleting = sqlDumpDirectory.getDirectory();

        assertEquals(userHome, beforeSaving);
        assertEquals(secondFolder.toFile(), afterSaving);
        assertEquals(userHome, otherDialog);
        assertEquals(userHome, afterDeleting);
    }

    @Test
    void testTheLastExecutedFileComesBackWhileItExists() throws Exception {
        Path dumpFile = Files.createDirectories(Path.of("target", "last-dir-test", "files").toAbsolutePath()).resolve("db_mypromos.sql");
        Files.writeString(dumpFile, "SELECT 1;");
        LastFileComponent lastSqlFile = LastFileComponent.getInstance(WorkspaceKeyEnum.SQL_FILE_LAST_FILE);

        File beforeSaving = lastSqlFile.getFile();
        lastSqlFile.saveFile(dumpFile.toFile());
        File afterSaving = lastSqlFile.getFile();
        Files.delete(dumpFile);
        File afterDeleting = lastSqlFile.getFile();

        assertEquals(null, beforeSaving);
        assertEquals(dumpFile.toFile(), afterSaving);
        assertEquals(null, afterDeleting);
    }

    @Test
    void testOpenFolderUsesTheFirstFolderThatExists() throws Exception {
        Path folder = Files.createDirectories(Path.of("target", "last-dir-test", "open").toAbsolutePath());
        FolderOpenerComponent folderOpenerComponent = FolderOpenerComponent.getInstance();

        assertEquals(folder.toFile(), folderOpenerComponent.getExistingFolder(folder.resolve("dump.sql").toString()));
        assertEquals(folder.toFile(), folderOpenerComponent.getExistingFolder(folder.resolve("not/yet/dump.sql").toString()));
        assertEquals(null, folderOpenerComponent.getExistingFolder("  "));
    }
}
