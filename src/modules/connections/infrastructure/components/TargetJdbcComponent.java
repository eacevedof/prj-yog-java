package modules.connections.infrastructure.components;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

import modules.shared.domain.enums.SharedConfigEnum;

import core.boot.BootPathsEnum;
import static core.config.Global.__;

import modules.connections.domain.enums.ConnectionsMessageEnum;
import modules.connections.domain.enums.DbEngineEnum;
import modules.connections.domain.entities.ConnectionEntity;

/**
 * Como se abre una conexion JDBC contra la BD destino de una conexion guardada (MySQL/MariaDB, PostgreSQL, SQL Server o SQLite): la URL y
 * sus propiedades. Lo usan la prueba de conexion y las sesiones de trabajo (arbol de objetos y editor).
 */
public final class TargetJdbcComponent {

    private static final int CONNECT_TIMEOUT_MS = 8000;

    private TargetJdbcComponent() {
    }

    public static TargetJdbcComponent getInstance() {
        return new TargetJdbcComponent();
    }

    public String getJdbcUrl(ConnectionEntity connectionEntity) {
        return getJdbcUrl(connectionEntity, connectionEntity.getHost(), connectionEntity.getPort());
    }

    /**
     * Abre la conexion JDBC (host y puerto: los de la conexion o los del tunel SSH). Un SQLite cuyo fichero no existe
     * falla en vez de crearlo vacio, que es lo que haria el driver: casi siempre es una ruta mal escrita.
     * @throws SQLException
     */
    public Connection openConnection(ConnectionEntity connectionEntity, String host, int port, int socketTimeoutMs) throws SQLException {
        if (DbEngineEnum.FILES.equals(connectionEntity.getDbEngine())) {
            return getFilesWorkingDb(connectionEntity);
        }
        if (DbEngineEnum.SQLITE.equals(connectionEntity.getDbEngine())) {
            Path sqliteFile = Path.of(connectionEntity.getDatabaseName());
            if (!Files.isRegularFile(sqliteFile)) {
                throw new SQLException(__(ConnectionsMessageEnum.SQLITE_FILE_X_NOT_FOUND, sqliteFile.toAbsolutePath()));
            }
            Properties sqliteProperties = new Properties();
            // espera a que otro proceso suelte el bloqueo en vez de fallar al momento
            sqliteProperties.setProperty("busy_timeout", String.valueOf(CONNECT_TIMEOUT_MS));
            return DriverManager.getConnection("jdbc:sqlite:" + sqliteFile.toAbsolutePath(), sqliteProperties);
        }
        return DriverManager.getConnection(getJdbcUrl(connectionEntity, host, port), getProperties(connectionEntity, socketTimeoutMs));
    }

    /**
     * Motor "Ficheros": el SQLite de trabajo de la conexion (se crea vacio la primera vez); los ficheros de la carpeta
     * se cargan en el al abrir la conexion y se vuelcan con "Guardar en ficheros".
     * @throws SQLException
     */
    private Connection getFilesWorkingDb(ConnectionEntity connectionEntity) throws SQLException {
        Path workingDbFile = Path.of(BootPathsEnum.getPathFilesWorkingDb(connectionEntity.getConnectionUuid()));
        try {
            Files.createDirectories(workingDbFile.getParent());
        }
        catch (IOException e) {
            throw new SQLException(e.getMessage(), e);
        }
        Properties sqliteProperties = new Properties();
        sqliteProperties.setProperty("busy_timeout", String.valueOf(CONNECT_TIMEOUT_MS));
        return DriverManager.getConnection("jdbc:sqlite:" + workingDbFile.toAbsolutePath(), sqliteProperties);
    }

    /**
     * Con tunel SSH (feature 12) se entra por 127.0.0.1 y el puerto local del tunel. En PostgreSQL la BD es obligatoria
     * (sin ella, "postgres").
     */
    public String getJdbcUrl(ConnectionEntity connectionEntity, String host, int port) {
        if (DbEngineEnum.PGSQL.equals(connectionEntity.getDbEngine())) {
            return "jdbc:postgresql://%s:%d/%s".formatted(host, port, getPgsqlDatabase(connectionEntity));
        }
        // SQL Server: la BD inicial va en las propiedades (databaseName), no en la URL
        if (DbEngineEnum.MSSQL.equals(connectionEntity.getDbEngine())) return "jdbc:sqlserver://%s:%d".formatted(host, port);
        return "jdbc:mysql://%s:%d/%s".formatted(host, port, connectionEntity.getDatabaseName());
    }

    /**
     * socketTimeout = 0 en las sesiones de trabajo: una query larga no debe cortarse sola (para eso esta Cancelar).
     */
    public Properties getProperties(ConnectionEntity connectionEntity, int socketTimeoutMs) {
        Properties properties = new Properties();
        properties.setProperty("user", connectionEntity.getUserName());
        properties.setProperty("password", connectionEntity.getUserPassword());
        if (DbEngineEnum.PGSQL.equals(connectionEntity.getDbEngine())) {
            // el driver de pg cuenta en segundos
            properties.setProperty("connectTimeout", String.valueOf(CONNECT_TIMEOUT_MS / 1000));
            properties.setProperty("socketTimeout", String.valueOf(socketTimeoutMs / 1000));
            properties.setProperty("sslmode", "prefer");
            properties.setProperty("ApplicationName", SharedConfigEnum.APP_NAME);
            // la rejilla manda los valores como texto: que el servidor los convierta al tipo de la columna
            properties.setProperty("stringtype", "unspecified");
            return properties;
        }
        if (DbEngineEnum.MSSQL.equals(connectionEntity.getDbEngine())) {
            if (!connectionEntity.getDatabaseName().isEmpty()) properties.setProperty("databaseName", connectionEntity.getDatabaseName());
            // loginTimeout en segundos, socketTimeout en ms
            properties.setProperty("loginTimeout", String.valueOf(CONNECT_TIMEOUT_MS / 1000));
            properties.setProperty("socketTimeout", String.valueOf(socketTimeoutMs));
            // cifrado sin validar el certificado: los SQL Server locales y de docker traen uno autofirmado (como el
            // sslMode PREFERRED de MySQL)
            properties.setProperty("encrypt", "true");
            properties.setProperty("trustServerCertificate", "true");
            properties.setProperty("applicationName", SharedConfigEnum.APP_NAME);
            return properties;
        }
        properties.setProperty("connectTimeout", String.valueOf(CONNECT_TIMEOUT_MS));
        properties.setProperty("socketTimeout", String.valueOf(socketTimeoutMs));
        // servidores sin TLS (local, docker) y MySQL 8 con caching_sha2_password sin TLS
        properties.setProperty("sslMode", "PREFERRED");
        properties.setProperty("allowPublicKeyRetrieval", "true");
        return properties;
    }

    public Properties getTestProperties(ConnectionEntity connectionEntity) {
        return getProperties(connectionEntity, CONNECT_TIMEOUT_MS);
    }

    private String getPgsqlDatabase(ConnectionEntity connectionEntity) {
        String databaseName = connectionEntity.getDatabaseName();
        return databaseName.isEmpty() ? DbEngineEnum.PGSQL_DEFAULT_DATABASE : databaseName;
    }
}
