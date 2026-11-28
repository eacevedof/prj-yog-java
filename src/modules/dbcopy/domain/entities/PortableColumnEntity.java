package modules.dbcopy.domain.entities;

/**
 * Copia entre motores: una columna del origen reducida a lo que cualquier motor SQL entiende (DbCopyTypeFamilyEnum).
 *
 * @param family        el tipo comun (DbCopyTypeFamilyEnum)
 * @param size          enteros: bytes (1, 2, 3, 4, 8) · coma flotante: 4 u 8 · texto y binario: longitud (0 = sin tope) ·
 *                      decimal: precision (0 = sin precision) · hora y fecha-hora: decimales de segundo
 * @param scale         decimal: decimales
 * @param isUnsigned    entero sin signo (MySQL UNSIGNED, tinyint de SQL Server)
 * @param isFixedLength char(n) / binary(n)
 * @param isNullable    admite NULL
 * @param isAutoIncrement AUTO_INCREMENT, serial/identity o IDENTITY
 * @param isCastToNumber MySQL: BIT y YEAR se leen con CAST(... AS UNSIGNED) (por JDBC llegan en bytes o como fecha)
 */
public record PortableColumnEntity(
    String name,
    String family,
    int size,
    int scale,
    boolean isUnsigned,
    boolean isFixedLength,
    boolean isNullable,
    boolean isAutoIncrement,
    boolean isCastToNumber
) {
}
