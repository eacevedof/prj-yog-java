package modules.dbcopy.infrastructure.repositories;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import modules.shared.application.Primitives;
import modules.shared.infrastructure.components.sqltrace.SqlTraceComponent;
import modules.shared.infrastructure.repositories.AbstractJdbcRepository;
import static modules.shared.application.Primitives.getTrimmedString;

import modules.connections.infrastructure.components.httptunnel.HttpTunnelResult;
import modules.connections.infrastructure.components.httptunnel.HttpTunnelSession;

import modules.explorer.domain.enums.ExplorerKeyEnum;

import modules.dbcopy.domain.enums.DbCopyCellKindEnum;
import modules.dbcopy.domain.enums.DbCopyConfigEnum;
import modules.dbcopy.domain.enums.DbCopyKeyEnum;
import modules.dbcopy.domain.enums.DbCopyObjectTypeEnum;
import modules.dbcopy.domain.services.DbCopySqlBuilder;

/**
 * Las lecturas de la copia (feature 29), sobre una sesion propia: del origen, que objetos hay, su CREATE y las filas
 * por paginas; del destino, si la BD existe y cuanto cabe en una sentencia. Vale para JDBC (directo o por SSH) y para
 * el tunel HTTP; por el tunel cada peticion es una sesion nueva, asi que la zona horaria viaja en el mismo lote que el
 * SELECT.
 */
public final class DbCopyReaderMysqlRepository extends AbstractJdbcRepository {

    private final DbCopySqlBuilder dbCopySqlBuilder = DbCopySqlBuilder.getInstance();
    private final HttpTunnelSession httpTunnelSession;

    private DbCopyReaderMysqlRepository(Connection pdo) throws SQLException {
        super(pdo);
        this.httpTunnelSession = pdo.isWrapperFor(HttpTunnelSession.class) ? pdo.unwrap(HttpTunnelSession.class) : null;
    }

    public static DbCopyReaderMysqlRepository getInstance(Connection pdo) throws SQLException {
        return new DbCopyReaderMysqlRepository(pdo);
    }

    /**
     * La BD origen como activa: asi SHOW CREATE VIEW no cualifica las tablas con su BD (la vista copiada apunta a las
     * tablas de la BD destino, no a las del origen).
     */
    public void prepareSession(String databaseName) throws SQLException {
        pdo.setCatalog(databaseName);
        if (httpTunnelSession != null) return;
        SqlTraceComponent.getInstance().addSql(DbCopyConfigEnum.SOURCE_SESSION_SQL);
        try (Statement statement = pdo.createStatement()) {
            statement.execute(DbCopyConfigEnum.SOURCE_SESSION_SQL);
        }
    }

    /**
     * Como mysqldump --single-transaction: todo lo que se lea despues sale de la misma foto de la BD (InnoDB) sin
     * bloquear a nadie. Por el tunel no hay sesion entre peticiones: no se puede.
     */
    public void startConsistentSnapshot() throws SQLException {
        if (httpTunnelSession != null) return;
        SqlTraceComponent.getInstance().addSql("SET SESSION TRANSACTION ISOLATION LEVEL REPEATABLE READ");
        SqlTraceComponent.getInstance().addSql("START TRANSACTION WITH CONSISTENT SNAPSHOT");
        try (Statement statement = pdo.createStatement()) {
            statement.execute("SET SESSION TRANSACTION ISOLATION LEVEL REPEATABLE READ");
            statement.execute("START TRANSACTION WITH CONSISTENT SNAPSHOT");
        }
    }

    /**
     * El servidor y su version para la cabecera del volcado: "MySQL 8.4.3".
     */
    public String getServerVersion() throws SQLException {
        return getText(query("SELECT VERSION() AS server_version").getFirst().get("server_version"));
    }

    /**
     * Quien es el servidor de verdad, llegue como llegue (directo, SSH o tunel): host, puerto y server_id, y el
     * server_uuid si lo tiene (MySQL 5.6+; en MariaDB SHOW VARIABLES no da fila). Dos conexiones guardadas distintas
     * pueden ir al mismo servidor: asi se evita copiar una BD encima de si misma (el DROP borraria el origen).
     */
    public String getServerIdentity() throws SQLException {
        Map<String, Object> row = query("SELECT @@hostname AS host_name, @@port AS port, @@server_id AS server_id").getFirst();
        String identity = getText(row.get("host_name")) + ":" + getText(row.get("port")) + ":" + getText(row.get("server_id"));
        List<Map<String, Object>> uuidRows = query("SHOW VARIABLES LIKE 'server_uuid'");
        if (uuidRows.isEmpty()) return identity;
        // SHOW VARIABLES: Variable_name, Value
        return identity + ":" + getText(uuidRows.getFirst().values().stream().skip(1).findFirst().orElse(""));
    }

