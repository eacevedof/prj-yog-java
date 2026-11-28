package modules.mongo.domain.enums;

/**
 * Topes de las lecturas de Mongo.
 */
public final class MongoConfigEnum {

    // tope de tiempo al contar documentos: en una coleccion enorme sin indice puede tardar
    public static final long COUNT_MAX_TIME_MS = 5000;

    private MongoConfigEnum() {
    }
}
