package modules.mongo.domain.enums;

/**
 * Topes y valores por defecto de las lecturas de Mongo.
 */
public final class MongoConfigEnum {

    // tope de tiempo al contar documentos: en una coleccion enorme sin indice puede tardar
    public static final long COUNT_MAX_TIME_MS = 5000;

    public static final int FIND_DEFAULT_LIMIT = 100;

    // la BD de la consola si la conexion no lleva ninguna, como mongosh
    public static final String DEFAULT_CONSOLE_DATABASE = "test";
    // lo que contesta la consola a "use <bd>", como mongosh
    public static final String SWITCHED_TO_DB_PREFIX = "switched to db ";

    private MongoConfigEnum() {
    }
}