    public boolean isDatabaseExisting(String databaseName) throws SQLException {
        return !query("""
            -- isDatabaseExisting
            SELECT SCHEMA_NAME AS database_name
            FROM information_schema.SCHEMATA
            WHERE 1
            AND SCHEMA_NAME = ?
            """, databaseName).isEmpty();
    }

    /**
     * Cuanto cabe en una sentencia INSERT de esta sesion: por el tunel, una peticion HTTP razonable; por JDBC, lo que
     * deje el max_allowed_packet del servidor con 4 bytes por caracter, sin pasar del tope.
     */
    public int getMaxStatementChars() throws SQLException {
        if (httpTunnelSession != null) return DbCopyConfigEnum.TUNNEL_MAX_STATEMENT_CHARS;
        long maxAllowedPacket = getLong(query("SELECT @@max_allowed_packet AS max_allowed_packet").getFirst().get("max_allowed_packet"));
        if (maxAllowedPacket <= 0) return DbCopyConfigEnum.JDBC_MAX_STATEMENT_CHARS;
        long packetChars = maxAllowedPacket / DbCopyConfigEnum.MAX_BYTES_PER_CHAR - DbCopyConfigEnum.PACKET_MARGIN_CHARS;
        return (int) Math.max(DbCopyConfigEnum.MIN_STATEMENT_CHARS, Math.min(DbCopyConfigEnum.JDBC_MAX_STATEMENT_CHARS, packetChars));
    }

    /**
     * [tables, views, procedures, functions, triggers, events => [nombres...], trigger_tables => [trigger => tabla]].
     */
    public Map<String, Object> getObjects(String databaseName) throws SQLException {
        List<String> tables = new ArrayList<>();
        List<String> views = new ArrayList<>();
        for (Map<String, Object> row : query("""
            -- getTablesAndViews
            SELECT TABLE_NAME AS table_name, TABLE_TYPE AS table_type
            FROM information_schema.TABLES
            WHERE 1
            AND TABLE_SCHEMA = ?
            ORDER BY TABLE_NAME
            """, databaseName)) {
            String tableType = getText(row.get(ExplorerKeyEnum.TABLE_TYPE));
            if (ExplorerKeyEnum.TABLE_TYPE_VIEW.equals(tableType)) views.add(getText(row.get("table_name")));
            else if (ExplorerKeyEnum.TABLE_TYPE_BASE_TABLE.equals(tableType)) tables.add(getText(row.get("table_name")));
        }
        List<String> procedures = new ArrayList<>();
        List<String> functions = new ArrayList<>();
        for (Map<String, Object> row : query("""
            -- getRoutines
            SELECT ROUTINE_NAME AS routine_name, ROUTINE_TYPE AS routine_type
            FROM information_schema.ROUTINES
            WHERE 1
            AND ROUTINE_SCHEMA = ?
            ORDER BY ROUTINE_NAME
            """, databaseName)) {
            if ("FUNCTION".equals(getText(row.get("routine_type")))) functions.add(getText(row.get("routine_name")));
            else procedures.add(getText(row.get("routine_name")));
        }
        List<String> triggers = new ArrayList<>();
        Map<String, Object> triggerTables = new LinkedHashMap<>();
        for (Map<String, Object> row : query("""
            -- getTriggers
            SELECT TRIGGER_NAME AS trigger_name, EVENT_OBJECT_TABLE AS table_name
            FROM information_schema.TRIGGERS
            WHERE 1
            AND TRIGGER_SCHEMA = ?
            ORDER BY TRIGGER_NAME
            """, databaseName)) {
            String triggerName = getText(row.get("trigger_name"));
            triggers.add(triggerName);
            triggerTables.put(triggerName, getText(row.get("table_name")));
        }
        List<String> events = getNames("""
            -- getEvents
            SELECT EVENT_NAME AS object_name
            FROM information_schema.EVENTS
            WHERE 1
            AND EVENT_SCHEMA = ?
            ORDER BY EVENT_NAME
            """, databaseName);
        return Primitives.of(
            DbCopyKeyEnum.TABLES, tables,
            DbCopyKeyEnum.VIEWS, views,
            DbCopyKeyEnum.PROCEDURES, procedures,
            DbCopyKeyEnum.FUNCTIONS, functions,
            DbCopyKeyEnum.TRIGGERS, triggers,
            DbCopyKeyEnum.EVENTS, events,
            DbCopyKeyEnum.TRIGGER_TABLES, triggerTables
        );
    }

