package modules.query.domain.entities;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static modules.shared.application.Primitives.getBool;
import static modules.shared.application.Primitives.getTrimmedString;

/**
 * Una sentencia con parametros enlazados (?) y sus valores en orden (null = NULL). isOneRowExpected: tiene que tocar
 * exactamente una fila (UPDATE y DELETE por clave primaria); si no, la transaccion se deshace. Los valores NO se
 * recortan: son los de las celdas tal cual.
 */
public final class PreparedSqlEntity {

    private final String sql;
    private final List<Object> params;
    private final boolean isOneRowExpected;

    private PreparedSqlEntity(Map<String, Object> primitives) {
        this.sql = getTrimmedString(primitives.get("sql"));
        this.params = primitives.get("params") instanceof List<?> list
            ? Collections.unmodifiableList(new ArrayList<>(list))
            : List.of();
        this.isOneRowExpected = getBool(primitives.get("isOneRowExpected"));
    }

    public static PreparedSqlEntity fromPrimitives(Map<String, Object> primitives) {
        return new PreparedSqlEntity(primitives);
    }

    public String getSql() {
        return sql;
    }

    public List<Object> getParams() {
        return params;
    }

    public boolean isOneRowExpected() {
        return isOneRowExpected;
    }
}
