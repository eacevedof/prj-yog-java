package modules.integration;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import modules.shared.domain.enums.ExceptionEnum;
import modules.shared.application.Primitives;

import testcase.AbstractTestCase;

import modules.connections.domain.enums.ConnectionsKeyEnum;
import modules.connections.application.createconnection.CreateConnectionDto;
import modules.connections.application.createconnection.CreateConnectionService;
import modules.connections.infrastructure.factories.TargetSessionFactory;

import modules.explorer.domain.enums.ForeignKeyActionEnum;
import modules.explorer.domain.enums.TableIndexTypeEnum;
import modules.explorer.domain.entities.TableColumnEntity;
import modules.explorer.domain.entities.TableDefinitionEntity;
import modules.explorer.domain.entities.TableForeignKeyEntity;
import modules.explorer.domain.entities.TableIndexEntity;
import modules.explorer.domain.services.TableAlterSqlBuilder;
import modules.explorer.application.altertable.AlterTableDto;
import modules.explorer.application.altertable.AlterTableResultDto;
import modules.explorer.application.createtable.CreateTableDto;
import modules.explorer.application.gettabledefinition.GetTableDefinitionDto;
import modules.explorer.application.altertable.AlterTableService;
import modules.explorer.application.createtable.CreateTableService;
import modules.explorer.application.gettabledefinition.GetTableDefinitionService;
import modules.explorer.domain.exceptions.ExplorerException;

import modules.query.domain.enums.QueryKeyEnum;
import modules.query.application.executequery.ExecuteQueryDto;
import modules.query.application.executequery.ExecuteQueryResultDto;
import modules.query.application.executequery.ExecuteQueryService;

/**
 * El editor de tablas ("Alter Table") contra un MySQL de verdad (JAVAYOG_TEST_MYSQL; make test-mysql): lo leido se
 * vuelve a generar sin cambios espurios, y una edicion completa (renombrar, mover, cambiar tipo, DEFAULT, indice, FK,
 * opciones y nombre de la tabla) queda en el servidor tal cual se pidio, sin perder los datos.
 */
final class TableEditorIntegrationTest extends AbstractTestCase {

    private static final String DATABASE = "javayog_alter_it";
    private static final String SESSION = "ctab-alter-it:explorer";

    private static String connectionUuid;
    private static String productionConnectionUuid;

    @BeforeAll
    static void seed() {
        String testMysql = System.getenv("JAVAYOG_TEST_MYSQL");
        Assumptions.assumeTrue(testMysql != null && !testMysql.isBlank(), "JAVAYOG_TEST_MYSQL not set: table editor tests skipped");
        String[] mysql = testMysql.split(":", 4);
        bootFreshDatabase("TableEditorIntegrationTest");
        connectionUuid = createConnection(Primitives.of("connectionName", "alter-it", "host", mysql[0], "port", mysql[1], "userName", mysql[2], "userPassword", mysql[3]));
        productionConnectionUuid = createConnection(Primitives.of(
            "connectionName", "alter-it-prod", "host", mysql[0], "port", mysql[1], "userName", mysql[2], "userPassword", mysql[3], "isProduction", true
        ));
        ExecuteQueryResultDto seedResult = execute("""
            DROP DATABASE IF EXISTS javayog_alter_it;
            CREATE DATABASE javayog_alter_it CHARACTER SET utf8mb4;
            USE javayog_alter_it;
            CREATE TABLE countries (code CHAR(2) PRIMARY KEY, name VARCHAR(40) NOT NULL);
            INSERT INTO countries VALUES ('ES', 'España'), ('NL', 'Nederland');
            CREATE TABLE clients (
                id INT UNSIGNED NOT NULL AUTO_INCREMENT,
                name VARCHAR(50) NOT NULL DEFAULT 'sin nombre' COMMENT 'nombre ''comercial''',
                email VARCHAR(100) NULL,
                balance DECIMAL(10,2) NOT NULL DEFAULT 0.00,
                country CHAR(2) NULL,
                created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                status ENUM('alta','baja') NOT NULL DEFAULT 'alta',
                PRIMARY KEY (id),
                UNIQUE KEY uq_email (email),
                KEY ix_name_prefix (name(10)),
                CONSTRAINT fk_country FOREIGN KEY (country) REFERENCES countries (code) ON DELETE SET NULL
            ) ENGINE=InnoDB COMMENT='clientes';
            INSERT INTO clients (name, email, balance, country) VALUES ('Ana', 'ana@x.es', 10.5, 'ES'), ('Bob', NULL, 0, 'NL');
            """);
        for (Map<String, Object> result : seedResult.getResults()) {
            org.junit.jupiter.api.Assertions.assertNull(result.get(QueryKeyEnum.ERROR_MESSAGE), String.valueOf(result.get(QueryKeyEnum.ERROR_MESSAGE)));
        }
    }

