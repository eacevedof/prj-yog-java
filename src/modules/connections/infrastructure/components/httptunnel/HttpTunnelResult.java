package modules.connections.infrastructure.components.httptunnel;

import java.util.List;
import java.util.Map;

import static modules.shared.application.Primitives.getBool;
import static modules.shared.application.Primitives.getTrimmedString;

/**
 * Un bloque &lt;result&gt; de la respuesta del tunel: una sentencia o un result set. Las celdas llegan como bytes
 * (el tunel las manda en base64) y null es NULL; el texto de una celda lo da HttpTunnelCellDecoder.
 *
 * columnNames:  &lt;n&gt; de cada &lt;f&gt;
 * columnTypes:  &lt;ty&gt;: el tipo literal de SQLyog ("int", "varchar", "blob"...)
 * columnTables: &lt;t&gt;: la tabla de la columna; en Redis, el tipo de respuesta (string, integer, nil, array)
 * rows:         una lista de celdas por fila
 * hasResultSet: false en un INSERT/UPDATE/DDL (&lt;f_i c="0"&gt;)
 */
public final class HttpTunnelResult {

    private final List<String> columnNames;
    private final List<String> columnTypes;
    private final List<String> columnTables;
    private final List<List<byte[]>> rows;
    private final boolean hasResultSet;
    private final long affectedRows;
    private final String insertId;
    private final String serverVersion;

    private HttpTunnelResult(Map<String, Object> primitives) {
        this.columnNames = getList(primitives.get("columnNames"));
        this.columnTypes = getList(primitives.get("columnTypes"));
        this.columnTables = getList(primitives.get("columnTables"));
        this.rows = getList(primitives.get("rows"));
        this.hasResultSet = getBool(primitives.get("hasResultSet"));
        this.affectedRows = primitives.get("affectedRows") instanceof Number number ? number.longValue() : 0;
        this.insertId = getTrimmedString(primitives.get("insertId"));
        this.serverVersion = getTrimmedString(primitives.get("serverVersion"));
    }

    public static HttpTunnelResult fromPrimitives(Map<String, Object> primitives) {
        return new HttpTunnelResult(primitives);
    }

    @SuppressWarnings("unchecked")
    private <T> List<T> getList(Object value) {
        return value instanceof List<?> list ? (List<T>) list : List.of();
    }

    public List<String> getColumnNames() {
        return columnNames;
    }

    public List<String> getColumnTypes() {
        return columnTypes;
    }

    public List<String> getColumnTables() {
        return columnTables;
    }

    public List<List<byte[]>> getRows() {
        return rows;
    }

    public boolean hasResultSet() {
        return hasResultSet;
    }

    public long getAffectedRows() {
        return affectedRows;
    }

    public String getInsertId() {
        return insertId;
    }

    public String getServerVersion() {
        return serverVersion;
    }
}