    /**
     * [character_set, collation] de la BD; vacio si no existe.
     */
    public Map<String, Object> getDatabaseCharset(String databaseName) throws SQLException {
        List<Map<String, Object>> rows = query("""
            -- getDatabaseCharset
            SELECT DEFAULT_CHARACTER_SET_NAME AS character_set, DEFAULT_COLLATION_NAME AS collation_name
            FROM information_schema.SCHEMATA
            WHERE 1
            AND SCHEMA_NAME = ?
            """, databaseName);
        if (rows.isEmpty()) return Primitives.of();
        return Primitives.of(
            DbCopyKeyEnum.CHARACTER_SET, getText(rows.getFirst().get("character_set")),
            DbCopyKeyEnum.COLLATION, getText(rows.getFirst().get("collation_name"))
        );
    }

    /**
     * El CREATE de SHOW CREATE; vacio si el servidor no lo da (una rutina de otro usuario sin permisos sale NULL).
     */
    public String getCreateSql(String objectType, String databaseName, String objectName) throws SQLException {
        List<Map<String, Object>> rows = query(dbCopySqlBuilder.getShowCreateSql(objectType, databaseName, objectName));
        if (rows.isEmpty()) return "";
        String columnLabel = switch (objectType) {
            case DbCopyObjectTypeEnum.VIEW -> "Create View";
            case DbCopyObjectTypeEnum.PROCEDURE -> "Create Procedure";
            case DbCopyObjectTypeEnum.FUNCTION -> "Create Function";
            case DbCopyObjectTypeEnum.TRIGGER -> "SQL Original Statement";
            case DbCopyObjectTypeEnum.EVENT -> "Create Event";
            default -> "Create Table";
        };
        for (Map.Entry<String, Object> entry : rows.getFirst().entrySet()) {
            if (entry.getKey().equalsIgnoreCase(columnLabel)) return getText(entry.getValue());
        }
        return "";
    }

    /**
     * Las columnas que se copian, en su orden: [column_name, data_type]. Las generadas (VIRTUAL/STORED) no: el
     * servidor destino las calcula y no admite valores.
     */
    public List<Map<String, Object>> getCopyableColumns(String databaseName, String tableName) throws SQLException {
        List<Map<String, Object>> columns = new ArrayList<>();
        for (Map<String, Object> row : query("""
            -- getCopyableColumns
            SELECT COLUMN_NAME AS column_name, DATA_TYPE AS data_type, EXTRA AS extra
            FROM information_schema.COLUMNS
            WHERE 1
            AND TABLE_SCHEMA = ?
            AND TABLE_NAME = ?
            ORDER BY ORDINAL_POSITION
            """, databaseName, tableName)) {
            if (getText(row.get("extra")).toUpperCase().contains("GENERATED")) continue;
            columns.add(Primitives.of(
                DbCopyKeyEnum.COLUMN_NAME, getText(row.get("column_name")),
                DbCopyKeyEnum.DATA_TYPE, getText(row.get("data_type"))
            ));
        }
        return columns;
    }

    public List<String> getPrimaryKeyColumns(String databaseName, String tableName) throws SQLException {
        return getNames("""
            -- getPrimaryKeyColumns
            SELECT COLUMN_NAME AS object_name
            FROM information_schema.KEY_COLUMN_USAGE
            WHERE 1
            AND TABLE_SCHEMA = ?
            AND TABLE_NAME = ?
            AND CONSTRAINT_NAME = 'PRIMARY'
            ORDER BY ORDINAL_POSITION
            """, databaseName, tableName);
    }

    /**
     * Las filas que estima el servidor (InnoDB no las cuenta exactas): solo para la barra de progreso; 0 si no la da.
     */
    public long getEstimatedRows(String databaseName, String tableName) throws SQLException {
        List<Map<String, Object>> rows = query("""
            -- getEstimatedRows
            SELECT TABLE_ROWS AS table_rows
            FROM information_schema.TABLES
            WHERE 1
            AND TABLE_SCHEMA = ?
            AND TABLE_NAME = ?
            """, databaseName, tableName);
        return rows.isEmpty() ? 0 : getLong(rows.getFirst().get("table_rows"));
    }

