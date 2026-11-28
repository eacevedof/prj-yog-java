package modules.files.domain.entities;

import java.util.List;

/**
 * Un fichero de la carpeta leido como tabla: como estaba escrito (formato, separador, saltos de linea, BOM), sus
 * columnas con su tipo (FilesColumnKindEnum) y sus filas, con los valores ya tipados (Long, Double, String o null).
 * explicitNullColumns: en un JSON, las claves que traian null escrito; en las demas, un NULL es que la clave no estaba
 * y al guardar se omite.
 */
public record FileTableEntity(
    String format,
    String csvDelimiter,
    String lineSeparator,
    boolean hasBom,
    List<String> columnNames,
    List<String> columnKinds,
    List<String> explicitNullColumns,
    List<List<Object>> rows
) {
}
