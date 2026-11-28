package modules.connections.infrastructure.components;

import java.util.List;
import java.util.concurrent.TimeUnit;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoCredential;
import com.mongodb.ServerAddress;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.connection.ClusterConnectionMode;

import org.bson.Document;

import modules.connections.domain.entities.ConnectionEntity;

/**
 * Como se abre un cliente MongoDB para una conexion guardada. El Host puede ser un servidor (con su puerto: conexion
 * directa a ese nodo, como directConnection=true, que es lo que funciona por un tunel SSH) o una cadena
 * mongodb:// o mongodb+srv:// sin credenciales (Atlas, replica sets). Usuario y password, si van, se autentican contra
 * admin salvo que la cadena diga otro authSource.
 */
public final class MongoClientComponent {

    private static final int CONNECT_TIMEOUT_MS = 8000;
    private static final String APPLICATION_NAME = "multidbs";
    private static final String DEFAULT_AUTH_SOURCE = "admin";

    private MongoClientComponent() {
    }

    public static MongoClientComponent getInstance() {
        return new MongoClientComponent();
    }

    /**
     * Con tunel SSH (feature 12) se entra por 127.0.0.1 y el puerto local del tunel. socketTimeoutMs 0 = sin limite.
     * El ping fuerza la conexion y la autenticacion aqui, no en la primera consulta.
     * @throws com.mongodb.MongoException si no conecta o no autentica
     */
    public MongoClient open(ConnectionEntity connectionEntity, String host, int port, int socketTimeoutMs) {
        MongoClientSettings.Builder settingsBuilder = MongoClientSettings.builder()
            .applicationName(APPLICATION_NAME)
            .applyToSocketSettings(socketSettings -> socketSettings
                .connectTimeout(CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .readTimeout(socketTimeoutMs, TimeUnit.MILLISECONDS))
            .applyToClusterSettings(clusterSettings -> clusterSettings.serverSelectionTimeout(CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS));
        String authSource = DEFAULT_AUTH_SOURCE;
        if (isConnectionString(host)) {
            ConnectionString connectionString = new ConnectionString(host);
            settingsBuilder.applyConnectionString(connectionString);
            String uriAuthSource = getUriOption(host, "authSource");
            if (uriAuthSource != null) authSource = uriAuthSource;
        }
        else {
            settingsBuilder.applyToClusterSettings(clusterSettings -> clusterSettings
                .hosts(List.of(new ServerAddress(host, port)))
                .mode(ClusterConnectionMode.SINGLE));
        }
        if (!connectionEntity.getUserName().isEmpty()) {
            settingsBuilder.credential(MongoCredential.createCredential(
                connectionEntity.getUserName(), authSource, connectionEntity.getUserPassword().toCharArray()
            ));
        }
        MongoClient mongoClient = MongoClients.create(settingsBuilder.build());
        try {
            mongoClient.getDatabase(DEFAULT_AUTH_SOURCE).runCommand(new Document("ping", 1));
        }
        catch (RuntimeException e) {
            mongoClient.close();
            throw e;
        }
        return mongoClient;
    }

    public boolean isConnectionString(String host) {
        return host.startsWith("mongodb://") || host.startsWith("mongodb+srv://");
    }

    public int getTestSocketTimeoutMs() {
        return CONNECT_TIMEOUT_MS;
    }

    public String getServerVersion(MongoClient mongoClient) {
        Document buildInfo = mongoClient.getDatabase(DEFAULT_AUTH_SOURCE).runCommand(new Document("buildInfo", 1));
        return "MongoDB " + buildInfo.getString("version");
    }

    private String getUriOption(String uri, String optionName) {
        int queryStart = uri.indexOf('?');
        if (queryStart == -1) return null;
        for (String option : uri.substring(queryStart + 1).split("&")) {
            String[] nameValue = option.split("=", 2);
            if (nameValue.length == 2 && nameValue[0].equalsIgnoreCase(optionName)) return nameValue[1];
        }
        return null;
    }
}