    /**
     * Renombrar BD: las filas de verdad (COUNT), para comprobar que la copia tiene las mismas que la original.
     */
    public long getExactRowCount(String databaseName, String tableName) throws SQLException {
        return getLong(query(dbCopySqlBuilder.getCountSql(databaseName, tableName)).getFirst().get("row_count"));
    }

    /**
     * Una pagina de DbCopyConfigEnum.PAGE_ROWS filas: con clave primaria, las que siguen a lastKeyValues (vacio = la
     * primera); sin ella, desde offset. Cada celda segun su tipo (cellKinds, DbCopyCellKindEnum, uno por columna):
     * texto tal cual lo da el servidor, bytes, o un BIT como numero; null es NULL.
     */
    public List<List<Object>> getRowsPage(
        String databaseName, String tableName, List<String> columnNames, List<String> cellKinds,
        List<String> primaryKeyColumns, List<Object> lastKeyValues, long offset
    ) throws SQLException {
        Set<String> bitColumns = new HashSet<>();
        for (int i = 0; i < columnNames.size(); i++) {
            if (DbCopyCellKindEnum.BIT.equals(cellKinds.get(i))) bitColumns.add(columnNames.get(i));
        }
        String sql = dbCopySqlBuilder.getSelectPageSql(
            databaseName, tableName, columnNames, bitColumns, primaryKeyColumns, lastKeyValues, DbCopyConfigEnum.PAGE_ROWS, offset
        );
        SqlTraceComponent sqlTraceComponent = SqlTraceComponent.getInstance();
        sqlTraceComponent.addSql(sql);
        List<List<Object>> rows = new ArrayList<>();
        if (httpTunnelSession != null) {
            HttpTunnelResult httpTunnelResult = httpTunnelSession.executeBatch(List.of(DbCopyConfigEnum.SOURCE_SESSION_SQL, sql));
            for (List<byte[]> tunnelRow : httpTunnelResult.getRows()) {
                List<Object> row = new ArrayList<>(cellKinds.size());
                for (int i = 0; i < cellKinds.size(); i++) {
                    byte[] cell = tunnelRow.get(i);
                    row.add(cell == null ? null : getCell(cellKinds.get(i), cell, new String(cell, StandardCharsets.UTF_8)));
                }
                rows.add(row);
            }
            sqlTraceComponent.addRowsCount(rows.size());
            return rows;
        }
        try (Statement statement = pdo.createStatement(); ResultSet resultSet = statement.executeQuery(sql)) {
            while (resultSet.next()) {
                List<Object> row = new ArrayList<>(cellKinds.size());
                for (int i = 0; i < cellKinds.size(); i++) {
                    if (DbCopyCellKindEnum.BINARY.equals(cellKinds.get(i))) {
                        row.add(resultSet.getBytes(i + 1));
                        continue;
                    }
                    String text = resultSet.getString(i + 1);
                    row.add(text == null ? null : getCell(cellKinds.get(i), null, text));
                }
                rows.add(row);
            }
        }
        sqlTraceComponent.addRowsCount(rows.size());
        return rows;
    }

    private Object getCell(String cellKind, byte[] bytes, String text) {
        if (DbCopyCellKindEnum.BINARY.equals(cellKind)) return bytes;
        if (DbCopyCellKindEnum.BIT.equals(cellKind)) return new BigDecimal(text.trim());
        return text;
    }

    private List<String> getNames(String sql, Object... params) throws SQLException {
        List<String> names = new ArrayList<>();
        for (Map<String, Object> row : query(sql, params)) {
            names.add(getText(row.get("object_name")));
        }
        return names;
    }

    /**
     * Un numero del servidor (TABLE_ROWS, @@max_allowed_packet); 0 si viene NULL o no es un entero.
     */
    private long getLong(Object value) {
        String text = getText(value);
        return text.matches("\\d{1,18}") ? Long.parseLong(text) : 0;
    }

    /**
     * information_schema por el tunel puede llegar en bytes (columnas binarias de algunas versiones).
     */
    private String getText(Object value) {
        if (value instanceof byte[] bytes) return new String(bytes, StandardCharsets.UTF_8);
        return getTrimmedString(value);
    }
}
