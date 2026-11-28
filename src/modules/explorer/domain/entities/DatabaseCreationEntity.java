package modules.explorer.domain.entities;

import java.util.Map;

import static modules.shared.application.Primitives.getBool;
import static modules.shared.application.Primitives.getTrimmedString;

/**
 * La BD que se va a crear y sus opciones. Cada motor usa las suyas: MySQL/MariaDB juego de caracteres, cotejamiento,
 * cifrado por defecto (MySQL 8: "" = no se escribe, "Y" o "N") y comentario (MariaDB 10.5+); PostgreSQL (un esquema)
 * el propietario; SQL Server el cotejamiento. Lo vacio no se escribe en el CREATE.
 */
public record DatabaseCreationEntity(
    String databaseName,
    String characterSet,
    String collation,
    String encryption,
    String comment,
    String owner,
    boolean isIfNotExists
) {

    public static final String ENCRYPTION_YES = "Y";
    public static final String ENCRYPTION_NO = "N";

    public static DatabaseCreationEntity fromPrimitives(Map<String, Object> primitives) {
        return new DatabaseCreationEntity(
            getTrimmedString(primitives.get("databaseName")),
            getTrimmedString(primitives.get("characterSet")),
            getTrimmedString(primitives.get("collation")),
            getTrimmedString(primitives.get("encryption")),
            getTrimmedString(primitives.get("comment")),
            getTrimmedString(primitives.get("owner")),
            getBool(primitives.get("isIfNotExists"))
        );
    }
}
