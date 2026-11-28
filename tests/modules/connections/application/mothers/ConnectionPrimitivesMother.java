package modules.connections.application.mothers;

import java.util.Map;

import modules.shared.application.Primitives;

/**
 * Datos de formulario de una conexion valida; cada test cambia solo lo que le interesa con Primitives.merge.
 */
public final class ConnectionPrimitivesMother {

    private ConnectionPrimitivesMother() {
    }

    public static Map<String, Object> getValidLocal(String connectionName) {
        return Primitives.of(
            "connectionName", connectionName,
            "dbEngine", "mysql",
            "host", "127.0.0.1",
            "port", "3306",
            "userName", "root",
            "userPassword", "Secret.1234",
            "databaseName", "db_test",
            "colorHex", "#e53935",
            "isProduction", false
        );
    }
}