    @AfterAll
    static void dropDatabase() {
        if (connectionUuid == null) return;
        execute("DROP DATABASE IF EXISTS javayog_alter_it");
        TargetSessionFactory.getInstance().closeAll();
    }

    @Test
    void testReadDefinitionRegeneratesNothing() throws SQLException {
        TableDefinitionEntity clients = getDefinition("clients");

        assertEquals(List.of(), TableAlterSqlBuilder.getInstance().getStatements(DATABASE, clients, clients));
        assertEquals(List.of(), TableAlterSqlBuilder.getInstance().getErrors(clients));
        assertEquals(List.of("id"), clients.primaryKeyColumns());
        TableColumnEntity name = clients.columns().get(1);
        assertEquals("varchar", name.dataType());
        assertEquals("50", name.length());
        assertEquals("'sin nombre'", name.defaultSql());
        assertEquals("nombre 'comercial'", name.comment());
        assertTrue(clients.columns().getFirst().isUnsigned());
        assertTrue(clients.columns().getFirst().isAutoIncrement());
        assertEquals("CURRENT_TIMESTAMP", clients.columns().get(5).defaultSql());
        assertEquals("CURRENT_TIMESTAMP", clients.columns().get(5).onUpdateSql());
        assertEquals("'alta','baja'", clients.columns().get(6).length());
        // MySQL crea un indice para cada clave foranea (fk_country): tambien se ve y se edita
        assertEquals(List.of("fk_country", "ix_name_prefix", "uq_email"), clients.indexes().stream().map(TableIndexEntity::name).toList());
        assertEquals(List.of("name(10)"), clients.indexes().get(1).columns());
        assertEquals(ForeignKeyActionEnum.SET_NULL, clients.foreignKeys().getFirst().onDelete());
        assertEquals("clientes", clients.comment());
    }

