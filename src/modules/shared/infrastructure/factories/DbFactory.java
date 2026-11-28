package modules.shared.infrastructure.factories;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.sqlite.SQLiteConfig;

import core.boot.BootPathsEnum;

public final class DbFactory {

    private static final int BUSY_TIMEOUT_MS = 5000;

    // una conexion por fichero para toda la app: la usa el hilo de la UI y, si acaso, una tarea en segundo plano;
    // SQLite ordena las escrituras con busy_timeout
    private static final Map<String, Connection> SQLITE_CONNECTIONS_BY_PATH = new ConcurrentHashMap<>();

    private DbFactory() {
    }

    public static DbFactory getInstance() {
        return new DbFactory();
    }

    /**
     * La BD local de la app (BootPathsEnum.getPathSqlite()). Si la carpeta o el fichero no existen se crean. La conexion
     * lleva foreign_keys = ON y espera hasta BUSY_TIMEOUT_MS si otra esta escribiendo.
     * @throws IllegalStateException
     */
    public Connection getSqlitePdo() {
        String pathSqlite = BootPathsEnum.getPathSqlite();
        return SQLITE_CONNECTIONS_BY_PATH.computeIfAbsent(pathSqlite, this::getNewSqliteConnection);
    }

    /**
     * Cierra las conexiones abiertas (al salir de la app y en los tests, antes de borrar el fichero de su BD).
     */
    public void closeAll() {
        for (Connection sqliteConnection : SQLITE_CONNECTIONS_BY_PATH.values()) {
            try {
                sqliteConnection.close();
            }
            catch (SQLException e) {
                throw new IllegalStateException(e.getMessage(), e);
            }
        }
        SQLITE_CONNECTIONS_BY_PATH.clear();
    }

    private Connection getNewSqliteConnection(String pathSqlite) {
        try {
            Files.createDirectories(Path.of(pathSqlite).getParent());
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        SQLiteConfig sqliteConfig = new SQLiteConfig();
        sqliteConfig.enforceForeignKeys(true);
        sqliteConfig.setBusyTimeout(BUSY_TIMEOUT_MS);
        try {
            return DriverManager.getConnection("jdbc:sqlite:" + pathSqlite, sqliteConfig.toProperties());
        }
        catch (SQLException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }
}
