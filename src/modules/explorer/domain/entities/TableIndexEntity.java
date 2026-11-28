package modules.explorer.domain.entities;

import java.util.List;

/**
 * Un indice que no es la clave primaria (la PK va en las columnas, como en SQLyog).
 *
 * @param originalName el nombre en la BD; vacio en uno nuevo
 * @param indexType    TableIndexTypeEnum: INDEX, UNIQUE, FULLTEXT o SPATIAL
 * @param columns      las columnas en orden; una puede llevar prefijo, "nombre(10)"
 */
public record TableIndexEntity(String originalName, String name, String indexType, List<String> columns) {

    public boolean isNew() {
        return originalName.isEmpty();
    }
}
