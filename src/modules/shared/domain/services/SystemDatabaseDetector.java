package modules.shared.domain.services;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Las BD del propio servidor (catalogo, usuarios, rendimiento): se ven apagadas en el arbol y avisan en naranja al
 * elegirlas como BD activa, para no escribir en ellas por error. En PostgreSQL son esquemas (pg_catalog, pg_toast...).
 */
public final class SystemDatabaseDetector {

    private static final String PGSQL_SYSTEM_PREFIX = "pg_";

    private static final Map<String, List<String>> SYSTEM_DATABASES = Map.of(
        SqlDialect.MYSQL, List.of("information_schema", "mysql", "performance_schema", "sys"),
        SqlDialect.PGSQL, List.of("information_schema"),
        SqlDialect.MSSQL, List.of("master", "model", "msdb", "tempdb")
    );

    private final String dbEngine;

    private SystemDatabaseDetector(String dbEngine) {
        this.dbEngine = dbEngine == null ? "" : dbEngine;
    }

    public static SystemDatabaseDetector getInstance(String dbEngine) {
        return new SystemDatabaseDetector(dbEngine);
    }

    public boolean isSystemDatabase(String databaseName) {
        if (databaseName == null || databaseName.isBlank()) return false;
        String lowerName = databaseName.trim().toLowerCase(Locale.ROOT);
        if (SqlDialect.PGSQL.equals(dbEngine) && lowerName.startsWith(PGSQL_SYSTEM_PREFIX)) return true;
        return SYSTEM_DATABASES.getOrDefault(dbEngine, List.of()).contains(lowerName);
    }
}
