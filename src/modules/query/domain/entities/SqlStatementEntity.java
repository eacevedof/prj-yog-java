package modules.query.domain.entities;

import java.util.Map;

import static modules.shared.application.Primitives.getInt;
import static modules.shared.application.Primitives.getTrimmedString;

/**
 * Una sentencia del editor ya troceada: su SQL (sin el delimitador) y donde empieza y acaba en el texto del editor
 * (endIndex incluye el delimitador). Las posiciones sirven para saber cual es la sentencia "actual" (F9).
 */
public final class SqlStatementEntity {

    private final String sql;
    private final int startIndex;
    private final int endIndex;
    private final String delimiter;

    private SqlStatementEntity(Map<String, Object> primitives) {
        this.sql = getTrimmedString(primitives.get("sql"));
        this.startIndex = getInt(primitives.get("startIndex"));
        this.endIndex = getInt(primitives.get("endIndex"));
        this.delimiter = primitives.get("delimiter") == null ? ";" : String.valueOf(primitives.get("delimiter"));
    }

    public static SqlStatementEntity fromPrimitives(Map<String, Object> primitives) {
        return new SqlStatementEntity(primitives);
    }

    public String getSql() {
        return sql;
    }

    public int getStartIndex() {
        return startIndex;
    }

    public int getEndIndex() {
        return endIndex;
    }

    /**
     * El delimitador vigente al cerrarse la sentencia (";" o el de un DELIMITER anterior): por donde sigue el troceo de
     * un fichero leido por trozos.
     */
    public String getDelimiter() {
        return delimiter;
    }
}
