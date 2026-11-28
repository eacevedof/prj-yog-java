package modules.connections.infrastructure.repositories;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import modules.shared.infrastructure.components.date.DateTimer;

/**
 * El fichero de una conexion SQLite local en el disco. Un fichero de 0 bytes es una BD SQLite vacia valida: el driver
 * la inicializa al primer CREATE.
 */
public final class SqliteFilesWriterDiskRepository {

    private static final String SQLITE_EXTENSION = ".sqlite";
    private static final String DEFAULT_FILE_BASE_NAME = "javayog";

    private SqliteFilesWriterDiskRepository() {
    }

    public static SqliteFilesWriterDiskRepository getInstance() {
        return new SqliteFilesWriterDiskRepository();
    }

    /**
     * Si la ruta es una carpeta que existe, crea dentro un .sqlite vacio (con el nombre de la conexion; si ya existe,
     * con la fecha y hora detras) y devuelve su ruta. Si no es una carpeta, la ruta tal cual.
     * @throws UncheckedIOException si no se puede crear
     */
    public String createEmptyFileIfFolder(String sqlitePath, String connectionName) {
        Path folder = Path.of(sqlitePath);
        if (!Files.isDirectory(folder)) return sqlitePath;
        String baseName = getFileBaseName(connectionName);
        Path sqliteFile = folder.resolve(baseName + SQLITE_EXTENSION);
        if (Files.exists(sqliteFile)) {
            sqliteFile = folder.resolve(baseName + "-" + DateTimer.getInstance().getNowDt().replaceAll("[^0-9]", "") + SQLITE_EXTENSION);
        }
        try {
            Files.createFile(sqliteFile);
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return sqliteFile.toAbsolutePath().toString();
    }

    /**
     * El nombre de la conexion valido como fichero: letras, digitos, - y _ (lo demas, -).
     */
    private String getFileBaseName(String connectionName) {
        String baseName = connectionName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]+", "-").replaceAll("^-+|-+$", "");
        return baseName.isEmpty() ? DEFAULT_FILE_BASE_NAME : baseName;
    }
}
