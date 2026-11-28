package modules.explorer.domain.entities;

/**
 * Una columna en el editor de tablas (MySQL/MariaDB), partida como en el "Alter Table" de SQLyog: tipo base, longitud o
 * valores ("80", "10,2", "'a','b'"), flags, DEFAULT como fragmento SQL tal cual va tras DEFAULT ("'pendiente'", "0",
 * "NULL", "CURRENT_TIMESTAMP"; vacio = sin DEFAULT) y ON UPDATE igual.
 *
 * @param originalName   el nombre en la BD; vacio en una columna nueva
 * @param characterSet   y collation: los de la columna (se repiten al redefinirla para no perderlos); vacios si no tiene
 * @param isGenerated    columna calculada: se ve pero no se edita (su expresion no se rehace)
 */
public record TableColumnEntity(
    String originalName,
    String name,
    String dataType,
    String length,
    boolean isUnsigned,
    boolean isZerofill,
    boolean isNotNull,
    boolean isAutoIncrement,
    String defaultSql,
    String onUpdateSql,
    String characterSet,
    String collation,
    String comment,
    boolean isGenerated
) {

    public boolean isNew() {
        return originalName.isEmpty();
    }

    public TableColumnEntity withName(String newName) {
        return new TableColumnEntity(originalName, newName, dataType, length, isUnsigned, isZerofill, isNotNull, isAutoIncrement,
            defaultSql, onUpdateSql, characterSet, collation, comment, isGenerated);
    }
}
