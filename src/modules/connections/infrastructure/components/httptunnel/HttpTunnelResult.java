package modules.connections.infrastructure.components.httptunnel;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Un bloque &lt;result&gt; de la respuesta del tunel: una sentencia o un result set. Las celdas llegan como bytes
 * (el tunel las manda en base64) y null es NULL.
 *
 * @param columnNames  &lt;n&gt; de cada &lt;f&gt;
 * @param columnTypes  &lt;ty&gt;: el tipo literal de SQLyog ("int", "varchar", "blob"...)
 * @param columnTables &lt;t&gt;: la tabla de la columna; en Redis, el tipo de respuesta (string, integer, nil, array)
 * @param rows         una lista de celdas por fila
 * @param hasResultSet false en un INSERT/UPDATE/DDL (&lt;f_i c="0"&gt;)
 */
public record HttpTunnelResult(
    List<String> columnNames,
    List<String> columnTypes,
    List<String> columnTables,
    List<List<byte[]>> rows,
    boolean hasResultSet,
    long affectedRows,
    String insertId,
    String serverVersion
) {

    public static String getText(byte[] cell) {
        return cell == null ? null : new String(cell, StandardCharsets.UTF_8);
    }
}
