package modules.workspace.application.getquerytabs;

import java.util.Map;

import static modules.shared.application.Primitives.getTrimmedString;

public final class GetQueryTabsDto {

    private final String connectionUuid;

    private GetQueryTabsDto(Map<String, Object> primitives) {
        this.connectionUuid = getTrimmedString(primitives.get("connectionUuid"));
    }

    public static GetQueryTabsDto fromPrimitives(Map<String, Object> primitives) {
        return new GetQueryTabsDto(primitives);
    }

    public String getConnectionUuid() {
        return connectionUuid;
    }
}
