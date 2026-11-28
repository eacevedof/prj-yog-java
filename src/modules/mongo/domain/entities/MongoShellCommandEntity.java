package modules.mongo.domain.entities;

import java.util.List;

/**
 * Una linea de la consola de MongoDB ya partida: que tipo de orden es y, si va contra una coleccion, su metodo con los
 * argumentos (cada uno JSON tal cual, sin parsear: lo parsea el driver) y la cadena de modificadores
 * (.sort({...}).limit(10)).
 *
 * @param type       RUN_COMMAND, SHOW_DATABASES, SHOW_COLLECTIONS, USE, DROP_DATABASE o COLLECTION_METHOD
 * @param name       la coleccion (COLLECTION_METHOD) o la BD (USE)
 * @param method     el metodo de la coleccion (find, insertOne...) o "" si no aplica
 * @param arguments  los argumentos del metodo (o el documento de RUN_COMMAND) como JSON
 * @param modifiers  la cadena tras el metodo: [nombre, argumento JSON] en orden
 */
public record MongoShellCommandEntity(String type, String name, String method, List<String> arguments, List<List<String>> modifiers) {

    public static final String RUN_COMMAND = "runCommand";
    public static final String SHOW_DATABASES = "showDatabases";
    public static final String SHOW_COLLECTIONS = "showCollections";
    public static final String USE = "use";
    public static final String DROP_DATABASE = "dropDatabase";
    public static final String COLLECTION_METHOD = "collectionMethod";
}
