package modules.shared.infrastructure.components.sqltrace;

import java.util.ArrayList;
import java.util.List;

/**
 * Feature 8: lo que lanzan los repositorios contra la BD destino mientras un servicio "automatico" (arbol, Info,
 * editores, volcado) trabaja, para apuntarlo en el historial. Va por hilo: cada servicio corre en su propio hilo de
 * fondo y dos cargas a la vez no se mezclan. Sin traza abierta, apuntar no hace nada.
 */
public final class SqlTraceComponent {

    private static final ThreadLocal<SqlTrace> CURRENT_TRACE = new ThreadLocal<>();

    private SqlTraceComponent() {
    }

    public static SqlTraceComponent getInstance() {
        return new SqlTraceComponent();
    }

    /**
     * Abre la traza del hilo. Si ya habia una abierta (un servicio llamado desde otro), devuelve false: lo que se lance
     * se suma a la de fuera y la apunta quien la abrio.
     */
    public boolean start() {
        if (CURRENT_TRACE.get() != null) return false;
        CURRENT_TRACE.set(new SqlTrace());
        return true;
    }

    public boolean isTracing() {
        return CURRENT_TRACE.get() != null;
    }

    public void addSql(String sql) {
        SqlTrace sqlTrace = CURRENT_TRACE.get();
        if (sqlTrace == null) return;
        sqlTrace.sqls.add(sql);
    }

    public void addRowsCount(int rowsCount) {
        SqlTrace sqlTrace = CURRENT_TRACE.get();
        if (sqlTrace == null) return;
        sqlTrace.rowsCount += rowsCount;
    }

    /**
     * Cierra la traza del hilo y la devuelve (vacia si no habia ninguna abierta).
     */
    public SqlTrace stop() {
        SqlTrace sqlTrace = CURRENT_TRACE.get();
        CURRENT_TRACE.remove();
        return sqlTrace == null ? new SqlTrace() : sqlTrace;
    }

    public static final class SqlTrace {

        private final List<String> sqls = new ArrayList<>();
        private int rowsCount;

        public List<String> getSqls() {
            return List.copyOf(sqls);
        }

        public int getRowsCount() {
            return rowsCount;
        }
    }
}
