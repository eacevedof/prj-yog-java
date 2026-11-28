package modules.connections.domain.enums;

import java.util.List;
import java.util.Map;

import modules.shared.domain.services.SqlDialect;

/**
 * Motores de BD destino: los SQL (MySQL/MariaDB, PostgreSQL, SQL Server, SQLite) comparten arbol, editor y pestaña Datos; Redis y
 * MongoDB llevan su propia pantalla.
 */
public final class DbEngineEnum {

    public static final String MYSQL = SqlDialect.MYSQL;
    public static final String PGSQL = SqlDialect.PGSQL;
    public static final String SQLITE = SqlDialect.SQLITE;
    public static final String MSSQL = SqlDialect.MSSQL;
    public static final String REDIS = "redis";
    public static final String MONGODB = "mongodb";
    public static final String FILES = SqlDialect.FILES;
    public static final String DEFAULT = MYSQL;
    public static final int MYSQL_DEFAULT_PORT = 3306;
    public static final int PGSQL_DEFAULT_PORT = 5432;
    public static final int MSSQL_DEFAULT_PORT = 1433;
    public static final int REDIS_DEFAULT_PORT = 6379;
    public static final int MONGODB_DEFAULT_PORT = 27017;
    public static final String PGSQL_DEFAULT_DATABASE = "postgres";
    public static final int REDIS_MAX_DATABASE_INDEX = 999;

    public static final List<String> VALUES = List.of(MYSQL, PGSQL, MSSQL, SQLITE, FILES, REDIS, MONGODB);

    /**
     * Lo que carga el motor "Ficheros" de su carpeta (sin entrar en subcarpetas).
     */
    public static final List<String> FILES_EXTENSIONS = List.of("csv", "tsv", "json", "jsonl", "ndjson");

    /**
     * Lo que se ve en el desplegable del dialogo de conexion.
     */
    public static final Map<String, String> LABELS = Map.of(
        MYSQL, "MySQL / MariaDB",
        PGSQL, "PostgreSQL",
        MSSQL, "SQL Server",
        SQLITE, "SQLite",
        REDIS, "Redis",
        MONGODB, "MongoDB",
        FILES, "Ficheros (CSV, JSON)"
    );

    private static final Map<String, Integer> DEFAULT_PORTS = Map.of(
        MYSQL, MYSQL_DEFAULT_PORT,
        PGSQL, PGSQL_DEFAULT_PORT,
        MSSQL, MSSQL_DEFAULT_PORT,
        REDIS, REDIS_DEFAULT_PORT,
        MONGODB, MONGODB_DEFAULT_PORT
    );

    private DbEngineEnum() {
    }

    /**
     * 0 si el motor no usa red (SQLite).
     */
    public static int getDefaultPort(String dbEngine) {
        return DEFAULT_PORTS.getOrDefault(dbEngine, 0);
    }

    public static boolean isSql(String dbEngine) {
        return SqlDialect.SQL_ENGINES.contains(dbEngine);
    }

    /**
     * Motores donde el usuario es opcional (Redis sin ACL, MongoDB sin autenticacion).
     */
    public static boolean isUserOptional(String dbEngine) {
        return REDIS.equals(dbEngine) || MONGODB.equals(dbEngine);
    }

    /**
     * El tunel PHP: MySQL, PostgreSQL, SQLite (su lista de BDs), SQL Server, Redis y MongoDB (contrato en el vault:
     * sqlyog-tunnel-multidbs). Ficheros no: la carpeta es local.
     */
    public static boolean isHttpTunnelSupported(String dbEngine) {
        return isSql(dbEngine) || REDIS.equals(dbEngine) || MONGODB.equals(dbEngine);
    }
}
