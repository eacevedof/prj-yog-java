package modules.mongo.domain.enums;

/**
 * Las claves de lo que mueven los repositorios y servicios de Mongo: el id de un documento, las colecciones (nombre y
 * documentos) y los documentos como tabla (columnas y filas).
 */
public final class MongoKeyEnum {

    public static final String ID = "_id";
    public static final String NAME = "name";
    public static final String COUNT = "count";
    public static final String COLUMNS = "columns";
    public static final String ROWS = "rows";

    private MongoKeyEnum() {
    }
}
