package modules.query.infrastructure.components;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;

import javafx.application.Platform;

import modules.shared.application.Primitives;

import modules.explorer.application.listdatabaseobjects.ListDatabaseObjectsDto;
import modules.explorer.application.listdatabaseobjects.ListDatabaseObjectsResultDto;
import modules.explorer.application.listdatabases.ListDatabasesDto;
import modules.explorer.application.listtablecolumns.ListTableColumnsDto;
import modules.explorer.application.listdatabaseobjects.ListDatabaseObjectsService;
import modules.explorer.application.listdatabases.ListDatabasesService;
import modules.explorer.application.listtablecolumns.ListTableColumnsService;

/**
 * Lo que sabe el autocompletado del esquema de una conexion (feature 14): BD, tablas de cada BD y columnas de cada
 * tabla. Se pide al servidor la primera vez que hace falta, en segundo plano y con su propia sesion; mientras llega,
 * get...() devuelve null y al llegar se avisa a onLoaded (en el hilo de JavaFX) para refrescar la lista. clear() lo
 * olvida todo (Refrescar en el arbol).
 */
public final class SchemaCacheComponent {

    private final String connectionUuid;
    private final String sessionKey;
    private final Map<String, List<String>> tableNamesByDatabase = new ConcurrentHashMap<>();
    private final Map<String, List<String>> columnNamesByTable = new ConcurrentHashMap<>();
    private final Set<String> loadingKeys = ConcurrentHashMap.newKeySet();
    private volatile List<String> databaseNames;
    private volatile Runnable onLoaded = () -> { };

    public SchemaCacheComponent(String connectionUuid, String sessionKey) {
        this.connectionUuid = connectionUuid;
        this.sessionKey = sessionKey;
    }

    public void setOnLoaded(Runnable onLoaded) {
        this.onLoaded = onLoaded;
    }

    public void clear() {
        tableNamesByDatabase.clear();
        columnNamesByTable.clear();
        databaseNames = null;
    }

    public List<String> getDatabaseNames() {
        if (databaseNames == null) {
            load("databases", () -> {
                databaseNames = ListDatabasesService.getInstance().invoke(ListDatabasesDto.fromPrimitives(Primitives.of(
                    "connectionUuid", connectionUuid, "sessionKey", sessionKey
                ))).getDatabaseNames();
                return true;
            });
        }
        return databaseNames;
    }

    /**
     * Tablas y vistas de una BD.
     */
    public List<String> getTableNames(String databaseName) {
        if (databaseName.isEmpty()) return List.of();
        List<String> tableNames = tableNamesByDatabase.get(databaseName);
        if (tableNames == null) {
            load("tables:" + databaseName, () -> {
                ListDatabaseObjectsResultDto objects = ListDatabaseObjectsService.getInstance().invoke(ListDatabaseObjectsDto.fromPrimitives(Primitives.of(
                    "connectionUuid", connectionUuid, "sessionKey", sessionKey, "databaseName", databaseName
                )));
                List<String> names = new ArrayList<>(objects.getTables());
                names.addAll(objects.getViews());
                tableNamesByDatabase.put(databaseName, names);
                return true;
            });
        }
        return tableNames;
    }

    public List<String> getColumnNames(String databaseName, String tableName) {
        if (databaseName.isEmpty() || tableName.isEmpty()) return List.of();
        String tableKey = (databaseName + "." + tableName).toLowerCase(Locale.ROOT);
        List<String> columnNames = columnNamesByTable.get(tableKey);
        if (columnNames == null) {
            load("columns:" + tableKey, () -> {
                List<String> names = new ArrayList<>();
                for (Map<String, Object> column : ListTableColumnsService.getInstance().invoke(ListTableColumnsDto.fromPrimitives(Primitives.of(
                    "connectionUuid", connectionUuid, "sessionKey", sessionKey, "databaseName", databaseName, "tableName", tableName
                ))).getColumns()) {
                    names.add(String.valueOf(column.get("column_name")));
                }
                columnNamesByTable.put(tableKey, names);
                return true;
            });
        }
        return columnNames;
    }

    /**
     * Una carga a la vez por clave. Si falla (sin permisos, tabla que no existe: los servicios dejan subir el
     * SQLException) se guarda vacia para no reintentar en cada tecla; el autocompletado es de mejor esfuerzo, como un
     * controller que no tiene a quien ensenar el error.
     */
    private void load(String loadKey, Callable<Boolean> loader) {
        if (!loadingKeys.add(loadKey)) return;
        Thread loadThread = new Thread(() -> {
            try {
                loader.call();
            }
            catch (Exception e) {
                markEmpty(loadKey);
            }
            finally {
                loadingKeys.remove(loadKey);
                Platform.runLater(onLoaded);
            }
        }, "schema-cache");
        loadThread.setDaemon(true);
        loadThread.start();
    }

    private void markEmpty(String loadKey) {
        if (loadKey.equals("databases")) databaseNames = List.of();
        else if (loadKey.startsWith("tables:")) tableNamesByDatabase.put(loadKey.substring("tables:".length()), List.of());
        else if (loadKey.startsWith("columns:")) columnNamesByTable.put(loadKey.substring("columns:".length()), List.of());
    }
}
