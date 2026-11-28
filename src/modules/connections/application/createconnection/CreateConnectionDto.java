package modules.connections.application.createconnection;

import java.util.Map;

import static modules.shared.application.Primitives.getBool;
import static modules.shared.application.Primitives.getInt;
import static modules.shared.application.Primitives.getTrimmedString;

import modules.connections.application.TunnelFormPrimitives;

public final class CreateConnectionDto {

    private final String connectionName;
    private final String dbEngine;
    private final String host;
    private final int port;
    private final String userName;
    private final String userPassword;
    private final String databaseName;
    private final String colorHex;
    private final boolean isProduction;
    private final Map<String, Object> tunnelForm;

    private CreateConnectionDto(Map<String, Object> primitives) {
        this.connectionName = getTrimmedString(primitives.get("connectionName"));
        this.dbEngine = getTrimmedString(primitives.get("dbEngine"));
        this.host = getTrimmedString(primitives.get("host"));
        this.port = getInt(primitives.get("port"));
        this.userName = getTrimmedString(primitives.get("userName"));
        Object password = primitives.get("userPassword");
        this.userPassword = password == null ? "" : String.valueOf(password);
        this.databaseName = getTrimmedString(primitives.get("databaseName"));
        this.colorHex = getTrimmedString(primitives.get("colorHex"));
        this.isProduction = getBool(primitives.get("isProduction"));
        this.tunnelForm = TunnelFormPrimitives.getInstance().getFormValues(primitives);
    }

    public static CreateConnectionDto fromPrimitives(Map<String, Object> primitives) {
        return new CreateConnectionDto(primitives);
    }

    public String getConnectionName() {
        return connectionName;
    }

    public String getDbEngine() {
        return dbEngine;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getUserName() {
        return userName;
    }

    public String getUserPassword() {
        return userPassword;
    }

    public String getDatabaseName() {
        return databaseName;
    }

    public String getColorHex() {
        return colorHex;
    }

    public boolean isProduction() {
        return isProduction;
    }

    /**
     * Los tuneles del formulario (SSH y HTTP), ya tipado: lo pasa a la entidad TunnelFormPrimitives.
     */
    public Map<String, Object> getTunnelForm() {
        return tunnelForm;
    }
}