    @Test
    void testAlterEverythingKeepsTheData() throws SQLException {
        TableDefinitionEntity original = getDefinition("clients");
        List<TableColumnEntity> columns = new ArrayList<>(original.columns());
        TableColumnEntity email = columns.remove(2);
        TableColumnEntity balance = columns.get(2);
        // renombrar y mover email al final, cambiar balance a 12,3 sin DEFAULT, quitar status y añadir phone tras name
        columns.set(2, new TableColumnEntity(balance.originalName(), "balance", "decimal", "12,3", false, false, true, false, "", "", "", "", "saldo", false));
        columns.removeIf(column -> column.name().equals("status"));
        columns.add(2, new TableColumnEntity("", "phone", "varchar", "20", false, false, false, false, "NULL", "", "", "", "", false));
        columns.add(email.withName("mail"));
        TableDefinitionEntity edited = new TableDefinitionEntity(
            "customers",
            columns,
            original.primaryKeyColumns(),
            List.of(
                original.indexes().get(0),
                original.indexes().get(1),
                new TableIndexEntity("uq_email", "uq_mail", TableIndexTypeEnum.UNIQUE, List.of("mail")),
                new TableIndexEntity("", "ix_phone", TableIndexTypeEnum.INDEX, List.of("phone"))
            ),
            List.of(new TableForeignKeyEntity("fk_country", "fk_country", List.of("country"), "countries", List.of("code"), ForeignKeyActionEnum.CASCADE, ForeignKeyActionEnum.CASCADE)),
            original.engine(),
            "utf8mb4_unicode_ci",
            "clientes de verdad",
            "",
            original.rowFormat()
        );

        AlterTableResultDto alterResult = AlterTableService.getInstance().invoke(AlterTableDto.fromPrimitives(Primitives.of(
            "connectionUuid", connectionUuid, "sessionKey", SESSION, "databaseName", DATABASE, "original", original, "edited", edited
        )));
        TableDefinitionEntity reread = getDefinition("customers");
        ExecuteQueryResultDto rows = execute("SELECT id, name, phone, balance, country, created_at IS NOT NULL, mail FROM javayog_alter_it.customers ORDER BY id");
        ExplorerException productionException = assertThrows(ExplorerException.class, () -> AlterTableService.getInstance().invoke(AlterTableDto.fromPrimitives(Primitives.of(
            "connectionUuid", productionConnectionUuid, "sessionKey", "ctab-alter-it-prod:explorer", "databaseName", DATABASE,
            "original", reread, "edited", new TableDefinitionEntity("customers", reread.columns(), reread.primaryKeyColumns(), reread.indexes(), reread.foreignKeys(),
                reread.engine(), reread.collation(), "otro", "", reread.rowFormat())
        ))));

        assertEquals("customers", alterResult.getTableName());
        assertTrue(alterResult.getStatements().getFirst().contains("DROP FOREIGN KEY `fk_country`"));
        assertTrue(alterResult.getStatements().getLast().startsWith("RENAME TABLE"));
        assertEquals(List.of("id", "name", "phone", "balance", "country", "created_at", "mail"), reread.columns().stream().map(TableColumnEntity::name).toList());
        assertEquals("12,3", reread.columns().get(3).length());
        assertEquals("saldo", reread.columns().get(3).comment());
        assertEquals("CURRENT_TIMESTAMP", reread.columns().get(5).onUpdateSql());
        assertEquals(List.of("fk_country", "ix_name_prefix", "ix_phone", "uq_mail"), reread.indexes().stream().map(TableIndexEntity::name).toList());
        assertEquals(ForeignKeyActionEnum.CASCADE, reread.foreignKeys().getFirst().onDelete());
        assertEquals("clientes de verdad", reread.comment());
        assertEquals("utf8mb4_unicode_ci", reread.collation());
        assertEquals(List.of(
            Arrays.asList("1", "Ana", null, "10.500", "ES", "1", "ana@x.es"),
            Arrays.asList("2", "Bob", null, "0.000", "NL", "1", null)
        ), rows.getResults().getFirst().get(QueryKeyEnum.ROWS));
        assertEquals(ExceptionEnum.CODE_PRECONDITION_REQUIRED, productionException.getCode());
    }

