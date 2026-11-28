package modules.query.infrastructure.components;

import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Las sentencias que se estan ejecutando, por clave de sesion, para poder cancelarlas desde el hilo de la UI.
 * Statement.cancel() de Connector/J abre otra conexion y lanza KILL QUERY: la sesion sigue viva. Ademas se apunta
 * que se pidio cancelar, para que "ejecutar todo" no siga con la siguiente sentencia.
 */
public final class RunningStatementsComponent {

    private static final Map<String, Statement> STATEMENTS_BY_SESSION_KEY = new ConcurrentHashMap<>();
    private static final Set<String> CANCELLED_SESSION_KEYS = ConcurrentHashMap.newKeySet();

    private RunningStatementsComponent() {
    }

    public static RunningStatementsComponent getInstance() {
        return new RunningStatementsComponent();
    }

    public void startRun(String sessionKey) {
        CANCELLED_SESSION_KEYS.remove(sessionKey);
    }

    public void register(String sessionKey, Statement statement) {
        STATEMENTS_BY_SESSION_KEY.put(sessionKey, statement);
    }

    public void unregister(String sessionKey) {
        STATEMENTS_BY_SESSION_KEY.remove(sessionKey);
    }

    public boolean isCancelled(String sessionKey) {
        return CANCELLED_SESSION_KEYS.contains(sessionKey);
    }

    /**
     * true si habia una sentencia en marcha y se le pidio al servidor que la corte.
     */
    public boolean cancel(String sessionKey) throws SQLException {
        CANCELLED_SESSION_KEYS.add(sessionKey);
        Statement statement = STATEMENTS_BY_SESSION_KEY.get(sessionKey);
        if (statement == null) return false;
        statement.cancel();
        return true;
    }
}
