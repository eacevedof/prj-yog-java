package modules.connections.infrastructure.repositories;

import java.io.File;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import com.jcraft.jsch.JSchException;

import com.mongodb.MongoException;
import com.mongodb.client.MongoClient;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.exceptions.JedisException;

import modules.shared.domain.services.SqlDialect;

import static core.config.Global.__;

import modules.connections.domain.enums.ConnectionsMessageEnum;
import modules.connections.domain.enums.DbEngineEnum;
import modules.connections.domain.entities.ConnectionEntity;
import modules.connections.infrastructure.components.MongoClientComponent;
import modules.connections.infrastructure.components.RedisClientComponent;
import modules.connections.infrastructure.components.SshTunnelComponent;
import modules.connections.infrastructure.components.TargetJdbcComponent;
import modules.connections.infrastructure.components.httptunnel.HttpTunnelClient;
import modules.connections.infrastructure.components.httptunnel.HttpTunnelResult;

/**
 * La BD destino (el servidor MySQL de una conexion guardada), no la BD local de la app.
 */
public final class TargetDbReaderJdbcRepository {

    private static final int TEST_SOCKET_TIMEOUT_MS = 8000;

    private TargetDbReaderJdbcRepository() {
    }

    public static TargetDbReaderJdbcRepository getInstance() {
        return new TargetDbReaderJdbcRepository();
    }

    /**
     * Abre y cierra una conexion (y su tunel SSH si lo lleva) y devuelve SELECT VERSION(). Lo que falle (host,
     * credenciales, red, tunel) sube como SQLException para que el servicio lo traduzca.
     */
    public String getServerVersion(ConnectionEntity connectionEntity) throws SQLException {
        if (connectionEntity.isHttpEnabled()) return getServerVersionByHttpTunnel(connectionEntity);
        if (DbEngineEnum.REDIS.equals(connectionEntity.getDbEngine())) return getRedisServerVersion(connectionEntity);
        if (DbEngineEnum.MONGODB.equals(connectionEntity.getDbEngine())) return getMongoServerVersion(connectionEntity);
        if (DbEngineEnum.FILES.equals(connectionEntity.getDbEngine())) return getFilesFolderSummary(connectionEntity);
        SshTunnelComponent sshTunnelComponent = SshTunnelComponent.getInstance();
        SshTunnelComponent.OpenTunnel openTunnel = null;
        try {
            String host = connectionEntity.getHost();
            int port = connectionEntity.getPort();
            if (connectionEntity.isSshEnabled()) {
                openTunnel = openTunnel(connectionEntity);
                host = SshTunnelComponent.LOCAL_HOST;
                port = openTunnel.localPort();
            }
            String versionSql = SqlDialect.getInstance(connectionEntity.getDbEngine()).getServerVersionSql();
            try (Connection connection = TargetJdbcComponent.getInstance().openConnection(connectionEntity, host, port, TEST_SOCKET_TIMEOUT_MS);
                 Statement statement = connection.createStatement();
                 ResultSet resultSet = statement.executeQuery(versionSql)) {
                return resultSet.next() ? resultSet.getString(1) : "";
            }
        }
        finally {
            sshTunnelComponent.close(openTunnel);
        }
    }

    /**
     * Redis: abre y cierra un cliente (con su tunel SSH si lo lleva) y lee la version de INFO server. Los errores de
     * Jedis suben como SQLException para que el servicio los trate igual que los de JDBC.
     */
    private String getRedisServerVersion(ConnectionEntity connectionEntity) throws SQLException {
        SshTunnelComponent.OpenTunnel openTunnel = null;
        try {
            String host = connectionEntity.getHost();
            int port = connectionEntity.getPort();
            if (connectionEntity.isSshEnabled()) {
                openTunnel = openTunnel(connectionEntity);
                host = SshTunnelComponent.LOCAL_HOST;
                port = openTunnel.localPort();
            }
            RedisClientComponent redisClientComponent = RedisClientComponent.getInstance();
            try (Jedis jedis = redisClientComponent.open(connectionEntity, host, port, redisClientComponent.getTestSocketTimeoutMs())) {
                return redisClientComponent.getServerVersion(jedis);
            }
        }
        catch (JedisException e) {
            throw new SQLException(e.getMessage(), e);
        }
        finally {
            SshTunnelComponent.getInstance().close(openTunnel);
        }
    }

