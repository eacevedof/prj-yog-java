package modules.explorer.infrastructure.repositories;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import modules.shared.infrastructure.repositories.AbstractJdbcRepository;
import static modules.shared.application.Primitives.getTrimmedString;

/**
 * Lo que pinta el arbol y la pestaña Info, un repositorio por motor SQL. Todos devuelven las mismas claves (las de
 * MySQL: table_type, engine, table_rows, column_key = PRI/UNI, indice "PRIMARY"...) para que servicios y UI no
 * distingan el motor. En PostgreSQL, "BD" es un esquema; en SQLite hay una sola, "main".
 */
public abstract class AbstractExplorerReaderRepository extends AbstractJdbcRepository {

    protected AbstractExplorerReaderRepository(Connection pdo) {
        super(pdo);
    }

    protected AbstractExplorerReaderRepository(Connection pdo, String dbEngine) {
        super(pdo, dbEngine);
    }

    public abstract List<String> getDatabaseNames() throws SQLException;

    /**
     * [tables => [...], views => [...]] de una BD, por nombre.
     */
    public abstract Map<String, Object> getTablesAndViews(String databaseName) throws SQLException;

    /**
     * [procedures => [...], functions => [...]] de una BD, por nombre.
     */
    public abstract Map<String, Object> getRoutines(String databaseName) throws SQLException;

    public abstract List<String> getTriggerNames(String databaseName) throws SQLException;

    /**
     * Las columnas de una tabla o vista, en su orden (claves de ExplorerKeyEnum).
     */
    public abstract List<Map<String, Object>> getTableColumns(String databaseName, String tableName) throws SQLException;

    /**
     * table_type (BASE TABLE / VIEW), engine, table_rows, data_length, index_length... Vacio si no existe.
     */
    public abstract Map<String, Object> getTableStatus(String databaseName, String tableName) throws SQLException;

    /**
     * Un indice por fila y columna (index_name, non_unique, seq_in_index, column_name, index_type); la clave primaria
     * se llama "PRIMARY".
     */
    public abstract List<Map<String, Object>> getIndexes(String databaseName, String tableName) throws SQLException;

    public abstract String getCreateStatement(String databaseName, String tableName, boolean isView) throws SQLException;

    /**
     * Las columnas de la clave primaria, en su orden. Vacio = no tiene (no se puede editar en la rejilla).
     */
    public abstract Set<String> getPrimaryKeyColumns(String databaseName, String tableName) throws SQLException;

    protected List<String> getColumnValues(List<Map<String, Object>> rows, String columnName) {
        List<String> values = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            values.add(getTrimmedString(row.get(columnName)));
        }
        return values;
    }
}
