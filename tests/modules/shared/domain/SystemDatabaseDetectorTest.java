package modules.shared.domain;

import org.junit.jupiter.api.Test;

import testcase.AbstractTestCase;

import modules.shared.domain.services.SqlDialect;
import modules.shared.domain.services.SystemDatabaseDetector;

/**
 * Las BD del propio servidor de cada motor, que se ven apagadas en el arbol y avisan como BD activa.
 */
final class SystemDatabaseDetectorTest extends AbstractTestCase {

    @Test
    void testMysqlSystemDatabases() {
        SystemDatabaseDetector systemDatabaseDetector = SystemDatabaseDetector.getInstance(SqlDialect.MYSQL);
        assertTrue(systemDatabaseDetector.isSystemDatabase("information_schema"));
        assertTrue(systemDatabaseDetector.isSystemDatabase("mysql"));
        assertTrue(systemDatabaseDetector.isSystemDatabase("Performance_Schema"));
        assertTrue(systemDatabaseDetector.isSystemDatabase("sys"));
        assertFalse(systemDatabaseDetector.isSystemDatabase("db_mysql_shop"));
        assertFalse(systemDatabaseDetector.isSystemDatabase(""));
    }

    @Test
    void testPgsqlSystemSchemas() {
        SystemDatabaseDetector systemDatabaseDetector = SystemDatabaseDetector.getInstance(SqlDialect.PGSQL);
        assertTrue(systemDatabaseDetector.isSystemDatabase("pg_catalog"));
        assertTrue(systemDatabaseDetector.isSystemDatabase("pg_toast"));
        assertTrue(systemDatabaseDetector.isSystemDatabase("information_schema"));
        assertFalse(systemDatabaseDetector.isSystemDatabase("public"));
        assertFalse(systemDatabaseDetector.isSystemDatabase("mysql"));
    }

    @Test
    void testMssqlAndSqlite() {
        assertTrue(SystemDatabaseDetector.getInstance(SqlDialect.MSSQL).isSystemDatabase("master"));
        assertTrue(SystemDatabaseDetector.getInstance(SqlDialect.MSSQL).isSystemDatabase("tempdb"));
        assertFalse(SystemDatabaseDetector.getInstance(SqlDialect.MSSQL).isSystemDatabase("db_shop"));
        assertFalse(SystemDatabaseDetector.getInstance(SqlDialect.SQLITE).isSystemDatabase("main"));
    }
}