    @Test
    @SuppressWarnings("unchecked")
    void testCreateTableWithKeysAndOptions() throws SQLException {
        TableDefinitionEntity orders = new TableDefinitionEntity(
            "orders",
            List.of(
                new TableColumnEntity("", "id", "int", "", true, false, true, true, "", "", "", "", "", false),
                new TableColumnEntity("", "client_id", "int", "", true, false, false, false, "NULL", "", "", "", "", false),
                new TableColumnEntity("", "total", "decimal", "10,2", false, false, true, false, "0.00", "", "", "", "con IVA", false)
            ),
            List.of("id"),
            List.of(new TableIndexEntity("", "ix_total", TableIndexTypeEnum.INDEX, List.of("total"))),
            List.of(new TableForeignKeyEntity("", "fk_client", List.of("client_id"), "clients", List.of("id"), ForeignKeyActionEnum.CASCADE, ForeignKeyActionEnum.RESTRICT)),
            "InnoDB", "", "pedidos", "", ""
        );

        ExplorerException notConfirmed = assertThrows(ExplorerException.class, () -> createTable(productionConnectionUuid, orders, false));
        String createdTableName = createTable(connectionUuid, orders, false);
        TableDefinitionEntity created = getDefinition("orders");
        TableDefinitionEntity ontoCountries = new TableDefinitionEntity("countries", orders.columns(), List.of("id"), List.of(), List.of(), "", "", "", "", "");
        assertThrows(SQLException.class, () -> createTable(connectionUuid, ontoCountries, false));
        List<List<String>> countriesCount = (List<List<String>>) execute("SELECT COUNT(*) FROM javayog_alter_it.countries").getResults().getFirst().get(QueryKeyEnum.ROWS);

        assertEquals(ExceptionEnum.CODE_PRECONDITION_REQUIRED, notConfirmed.getCode());
        assertEquals("orders", createdTableName);
        assertEquals(List.of("id", "client_id", "total"), created.columns().stream().map(TableColumnEntity::name).toList());
        assertEquals(List.of("id"), created.primaryKeyColumns());
        assertEquals(List.of("fk_client"), created.foreignKeys().stream().map(TableForeignKeyEntity::name).toList());
        assertTrue(created.indexes().stream().anyMatch(index -> index.name().equals("ix_total")));
        assertEquals("pedidos", created.comment());
        assertEquals("con IVA", created.columns().get(2).comment());
        assertEquals("2", countriesCount.getFirst().getFirst());
    }

    @Test
    void testErrorsBeforeTouchingTheServer() throws SQLException {
        TableDefinitionEntity countries = getDefinition("countries");
        List<TableColumnEntity> columns = new ArrayList<>(countries.columns());
        columns.add(new TableColumnEntity("", "con espacio", "int", "", false, false, false, false, "", "", "", "", "", false));
        columns.add(new TableColumnEntity("", "code", "", "", false, false, false, false, "", "", "", "", "", false));
        TableDefinitionEntity edited = new TableDefinitionEntity("países", columns, List.of("nope"), List.of(), List.of(),
            countries.engine(), countries.collation(), countries.comment(), "", countries.rowFormat());

        List<String> errors = TableAlterSqlBuilder.getInstance().getErrors(edited);
        ExplorerException nothingToDo = assertThrows(ExplorerException.class, () -> AlterTableService.getInstance().invoke(AlterTableDto.fromPrimitives(Primitives.of(
            "connectionUuid", connectionUuid, "sessionKey", SESSION, "databaseName", DATABASE, "original", countries, "edited", countries
        ))));

        assertEquals(5, errors.size());
        assertTrue(nothingToDo.getMessage().contains("nothing to change"));
    }

    private static TableDefinitionEntity getDefinition(String tableName) throws SQLException {
        return GetTableDefinitionService.getInstance().invoke(GetTableDefinitionDto.fromPrimitives(Primitives.of(
            "connectionUuid", connectionUuid, "sessionKey", SESSION, "databaseName", DATABASE, "tableName", tableName
        ))).getTableDefinition();
    }

    private static String createTable(String connectionUuid, TableDefinitionEntity edited, boolean isConfirmed) throws SQLException {
        return CreateTableService.getInstance().invoke(CreateTableDto.fromPrimitives(Primitives.of(
            "connectionUuid", connectionUuid, "sessionKey", SESSION, "databaseName", DATABASE, "edited", edited, "isConfirmed", isConfirmed
        ))).getTableName();
    }

    private static String createConnection(Map<String, Object> form) {
        return String.valueOf(CreateConnectionService.getInstance().invoke(CreateConnectionDto.fromPrimitives(form))
            .getConnection().get(ConnectionsKeyEnum.CONNECTION_UUID));
    }

    private static ExecuteQueryResultDto execute(String sql) {
        return ExecuteQueryService.getInstance().invoke(ExecuteQueryDto.fromPrimitives(Primitives.of(
            "connectionUuid", connectionUuid, "sessionKey", "ctab-alter-it:query-1", "databaseName", "", "sqlQuery", sql, "maxRows", 0, "isConfirmed", true
        )));
    }
}
