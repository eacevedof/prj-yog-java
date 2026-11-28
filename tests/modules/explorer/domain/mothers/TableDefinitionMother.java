package modules.explorer.domain.mothers;

import java.util.List;

import modules.explorer.domain.enums.ForeignKeyActionEnum;
import modules.explorer.domain.enums.TableIndexTypeEnum;
import modules.explorer.domain.entities.TableColumnEntity;
import modules.explorer.domain.entities.TableDefinitionEntity;
import modules.explorer.domain.entities.TableForeignKeyEntity;
import modules.explorer.domain.entities.TableIndexEntity;

/**
 * Tablas para "Crear tabla": orders con id auto incremental, customer_id con indice y clave foranea a customers(id), y
 * total con UNIQUE (total, id).
 */
public final class TableDefinitionMother {

    private TableDefinitionMother() {
    }

    public static TableDefinitionEntity getOrders(String idType, String totalComment, String tableComment) {
        return new TableDefinitionEntity(
            "orders",
            List.of(
                new TableColumnEntity("", "id", idType, "", false, false, true, true, "", "", "", "", "", false),
                new TableColumnEntity("", "customer_id", "int", "", false, false, false, false, "", "", "", "", "", false),
                new TableColumnEntity("", "total", "decimal", "10,2", false, false, true, false, "0", "", "", "", totalComment, false)
            ),
            List.of("id"),
            List.of(
                new TableIndexEntity("", "ix_orders_customer", TableIndexTypeEnum.INDEX, List.of("customer_id")),
                new TableIndexEntity("", "uq_orders_total", TableIndexTypeEnum.UNIQUE, List.of("total", "id"))
            ),
            List.of(new TableForeignKeyEntity("", "fk_orders_customer", List.of("customer_id"), "customers", List.of("id"), ForeignKeyActionEnum.CASCADE, ForeignKeyActionEnum.RESTRICT)),
            "", "", tableComment, "", ""
        );
    }
}
