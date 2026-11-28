package modules.explorer.application.renametable;

import java.util.Map;

import static modules.shared.application.Primitives.getTrimmedString;

public final class RenameTableResultDto {

    private final String newTableName;
    private final String renameSql;

    private RenameTableResultDto(Map<String, Object> primitives) {
        this.newTableName = getTrimmedString(primitives.get("newTableName"));
        this.renameSql = getTrimmedString(primitives.get("renameSql"));
    }

    public static RenameTableResultDto fromPrimitives(Map<String, Object> primitives) {
        return new RenameTableResultDto(primitives);
    }

    /**
     * Como sale en el arbol (en SQL Server, con su esquema si lo tenia).
     */
    public String getNewTableName() {
        return newTableName;
    }

    public String getRenameSql() {
        return renameSql;
    }
}
