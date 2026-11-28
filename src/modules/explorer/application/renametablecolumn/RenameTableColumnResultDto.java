package modules.explorer.application.renametablecolumn;

import java.util.Map;

import static modules.shared.application.Primitives.getTrimmedString;

public final class RenameTableColumnResultDto {

    private final String renameSql;

    private RenameTableColumnResultDto(Map<String, Object> primitives) {
        this.renameSql = getTrimmedString(primitives.get("renameSql"));
    }

    public static RenameTableColumnResultDto fromPrimitives(Map<String, Object> primitives) {
        return new RenameTableColumnResultDto(primitives);
    }

    public String getRenameSql() {
        return renameSql;
    }
}
