package modules.integration;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import modules.shared.application.Primitives;

import testcase.AbstractTestCase;

import modules.connections.domain.enums.ConnectionsKeyEnum;
import modules.connections.application.createconnection.CreateConnectionDto;
import modules.connections.application.createconnection.CreateConnectionService;
import modules.connections.infrastructure.factories.TargetSessionFactory;

import modules.explorer.application.createdatabase.CreateDatabaseDto;
import modules.explorer.application.dropdatabase.DropDatabaseDto;
import modules.explorer.application.getcreatedatabaseoptions.GetCreateDatabaseOptionsDto;
import modules.explorer.application.getcreatedatabaseoptions.GetCreateDatabaseOptionsResultDto;
import modules.explorer.application.listdatabases.ListDatabasesDto;
import modules.explorer.application.createdatabase.CreateDatabaseService;
import modules.explorer.application.dropdatabase.DropDatabaseService;
import modules.explorer.application.getcreatedatabaseoptions.GetCreateDatabaseOptionsService;
import modules.explorer.application.listdatabases.ListDatabasesService;
import modules.explorer.domain.exceptions.ExplorerException;

import modules.query.domain.enums.QueryKeyEnum;
import modules.query.application.executequery.ExecuteQueryDto;
import modules.query.application.executequery.ExecuteQueryResultDto;
import modules.query.application.executequery.ExecuteQueryService;

/**
 * "Crear BD" (make test-mysql): lee las opciones del servidor y crea la BD en MySQL (juego de caracteres y
 * cotejamiento), PostgreSQL (esquema con propietario) y SQL Server (cotejamiento); despues sale en la lista del arbol. Luego
 * "Eliminar BD" la borra (en PostgreSQL con sus tablas; en SQL Server aunque otra sesion este dentro) y una BD de
 * sistema no se deja borrar.
 */
final class CreateDatabaseIntegrationTest extends AbstractTestCase {

    private static final String DATABASE = "jy_create_db_it";
    private static final String SESSION = "ctab-create-db-it:explorer";

    private static String mysqlUuid;
    private static String pgsqlUuid;
    private static String mssqlUuid;

    @BeforeAll
    static void createConnections() {
        String testMysql = System.getenv("JAVAYOG_TEST_MYSQL");
        Assumptions.assumeTrue(testMysql != null && !testMysql.isBlank(), "JAVAYOG_TEST_MYSQL not set: create database skipped");
        bootFreshDatabase("CreateDatabaseIntegrationTest");
        String[] mysql = testMysql.split(":", 4);
        mysqlUuid = createConnection(Primitives.of(
            "connectionName", "create-my", "host", mysql[0], "port", mysql[1], "userName", mysql[2], "userPassword", mysql[3]
        ));
        execute(mysqlUuid, "DROP DATABASE IF EXISTS " + DATABASE);
        String testPgsql = System.getenv("JAVAYOG_TEST_PGSQL");
        if (testPgsql != null && !testPgsql.isBlank()) {
            String[] pgsql = testPgsql.split(":", 4);
            pgsqlUuid = createConnection(Primitives.of(
                "connectionName", "create-pg", "dbEngine", "pgsql", "host", pgsql[0], "port", pgsql[1], "userName", pgsql[2], "userPassword", pgsql[3]
            ));
            execute(pgsqlUuid, "DROP SCHEMA IF EXISTS " + DATABASE + " CASCADE");
        }
        String testMssql = System.getenv("JAVAYOG_TEST_MSSQL");
        if (testMssql != null && !testMssql.isBlank()) {
            String[] mssql = testMssql.split(":", 4);
            mssqlUuid = createConnection(Primitives.of(
                "connectionName", "create-ms", "dbEngine", "mssql", "host", mssql[0], "port", mssql[1], "userName", mssql[2], "userPassword", mssql[3]
            ));
            dropMssqlDatabase();
        }
    }

    @AfterAll
    static void dropDatabases() {
        if (mysqlUuid == null) return;
        execute(mysqlUuid, "DROP DATABASE IF EXISTS " + DATABASE);
        if (pgsqlUuid != null) execute(pgsqlUuid, "DROP SCHEMA IF EXISTS " + DATABASE + " CASCADE");
        if (mssqlUuid != null) dropMssqlDatabase();
        TargetSessionFactory.getInstance().closeAll();
    }

    @Test
    void mysql_creates_the_database_with_its_character_set_and_collation() throws SQLException {
        GetCreateDatabaseOptionsResultDto options = getOptions(mysqlUuid);
        assertTrue(options.getCollationsByCharacterSet().containsKey("latin1"));
        assertFalse(options.getDefaultCharacterSet().isEmpty());

        create(mysqlUuid, Primitives.of("characterSet", "latin1", "collation", "latin1_spanish_ci"));
        assertEquals("latin1_spanish_ci", getFirstCell(mysqlUuid,
            "SELECT DEFAULT_COLLATION_NAME FROM information_schema.SCHEMATA WHERE SCHEMA_NAME = '" + DATABASE + "'"));
        assertTrue(getDatabaseNames(mysqlUuid).contains(DATABASE));
        create(mysqlUuid, Primitives.of("isIfNotExists", true));

        drop(mysqlUuid);
        assertFalse(getDatabaseNames(mysqlUuid).contains(DATABASE));
        assertThrows(ExplorerException.class, () -> DropDatabaseService.getInstance().invoke(DropDatabaseDto.fromPrimitives(Primitives.of(
            "connectionUuid", mysqlUuid, "sessionKey", SESSION + "-" + mysqlUuid, "databaseName", "mysql"
        ))));
    }

