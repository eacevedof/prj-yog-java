package modules.explorer.domain.entities;

import java.util.List;

/**
 * Una tabla tal como la edita el "Alter Table": nombre, columnas en orden, clave primaria, indices, claves foraneas y
 * las opciones de la pestaña Avanzado. autoIncrement vacio = no se toca.
 */
public record TableDefinitionEntity(
    String tableName,
    List<TableColumnEntity> columns,
    List<String> primaryKeyColumns,
    List<TableIndexEntity> indexes,
    List<TableForeignKeyEntity> foreignKeys,
    String engine,
    String collation,
    String comment,
    String autoIncrement,
    String rowFormat
) {
}
