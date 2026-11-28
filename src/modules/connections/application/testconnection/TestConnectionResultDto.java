package modules.connections.application.testconnection;

import java.util.Map;

import static modules.shared.application.Primitives.getTrimmedString;

public final class TestConnectionResultDto {

    private final String serverVersion;
    private final String message;

    private TestConnectionResultDto(Map<String, Object> primitives) {
        this.serverVersion = getTrimmedString(primitives.get("serverVersion"));
        this.message = getTrimmedString(primitives.get("message"));
    }

    public static TestConnectionResultDto fromPrimitives(Map<String, Object> primitives) {
        return new TestConnectionResultDto(primitives);
    }

    public String getServerVersion() {
        return serverVersion;
    }

    public String getMessage() {
        return message;
    }
}