    @Test
    void postgresql_creates_a_schema_owned_by_the_chosen_role() throws SQLException {
        Assumptions.assumeTrue(pgsqlUuid != null, "JAVAYOG_TEST_PGSQL not set");
        GetCreateDatabaseOptionsResultDto options = getOptions(pgsqlUuid);
        assertTrue(options.getOwners().contains(options.getDefaultOwner()));

        create(pgsqlUuid, Primitives.of("owner", options.getDefaultOwner()));
        assertEquals(options.getDefaultOwner(), getFirstCell(pgsqlUuid,
            "SELECT pg_get_userbyid(nspowner) FROM pg_namespace WHERE nspname = '" + DATABASE + "'"));
        assertTrue(getDatabaseNames(pgsqlUuid).contains(DATABASE));

        execute(pgsqlUuid, "CREATE TABLE " + DATABASE + ".t (id int)");
        drop(pgsqlUuid);
        assertFalse(getDatabaseNames(pgsqlUuid).contains(DATABASE));
    }

    @Test
    void sql_server_creates_the_database_with_its_collation() throws SQLException {
        Assumptions.assumeTrue(mssqlUuid != null, "JAVAYOG_TEST_MSSQL not set");
        GetCreateDatabaseOptionsResultDto options = getOptions(mssqlUuid);
        assertTrue(options.getCollations().contains("Latin1_General_CI_AS"));

        create(mssqlUuid, Primitives.of("collation", "Latin1_General_CI_AS"));
        assertEquals("Latin1_General_CI_AS", getFirstCell(mssqlUuid,
            "SELECT collation_name FROM sys.databases WHERE name = '" + DATABASE + "'"));
        create(mssqlUuid, Primitives.of("isIfNotExists", true));

        execute(mssqlUuid, "USE " + DATABASE);
        drop(mssqlUuid);
        assertFalse(getDatabaseNames(mssqlUuid).contains(DATABASE));
    }

    private static GetCreateDatabaseOptionsResultDto getOptions(String connectionUuid) throws SQLException {
        return GetCreateDatabaseOptionsService.getInstance().invoke(GetCreateDatabaseOptionsDto.fromPrimitives(Primitives.of(
            "connectionUuid", connectionUuid, "sessionKey", SESSION + "-" + connectionUuid
        )));
    }

    private static void create(String connectionUuid, Map<String, Object> options) throws SQLException {
        CreateDatabaseService.getInstance().invoke(CreateDatabaseDto.fromPrimitives(Primitives.merge(options, Primitives.of(
            "connectionUuid", connectionUuid, "sessionKey", SESSION + "-" + connectionUuid, "databaseName", DATABASE
        ))));
    }

    private static void drop(String connectionUuid) throws SQLException {
        DropDatabaseService.getInstance().invoke(DropDatabaseDto.fromPrimitives(Primitives.of(
            "connectionUuid", connectionUuid, "sessionKey", SESSION + "-" + connectionUuid, "databaseName", DATABASE
        )));
    }

    private static List<String> getDatabaseNames(String connectionUuid) throws SQLException {
        return ListDatabasesService.getInstance().invoke(ListDatabasesDto.fromPrimitives(Primitives.of(
            "connectionUuid", connectionUuid, "sessionKey", SESSION + "-" + connectionUuid
        ))).getDatabaseNames();
    }

    private static void dropMssqlDatabase() {
        execute(mssqlUuid, "IF DB_ID('" + DATABASE + "') IS NOT NULL BEGIN ALTER DATABASE " + DATABASE
            + " SET SINGLE_USER WITH ROLLBACK IMMEDIATE; DROP DATABASE " + DATABASE + "; END");
    }

    private static String createConnection(Map<String, Object> form) {
        return String.valueOf(CreateConnectionService.getInstance().invoke(CreateConnectionDto.fromPrimitives(form)).getConnection().get(ConnectionsKeyEnum.CONNECTION_UUID));
    }

    @SuppressWarnings("unchecked")
    private static String getFirstCell(String connectionUuid, String sql) {
        ExecuteQueryResultDto result = execute(connectionUuid, sql);
        Object errorMessage = result.getResults().getFirst().get(QueryKeyEnum.ERROR_MESSAGE);
        org.junit.jupiter.api.Assertions.assertNull(errorMessage, String.valueOf(errorMessage));
        List<List<Object>> rows = (List<List<Object>>) result.getResults().getFirst().get(QueryKeyEnum.ROWS);
        return rows.isEmpty() ? null : String.valueOf(rows.getFirst().getFirst());
    }

    private static ExecuteQueryResultDto execute(String connectionUuid, String sql) {
        return ExecuteQueryService.getInstance().invoke(ExecuteQueryDto.fromPrimitives(Primitives.of(
            "connectionUuid", connectionUuid,
            "sessionKey", SESSION + "-query-" + connectionUuid,
            "databaseName", "",
            "sqlQuery", sql,
            "maxRows", 0,
            "isConfirmed", true
        )));
    }
}
