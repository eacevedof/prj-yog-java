package modules.dbcopy.domain.entities;

import java.util.Map;

import static modules.shared.application.Primitives.getBool;
import static modules.shared.application.Primitives.getInt;
import static modules.shared.application.Primitives.getTrimmedString;
import static modules.shared.application.Primitives.getUntrimmedString;

/**
 * Copia entre motores: una columna del origen reducida a lo que cualquier motor SQL entiende (DbCopyTypeFamilyEnum).
 * El nombre NO se recorta: es el de la BD tal cual (un espacio al final es legal en un identificador entre comillas).
 *
 * family:          el tipo comun (DbCopyTypeFamilyEnum)
 * size:            enteros: bytes (1, 2, 3, 4, 8) · coma flotante: 4 u 8 · texto y binario: longitud (0 = sin tope) ·
 *                  decimal: precision (0 = sin precision) · hora y fecha-hora: decimales de segundo
 * scale:           decimal: decimales
 * isUnsigned:      entero sin signo (MySQL UNSIGNED, tinyint de SQL Server)
 * isFixedLength:   char(n) / binary(n)
 * isNullable:      admite NULL
 * isAutoIncrement: AUTO_INCREMENT, serial/identity o IDENTITY
 * isCastToNumber:  MySQL: BIT y YEAR se leen con CAST(... AS UNSIGNED) (por JDBC llegan en bytes o como fecha)
 */
public final class PortableColumnEntity {

    private final String name;
    private final String family;
    private final int size;
    private final int scale;
    private final boolean isUnsigned;
    private final boolean isFixedLength;
    private final boolean isNullable;
    private final boolean isAutoIncrement;
    private final boolean isCastToNumber;

    private PortableColumnEntity(Map<String, Object> primitives) {
        this.name = getUntrimmedString(primitives.get("name"));
        this.family = getTrimmedString(primitives.get("family"));
        this.size = getInt(primitives.get("size"));
        this.scale = getInt(primitives.get("scale"));
        this.isUnsigned = getBool(primitives.get("isUnsigned"));
        this.isFixedLength = getBool(primitives.get("isFixedLength"));
        this.isNullable = getBool(primitives.get("isNullable"));
        this.isAutoIncrement = getBool(primitives.get("isAutoIncrement"));
        this.isCastToNumber = getBool(primitives.get("isCastToNumber"));
    }

    public static PortableColumnEntity fromPrimitives(Map<String, Object> primitives) {
        return new PortableColumnEntity(primitives);
    }

    public String getName() {
        return name;
    }

    public String getFamily() {
        return family;
    }

    public int getSize() {
        return size;
    }

    public int getScale() {
        return scale;
    }

    public boolean isUnsigned() {
        return isUnsigned;
    }

    public boolean isFixedLength() {
        return isFixedLength;
    }

    public boolean isNullable() {
        return isNullable;
    }

    public boolean isAutoIncrement() {
        return isAutoIncrement;
    }

    public boolean isCastToNumber() {
        return isCastToNumber;
    }
}
