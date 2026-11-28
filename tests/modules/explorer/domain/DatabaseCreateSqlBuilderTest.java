package modules.explorer.domain;

import java.util.List;

import org.junit.jupiter.api.Test;

import modules.shared.domain.services.SqlDialect;
import modules.shared.application.Primitives;

import testcase.AbstractTestCase;

import modules.explorer.domain.entities.DatabaseCreationEntity;
import modules.explorer.domain.services.DatabaseCreateSqlBuilder;

/**
 * "Crear BD": el CREATE de cada motor con sus opciones; lo vacio no se escribe y SQLite no crea BDs.
 */
final class DatabaseCreateSqlBuilderTest extends AbstractTestCase {

    @Test
    void testMysqlWithAllOptions() {
        DatabaseCreationEntity databaseCreationEntity = DatabaseCreationEntity.fromPrimitives(Primitives.of(
            "databaseName", " db_shop ", "characterSet", "utf8mb4", "collation", "utf8mb4_unicode_ci",
            "encryption", "Y", "comment", "it's new", "isIfNotExists", true
        ));
        assertEquals("CREATE DATABASE IF NOT EXISTS `db_shop` CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ENCRYPTION = 'Y' COMMENT = 'it\\'s new'",
            DatabaseCreateSqlBuilder.getInstance(SqlDialect.MYSQL).getCreateSql(databaseCreationEntity));
    }

    @Test
    void testMysqlWithoutOptions() {
        assertEquals("CREATE DATABASE `db_shop`", DatabaseCreateSqlBuilder.getInstance(SqlDialect.MYSQL)
            .getCreateSql(DatabaseCreationEntity.fromPrimitives(Primitives.of("databaseName", "db_shop"))));
    }

    @Test
    void testPgsqlSchemaWithOwner() {
        DatabaseCreationEntity databaseCreationEntity = DatabaseCreationEntity.fromPrimitives(Primitives.of(
            "databaseName", "sales", "owner", "app_user", "isIfNotExists", true
        ));
        assertEquals("CREATE SCHEMA IF NOT EXISTS \"sales\" AUTHORIZATION \"app_user\"",
            DatabaseCreateSqlBuilder.getInstance(SqlDialect.PGSQL).getCreateSql(databaseCreationEntity));
    }

    @Test
    void testMssqlWithCollationAndIfNotExists() {
        DatabaseCreationEntity databaseCreationEntity = DatabaseCreationEntity.fromPrimitives(Primitives.of(
            "databaseName", "db_shop", "collation", "Latin1_General_CI_AS", "isIfNotExists", true
        ));
        assertEquals("IF DB_ID(N'db_shop') IS NULL CREATE DATABASE [db_shop] COLLATE Latin1_General_CI_AS",
            DatabaseCreateSqlBuilder.getInstance(SqlDialect.MSSQL).getCreateSql(databaseCreationEntity));
    }

    @Test
    void testSqliteDoesNotCreateDatabases() {
        DatabaseCreateSqlBuilder databaseCreateSqlBuilder = DatabaseCreateSqlBuilder.getInstance(SqlDialect.SQLITE);
        assertFalse(databaseCreateSqlBuilder.isSupported());
        assertEquals("", databaseCreateSqlBuilder.getCreateSql(DatabaseCreationEntity.fromPrimitives(Primitives.of("databaseName", "x"))));
    }

    @Test
    void testErrorsForWrongOptions() {
        DatabaseCreationEntity databaseCreationEntity = DatabaseCreationEntity.fromPrimitives(Primitives.of(
            "databaseName", "", "characterSet", "utf8; DROP", "encryption", "maybe"
        ));
        assertEquals(List.of("databaseName", "utf8; DROP", "maybe"),
            DatabaseCreateSqlBuilder.getInstance(SqlDialect.MYSQL).getErrors(databaseCreationEntity));
    }
}