    /**
     * Ficheros: no hay servidor; probar es ver que la carpeta existe y cuantos CSV y JSON tiene, o que el fichero
     * concreto existe y es de datos.
     * @throws SQLException si no existe
     */
    private String getFilesFolderSummary(ConnectionEntity connectionEntity) throws SQLException {
        File folder = new File(connectionEntity.getDatabaseName());
        if (folder.isFile() && DbEngineEnum.FILES_EXTENSIONS.contains(getExtension(folder.getName()))) {
            return __(ConnectionsMessageEnum.FILES_FILE_X_OK, folder.getAbsolutePath());
        }
        File[] folderFiles = folder.listFiles();
        if (!folder.isDirectory() || folderFiles == null) {
            throw new SQLException(__(ConnectionsMessageEnum.FILES_FOLDER_X_NOT_FOUND, folder.getAbsolutePath()));
        }
        long dataFilesCount = Arrays.stream(folderFiles)
            .filter(File::isFile)
            .filter(file -> DbEngineEnum.FILES_EXTENSIONS.contains(getExtension(file.getName())))
            .count();
        return __(ConnectionsMessageEnum.FILES_FOLDER_X_WITH_X_FILES, folder.getAbsolutePath(), dataFilesCount);
    }

    private String getExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        return dotIndex == -1 ? "" : fileName.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }

    /**
     * MongoDB: abre y cierra un cliente (con su tunel SSH si lo lleva) y lee la version de buildInfo.
     */
    private String getMongoServerVersion(ConnectionEntity connectionEntity) throws SQLException {
        SshTunnelComponent.OpenTunnel openTunnel = null;
        try {
            String host = connectionEntity.getHost();
            int port = connectionEntity.getPort();
            if (connectionEntity.isSshEnabled()) {
                openTunnel = openTunnel(connectionEntity);
                host = SshTunnelComponent.LOCAL_HOST;
                port = openTunnel.localPort();
            }
            MongoClientComponent mongoClientComponent = MongoClientComponent.getInstance();
            try (MongoClient mongoClient = mongoClientComponent.open(connectionEntity, host, port, mongoClientComponent.getTestSocketTimeoutMs())) {
                return mongoClientComponent.getServerVersion(mongoClient);
            }
        }
        catch (MongoException e) {
            throw new SQLException(e.getMessage(), e);
        }
        finally {
            SshTunnelComponent.getInstance().close(openTunnel);
        }
    }

    /**
     * Feature 13: primero el ping del tunel (URL y token) y luego la BD a traves de el.
     */
    private String getServerVersionByHttpTunnel(ConnectionEntity connectionEntity) throws SQLException {
        HttpTunnelClient httpTunnelClient = HttpTunnelClient.getInstance(connectionEntity);
        String tunnelVersion = httpTunnelClient.getTunnelVersion();
        String dbEngine = connectionEntity.getDbEngine();
        // la consulta de prueba de cada motor; SQL Server, Redis y MongoDB dan su version en <s_v>
        String probe = switch (dbEngine) {
            case DbEngineEnum.MSSQL -> "SELECT 1";
            case DbEngineEnum.REDIS -> "PING";
            case DbEngineEnum.MONGODB -> "{\"ping\": 1}";
            default -> "SELECT VERSION()";
        };
        String databaseName = DbEngineEnum.MONGODB.equals(dbEngine) ? DbEngineEnum.MONGODB_ADMIN_DATABASE : connectionEntity.getDatabaseName();
        List<HttpTunnelResult> results = httpTunnelClient.execute(probe, databaseName, false);
        if (results.isEmpty()) return "(HTTP tunnel " + tunnelVersion + ")";
        HttpTunnelResult firstResult = results.getFirst();
        boolean isVersionInRows = !DbEngineEnum.MSSQL.equals(dbEngine) && !DbEngineEnum.REDIS.equals(dbEngine) && !DbEngineEnum.MONGODB.equals(dbEngine);
        String serverVersion = isVersionInRows
            ? (firstResult.rows().isEmpty() ? "" : HttpTunnelResult.getText(firstResult.rows().getFirst().getFirst()))
            : firstResult.serverVersion();
        return serverVersion + " (HTTP tunnel " + tunnelVersion + ")";
    }

    /**
     * El fallo del tunel sube como SQLException con su propio mensaje: asi se distingue de un fallo de MySQL.
     */
    private SshTunnelComponent.OpenTunnel openTunnel(ConnectionEntity connectionEntity) throws SQLException {
        try {
            return SshTunnelComponent.getInstance().open(connectionEntity);
        }
        catch (JSchException e) {
            throw new SQLException(__(ConnectionsMessageEnum.SSH_TUNNEL_FAILED_X, e.getMessage()), e);
        }
    }
}
