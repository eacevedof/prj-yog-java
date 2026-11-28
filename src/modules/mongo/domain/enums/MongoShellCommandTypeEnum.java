package modules.mongo.domain.enums;

/**
 * El tipo de una linea de la consola de MongoDB ya partida (MongoShellCommandEntity.type).
 */
public final class MongoShellCommandTypeEnum {

    public static final String RUN_COMMAND = "runCommand";
    public static final String SHOW_DATABASES = "showDatabases";
    public static final String SHOW_COLLECTIONS = "showCollections";
    public static final String USE = "use";
    public static final String DROP_DATABASE = "dropDatabase";
    public static final String COLLECTION_METHOD = "collectionMethod";

    private MongoShellCommandTypeEnum() {
    }
}
