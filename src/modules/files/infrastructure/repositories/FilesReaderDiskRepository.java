package modules.files.infrastructure.repositories;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import modules.shared.application.Primitives;

import modules.connections.domain.enums.DbEngineEnum;

import modules.files.domain.enums.FilesKeyEnum;

/**
 * Los ficheros de datos de una carpeta del disco (sin entrar en subcarpetas) y su texto en UTF-8.
 */
public final class FilesReaderDiskRepository {

    private FilesReaderDiskRepository() {
    }

    public static FilesReaderDiskRepository getInstance() {
        return new FilesReaderDiskRepository();
    }

    /**
     * La ruta de la conexion es una carpeta o un fichero de datos (.csv, .tsv, .json, .jsonl, .ndjson) que existe.
     */
    public boolean isFolderOrDataFile(String path) {
        File file = new File(path);
        return file.isDirectory() || isDataFile(file);
    }

    /**
     * Donde estan los ficheros: la propia carpeta o, si la ruta es un fichero, su carpeta.
     */
    public String getFolder(String path) {
        File file = new File(path);
        if (file.isDirectory() || file.getAbsoluteFile().getParentFile() == null) return path;
        return file.getAbsoluteFile().getParent();
    }

    /**
     * Cada .csv, .tsv, .json, .jsonl o .ndjson de la carpeta con su fecha de modificacion y tamano, por nombre. Si la
     * ruta es un fichero, solo ese.
     */
    public List<Map<String, Object>> getDataFiles(String path) {
        File pathFile = new File(path);
        File[] folderFiles = isDataFile(pathFile) ? new File[] {pathFile} : pathFile.listFiles();
        if (folderFiles == null) return List.of();
        List<Map<String, Object>> dataFiles = new ArrayList<>();
        Arrays.stream(folderFiles)
            .filter(this::isDataFile)
            .sorted(Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER))
            .forEach(file -> dataFiles.add(Primitives.of(
                FilesKeyEnum.FILE_NAME, file.getName(),
                FilesKeyEnum.FILE_MTIME, file.lastModified(),
                FilesKeyEnum.FILE_SIZE, file.length()
            )));
        return dataFiles;
    }

    private boolean isDataFile(File file) {
        return file.isFile() && DbEngineEnum.FILES_EXTENSIONS.contains(getExtension(file.getName()));
    }

    /**
     * @throws IOException si no se puede leer
     */
    public String getText(String folder, String fileName) throws IOException {
        return Files.readString(Path.of(folder, fileName), StandardCharsets.UTF_8);
    }

    public Map<String, Object> getFileState(String folder, String fileName) {
        File file = Path.of(folder, fileName).toFile();
        return Primitives.of(FilesKeyEnum.FILE_MTIME, file.lastModified(), FilesKeyEnum.FILE_SIZE, file.length());
    }

    public String getExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        return dotIndex == -1 ? "" : fileName.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }
}
