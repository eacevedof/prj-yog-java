package modules.explorer.domain;

import java.util.List;

import org.junit.jupiter.api.Test;

import modules.shared.domain.services.SqlDialect;

import testcase.AbstractTestCase;

import modules.explorer.domain.services.DatabaseDropSqlBuilder;

/**
 * "Eliminar BD": el DROP de cada motor; SQL Server echa antes las otras sesiones y sale de la BD; SQLite no borra BDs.
 */
final class DatabaseDropSqlBuilderTest extends AbstractTestCase {

    @Test
    void testDropInEachEngine() {
        assertEquals(List.of("DROP DATABASE `db_shop`"), DatabaseDropSqlBuilder.getInstance(SqlDialect.MYSQL).getDropStatements("db_shop"));
        assertEquals(List.of("DROP SCHEMA \"sales\" CASCADE"), DatabaseDropSqlBuilder.getInstance(SqlDialect.PGSQL).getDropStatements("sales"));
        assertEquals(List.of(
            "USE [master]",
            "ALTER DATABASE [db_shop] SET SINGLE_USER WITH ROLLBACK IMMEDIATE",
            "DROP DATABASE [db_shop]"
        ), DatabaseDropSqlBuilder.getInstance(SqlDialect.MSSQL).getDropStatements("db_shop"));
    }

    @Test
    void testSqliteDoesNotDropDatabases() {
        DatabaseDropSqlBuilder databaseDropSqlBuilder = DatabaseDropSqlBuilder.getInstance(SqlDialect.SQLITE);
        assertFalse(databaseDropSqlBuilder.isSupported());
        assertEquals(List.of(), databaseDropSqlBuilder.getDropStatements("main"));
    }
}
