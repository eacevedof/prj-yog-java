package modules.explorer.domain.entities;

import java.util.List;

/**
 * Una clave foranea.
 *
 * @param originalName      el nombre de la restriccion en la BD; vacio en una nueva
 * @param referencedTable   la tabla padre, de la misma BD
 * @param onDelete          y onUpdate: ForeignKeyActionEnum (RESTRICT, CASCADE, SET NULL, NO ACTION)
 */
public record TableForeignKeyEntity(
    String originalName,
    String name,
    List<String> columns,
    String referencedTable,
    List<String> referencedColumns,
    String onDelete,
    String onUpdate
) {

    public boolean isNew() {
        return originalName.isEmpty();
    }
}
