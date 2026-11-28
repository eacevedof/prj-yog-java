package modules.query.domain.entities;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Una sentencia con parametros enlazados (?) y sus valores en orden (null = NULL). expectsOneRow: tiene que tocar
 * exactamente una fila (UPDATE y DELETE por clave primaria); si no, la transaccion se deshace.
 */
public final class PreparedSqlEntity {

    private final String sql;
    private final List<Object> params;
    private final boolean expectsOneRow;

    private PreparedSqlEntity(String sql, List<Object> params, boolean expectsOneRow) {
        this.sql = sql;
        this.params = Collections.unmodifiableList(new ArrayList<>(params));
        this.expectsOneRow = expectsOneRow;
    }

    public static PreparedSqlEntity of(String sql, List<Object> params, boolean expectsOneRow) {
        return new PreparedSqlEntity(sql, params, expectsOneRow);
    }

    public String getSql() {
        return sql;
    }

    public List<Object> getParams() {
        return params;
    }

    public boolean expectsOneRow() {
        return expectsOneRow;
    }
}
