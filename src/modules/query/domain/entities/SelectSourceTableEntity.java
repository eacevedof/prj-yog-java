package modules.query.domain.entities;

import java.util.Map;

import static modules.shared.application.Primitives.getTrimmedString;

/**
 * La tabla de la que sale un SELECT editable (SELECT de una sola tabla, sin JOIN ni agrupar): su BD si venia
 * cualificada ("db.tabla"; vacia si no, se usa la activa) y su nombre, ya sin comillas.
 */
public final class SelectSourceTableEntity {

    private final String databaseName;
    private final String tableName;

    private SelectSourceTableEntity(Map<String, Object> primitives) {
        this.databaseName = getTrimmedString(primitives.get("databaseName"));
        this.tableName = getTrimmedString(primitives.get("tableName"));
    }

    public static SelectSourceTableEntity fromPrimitives(Map<String, Object> primitives) {
        return new SelectSourceTableEntity(primitives);
    }

    public String getDatabaseName() {
        return databaseName;
    }

    public String getTableName() {
        return tableName;
    }
}
