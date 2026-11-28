package modules.files.domain.entities;

import java.util.List;
import java.util.Map;

import static modules.shared.application.Primitives.getBool;
import static modules.shared.application.Primitives.getTrimmedString;
import static modules.shared.application.Primitives.getUntrimmedString;

import modules.files.domain.enums.FilesKeyEnum;

/**
 * Un fichero de la carpeta leido como tabla: como estaba escrito (formato, separador, saltos de linea, BOM), sus
 * columnas con su tipo (FilesColumnKindEnum) y sus filas, con los valores ya tipados (Long, Double, String o null).
 * explicitNullColumns: en un JSON, las claves que traian null escrito; en las demas, un NULL es que la clave no estaba
 * y al guardar se omite. El separador y el salto de linea NO se recortan: son datos donde el blanco importa ("\t",
 * "\n"); tampoco los nombres de columna, que se escriben tal cual en el fichero.
 */
public final class FileTableEntity {

    private final String format;
    private final String csvDelimiter;
    private final String lineSeparator;
    private final boolean hasBom;
    private final List<String> columnNames;
    private final List<String> columnKinds;
    private final List<String> explicitNullColumns;
    private final List<List<Object>> rows;

    private FileTableEntity(Map<String, Object> primitives) {
        this.format = getTrimmedString(primitives.get(FilesKeyEnum.FILE_FORMAT));
        this.csvDelimiter = getUntrimmedString(primitives.get(FilesKeyEnum.CSV_DELIMITER));
        this.lineSeparator = getUntrimmedString(primitives.get(FilesKeyEnum.LINE_SEPARATOR));
        this.hasBom = getBool(primitives.get(FilesKeyEnum.HAS_BOM));
        this.columnNames = getList(primitives.get(FilesKeyEnum.COLUMN_NAMES));
        this.columnKinds = getList(primitives.get(FilesKeyEnum.COLUMN_KINDS));
        this.explicitNullColumns = getList(primitives.get(FilesKeyEnum.EXPLICIT_NULL_COLUMNS));
        this.rows = getList(primitives.get(FilesKeyEnum.ROWS));
    }

    public static FileTableEntity fromPrimitives(Map<String, Object> primitives) {
        return new FileTableEntity(primitives);
    }

    @SuppressWarnings("unchecked")
    private <T> List<T> getList(Object value) {
        return value instanceof List<?> list ? (List<T>) list : List.of();
    }

    public String getFormat() {
        return format;
    }

    public String getCsvDelimiter() {
        return csvDelimiter;
    }

    public String getLineSeparator() {
        return lineSeparator;
    }

    public boolean hasBom() {
        return hasBom;
    }

    public List<String> getColumnNames() {
        return columnNames;
    }

    public List<String> getColumnKinds() {
        return columnKinds;
    }

    public List<String> getExplicitNullColumns() {
        return explicitNullColumns;
    }

    public List<List<Object>> getRows() {
        return rows;
    }
}
