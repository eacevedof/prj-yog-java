package modules.explorer.infrastructure.repositories;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import modules.shared.infrastructure.repositories.AbstractJdbcRepository;
import static modules.shared.application.Primitives.getTrimmedString;

import modules.explorer.domain.entities.DatabaseOptionsEntity;

/**
 * Lo que lee el "Alter Database" (MySQL/MariaDB) de information_schema: las opciones de la BD (las columnas que no
 * tiene el servidor salen como no soportadas) y los juegos de caracteres con sus cotejamientos.
 */
public final class DatabaseOptionsReaderMysqlRepository extends AbstractJdbcRepository {

    // "Unknown table 'SCHEMATA_EXTENSIONS' in information_schema" (MariaDB, MySQL < 8.0.22); 1146 por si el servidor
    // la da como tabla que no existe
    private static final int ER_UNKNOWN_TABLE = 1109;
    private static final int ER_NO_SUCH_TABLE = 1146;

    private DatabaseOptionsReaderMysqlRepository(Connection pdo) {
        super(pdo);
    }

    public static DatabaseOptionsReaderMysqlRepository getInstance(Connection pdo) {
        return new DatabaseOptionsReaderMysqlRepository(pdo);
    }

    /**
     * null si la BD no existe.
     */
    public DatabaseOptionsEntity getDatabaseOptions(String databaseName) throws SQLException {
        List<Map<String, Object>> rows = query("""
            -- getDatabaseOptions
            SELECT *
            FROM information_schema.SCHEMATA
            WHERE 1
            AND SCHEMA_NAME = ?
            """, databaseName);
        if (rows.isEmpty()) return null;
        Map<String, Object> row = getUpperCaseKeys(rows.getFirst());
        boolean isEncryptionSupported = row.containsKey("DEFAULT_ENCRYPTION");
        boolean isCommentSupported = row.containsKey("SCHEMA_COMMENT");
        String readOnlyOptions = getReadOnlyOptions(databaseName);
        return new DatabaseOptionsEntity(
            databaseName,
            getText(row.get("DEFAULT_CHARACTER_SET_NAME")),
            getText(row.get("DEFAULT_COLLATION_NAME")),
            isEncryptionSupported,
            isEncryptionSupported && "YES".equalsIgnoreCase(getText(row.get("DEFAULT_ENCRYPTION"))),
            readOnlyOptions != null,
            readOnlyOptions != null && readOnlyOptions.toUpperCase(Locale.ROOT).replace(" ", "").contains("READONLY=1"),
            isCommentSupported,
            isCommentSupported ? getText(row.get("SCHEMA_COMMENT")) : ""
        );
    }

    /**
     * [juego de caracteres => [cotejamientos...]], el de por defecto de cada juego el primero.
     */
    public Map<String, List<String>> getCollationsByCharacterSet() throws SQLException {
        Map<String, List<String>> collationsByCharacterSet = new LinkedHashMap<>();
        for (Map<String, Object> row : query("""
            -- getCollationsByCharacterSet
            SELECT CHARACTER_SET_NAME AS character_set, COLLATION_NAME AS collation_name, IS_DEFAULT AS is_default
            FROM information_schema.COLLATIONS
            WHERE 1
            AND CHARACTER_SET_NAME IS NOT NULL
            ORDER BY CHARACTER_SET_NAME, IS_DEFAULT DESC, COLLATION_NAME
            """)) {
            collationsByCharacterSet.computeIfAbsent(getText(row.get("character_set")), characterSet -> new ArrayList<>())
                .add(getText(row.get("collation_name")));
        }
        return collationsByCharacterSet;
    }

    /**
     * [character_set, collation] por defecto del servidor (lo que toma una BD nueva sin decir nada).
     */
    public Map<String, Object> getServerDefaults() throws SQLException {
        Map<String, Object> row = query("""
            -- getServerDefaults
            SELECT @@character_set_server AS character_set, @@collation_server AS collation_name
            """).getFirst();
        return Map.of("characterSet", getText(row.get("character_set")), "collation", getText(row.get("collation_name")));
    }

    /**
     * Las columnas de information_schema.SCHEMATA en mayusculas: DEFAULT_ENCRYPTION (MySQL 8) y SCHEMA_COMMENT
     * (MariaDB 10.5+) dicen si el servidor acepta ENCRYPTION y COMMENT en el CREATE DATABASE.
     */
    public List<String> getSchemataColumnNames() throws SQLException {
        List<String> columnNames = new ArrayList<>();
        for (Map<String, Object> row : query("""
            -- getSchemataColumnNames
            SELECT COLUMN_NAME AS column_name
            FROM information_schema.COLUMNS
            WHERE 1
            AND TABLE_SCHEMA = 'information_schema'
            AND TABLE_NAME = 'SCHEMATA'
            """)) {
            columnNames.add(getText(row.get("column_name")).toUpperCase(Locale.ROOT));
        }
        return columnNames;
    }

    /**
     * "READ ONLY=1" o vacio en MySQL 8.0.22+; null si el servidor no tiene SCHEMATA_EXTENSIONS (MariaDB, MySQL viejo).
     * Cualquier otro fallo (permisos, tunel caido) sube: no es que la opcion no exista.
     */
    private String getReadOnlyOptions(String databaseName) throws SQLException {
        try {
            List<Map<String, Object>> rows = query("""
                -- getReadOnlyOptions
                SELECT OPTIONS AS schema_options
                FROM information_schema.SCHEMATA_EXTENSIONS
                WHERE 1
                AND SCHEMA_NAME = ?
                """, databaseName);
            return rows.isEmpty() ? "" : getText(rows.getFirst().get("schema_options"));
        }
        catch (SQLException e) {
            if (e.getErrorCode() == ER_UNKNOWN_TABLE || e.getErrorCode() == ER_NO_SUCH_TABLE) return null;
            throw e;
        }
    }

    private static Map<String, Object> getUpperCaseKeys(Map<String, Object> row) {
        Map<String, Object> upperCaseRow = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : row.entrySet()) upperCaseRow.put(entry.getKey().toUpperCase(Locale.ROOT), entry.getValue());
        return upperCaseRow;
    }

    /**
     * information_schema por el tunel puede llegar en bytes.
     */
    private static String getText(Object value) {
        if (value instanceof byte[] bytes) return new String(bytes, StandardCharsets.UTF_8);
        return getTrimmedString(value);
    }
}
