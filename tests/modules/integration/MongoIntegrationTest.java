package modules.integration;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import modules.shared.domain.enums.ExceptionEnum;
import modules.shared.application.Primitives;
import modules.shared.infrastructure.exceptions.FieldsException;

import testcase.AbstractTestCase;

import modules.connections.domain.enums.ConnectionsKeyEnum;
import modules.connections.application.createconnection.CreateConnectionDto;
import modules.connections.application.testconnection.TestConnectionDto;
import modules.connections.application.createconnection.CreateConnectionService;
import modules.connections.application.testconnection.TestConnectionService;
import modules.connections.infrastructure.factories.MongoSessionFactory;

import modules.mongo.application.executecommand.ExecuteMongoCommandDto;
import modules.mongo.application.executecommand.ExecuteMongoCommandResultDto;
import modules.mongo.application.finddocuments.FindMongoDocumentsDto;
import modules.mongo.application.finddocuments.FindMongoDocumentsResultDto;
import modules.mongo.application.listcollections.ListMongoCollectionsDto;
import modules.mongo.application.listdatabases.ListMongoDatabasesDto;
import modules.mongo.application.savedocument.SaveMongoDocumentDto;
import modules.mongo.application.savedocument.SaveMongoDocumentResultDto;
import modules.mongo.application.executecommand.ExecuteMongoCommandService;
import modules.mongo.application.finddocuments.FindMongoDocumentsService;
import modules.mongo.application.listcollections.ListMongoCollectionsService;
import modules.mongo.application.listdatabases.ListMongoDatabasesService;
import modules.mongo.application.savedocument.SaveMongoDocumentService;
import modules.mongo.domain.exceptions.MongoModuleException;

/**
 * MongoDB: solo si existe JAVAYOG_TEST_MONGO=host:puerto:usuario:password (make test-mysql levanta un Mongo 7
 * desechable). Usa la BD javayog_it y la borra al acabar. Con JAVAYOG_TEST_SSH y JAVAYOG_TEST_MONGO_FROM_SSH, tambien
 * por el tunel SSH.
 */
final class MongoIntegrationTest extends AbstractTestCase {

    private static final String DATABASE = "javayog_it";
    private static final String BROWSER_SESSION = "ctab-mongo:mongo-browser";
    private static final String CONSOLE_SESSION = "ctab-mongo:mongo-console";

    private static String[] mongo;
    private static String connectionUuid;
    private static String productionUuid;
    // las comprobaciones se repiten por el tunel HTTP (testHttpTunnelToMongoBehavesLikeTheDriver)
    private static boolean isTunnelRun;

    @BeforeAll
    static void seed() {
        String testMongo = System.getenv("JAVAYOG_TEST_MONGO");
        Assumptions.assumeTrue(testMongo != null && !testMongo.isBlank(), "JAVAYOG_TEST_MONGO not set: MongoDB tests skipped");
        mongo = testMongo.split(":", 4);

        bootFreshDatabase("MongoIntegrationTest");
        connectionUuid = createConnection("mongo-it", false);
        productionUuid = createConnection("mongo-it-prod", true);
        seedData(connectionUuid);
    }

    /**
     * La BD de prueba desde cero: la siembra la consola (por la conexion que se le pase).
     */
    private static void seedData(String connectionUuid) {
        for (String command : List.of(
            "db.dropDatabase()",
            "db.users.insertMany([{_id: 1, name: \"Ána\", age: 34, tags: [\"a\", \"b\"], plan: {kind: \"pro\"}}, {_id: 2, name: \"bob\", age: 17}, {_id: 3, name: \"carla\", age: 51, big: NumberLong(5), at: ISODate(\"2026-01-02T03:04:05Z\")}])",
            "db.orders.insertOne({user: 1, total: 12.5})",
            "db.users.createIndex({name: 1}, {unique: true, name: \"ux_name\"})"
        )) {
            ExecuteMongoCommandResultDto result = command(connectionUuid, command, false);
            org.junit.jupiter.api.Assertions.assertFalse(result.isError(), command + " -> " + result.getOutput());
        }
    }

    @AfterAll
    static void drop() throws SQLException {
        if (connectionUuid == null) return;
        command(connectionUuid, "db.dropDatabase()", false);
        MongoSessionFactory.getInstance().closeAll();
    }

    @Test
    void testConnectionTestAndValidation() throws SQLException {
        String version = TestConnectionService.getInstance().invoke(TestConnectionDto.fromPrimitives(getForm("mongo-test", mongo[3]))).getServerVersion();
        SQLException wrongPassword = assertThrows(SQLException.class, () -> TestConnectionService.getInstance().invoke(
            TestConnectionDto.fromPrimitives(getForm("mongo-bad", "wrong"))
        ));
        FieldsException uriWithCredentials = assertThrows(FieldsException.class, () -> CreateConnectionService.getInstance().invoke(CreateConnectionDto.fromPrimitives(Primitives.merge(
            getForm("mongo-uri", ""), Primitives.of("host", "mongodb+srv://u:p@cluster0.example.net/?retryWrites=true")
        ))));

        assertTrue(version.startsWith("MongoDB 7"));
        assertTrue(wrongPassword.getMessage().contains("uthenticat"));
        assertEquals(1, uriWithCredentials.getErrors().size());
    }

    @Test
    void testDatabasesCollectionsAndFind() {
        List<String> databases = ListMongoDatabasesService.getInstance().invoke(ListMongoDatabasesDto.fromPrimitives(Primitives.of(
            "connectionUuid", connectionUuid, "sessionKey", BROWSER_SESSION
        ))).getDatabaseNames();
        List<Map<String, Object>> collections = ListMongoCollectionsService.getInstance().invoke(ListMongoCollectionsDto.fromPrimitives(Primitives.of(
            "connectionUuid", connectionUuid, "sessionKey", BROWSER_SESSION, "databaseName", DATABASE
        ))).getCollections();
        FindMongoDocumentsResultDto adults = find("{age: {$gte: 18}}", "{age: -1}");
        MongoModuleException badFilter = assertThrows(MongoModuleException.class, () -> find("{age: ", ""));

        assertTrue(databases.contains(DATABASE));
        assertEquals(List.of("orders", "users"), collections.stream().map(collection -> String.valueOf(collection.get("name"))).toList());
        assertEquals(3L, ((Number) collections.get(1).get("count")).longValue());
        assertEquals(2L, adults.getTotalCount());
        assertEquals(List.of("_id", "name", "age", "big", "at", "tags", "plan"), adults.getColumns());
        assertEquals("carla", adults.getRows().getFirst().get(1));
        // por el tunel, el Extended JSON relajado convierte un Int64 pequeño en numero (gap anotado en sqlyog-tunnel-doing)
        assertEquals(isTunnelRun ? "5" : "NumberLong(5)", adults.getRows().getFirst().get(3));
        assertTrue(adults.getRows().getFirst().get(4).startsWith("ISODate("));
        assertEquals("{\"kind\": \"pro\"}", adults.getRows().get(1).get(6));
        assertTrue(isTunnelRun || adults.getDocumentsJson().getFirst().contains("NumberLong(5)"));
        assertEquals(ExceptionEnum.CODE_BAD_REQUEST, badFilter.getCode());
    }

    @Test
    void testSaveKeepsTypesAndProductionAsks() {
        String carla = find("{_id: 3}", "").getDocumentsJson().getFirst();
        save(connectionUuid, carla.replace("\"carla\"", "\"Carla R.\""), false, false);
        SaveMongoDocumentResultDto inserted = save(connectionUuid, "{name: \"nuevo\", age: 1}", true, false);
        save(connectionUuid, "{_id: 2}", false, true);
        MongoModuleException gone = assertThrows(MongoModuleException.class, () -> save(connectionUuid, "{_id: 2, name: \"x\"}", false, false));
        MongoModuleException production = assertThrows(MongoModuleException.class, () -> save(productionUuid, "{_id: 1}", false, true));
        String saved = find("{_id: 3}", "").getDocumentsJson().getFirst();

        assertTrue(saved.contains("\"Carla R.\""));
        assertTrue(isTunnelRun || saved.contains("NumberLong(5)"));
        org.junit.jupiter.api.Assertions.assertTrue(saved.contains("ISODate("), saved);
        assertTrue(inserted.getIdJson().startsWith("ObjectId("));
        assertEquals(ExceptionEnum.CODE_NOT_FOUND, gone.getCode());
        assertEquals(ExceptionEnum.CODE_PRECONDITION_REQUIRED, production.getCode());
    }

    @Test
    void testConsoleLikeMongosh() throws SQLException {
        ExecuteMongoCommandResultDto findOutput = command(connectionUuid, "db.orders.find({}, {_id: 0}).limit(5)", false);
        ExecuteMongoCommandResultDto count = command(connectionUuid, "db.users.countDocuments({age: {$lt: 100}})", false);
        ExecuteMongoCommandResultDto aggregate = command(connectionUuid, "db.orders.aggregate([{$group: {_id: null, total: {$sum: \"$total\"}}}])", false);
        ExecuteMongoCommandResultDto indexes = command(connectionUuid, "db.users.getIndexes()", false);
        ExecuteMongoCommandResultDto used = command(connectionUuid, "use otra", false);
        ExecuteMongoCommandResultDto unknown = command(connectionUuid, "db.users.mapReduce()", false);
        ExecuteMongoCommandResultDto duplicate = command(connectionUuid, "db.users.insertOne({name: \"bob\"})", false);
        ExecuteMongoCommandResultDto ping = command(connectionUuid, "{ping: 1}", false);
        MongoModuleException production = assertThrows(MongoModuleException.class, () -> command(productionUuid, "db.users.deleteMany({})", false));

        assertTrue(findOutput.getOutput().contains("\"total\": 12.5"));
        assertTrue(findOutput.getOutput().endsWith("(1 documento(s))"));
        assertTrue(count.getOutput().matches("[0-9]+"));
        assertTrue(aggregate.getOutput().contains("\"total\": 12.5"));
        assertTrue(indexes.getOutput().contains("ux_name"));
        assertEquals("otra", used.getCurrentDatabase());
        assertTrue(unknown.isError());
        assertTrue(unknown.getOutput().contains("Unsupported method mapReduce"));
        assertTrue(duplicate.isError());
        assertTrue(duplicate.getOutput().contains("E11000"));
        assertTrue(ping.getOutput().contains("\"ok\": 1"));
        assertEquals(ExceptionEnum.CODE_PRECONDITION_REQUIRED, production.getCode());
    }

    /**
     * Feature 12 con MongoDB: por el tunel SSH (conexion directa al nodo).
     */
    @Test
    void testSshTunnelToMongo() throws SQLException {
        String testSsh = System.getenv("JAVAYOG_TEST_SSH");
        String mongoFromSsh = System.getenv("JAVAYOG_TEST_MONGO_FROM_SSH");
        Assumptions.assumeTrue(testSsh != null && mongoFromSsh != null, "SSH tunnel to MongoDB not configured");
        String[] ssh = testSsh.split(":", 6);
        String[] target = mongoFromSsh.split(":", 2);

        String version = TestConnectionService.getInstance().invoke(TestConnectionDto.fromPrimitives(Primitives.of(
            "connectionName", "mongo-ssh", "dbEngine", "mongodb", "host", target[0], "port", target[1], "userName", mongo[2], "userPassword", mongo[3],
            "sshEnabled", true, "sshHost", ssh[0], "sshPort", ssh[1], "sshUser", ssh[2], "sshPassword", ssh[3]
        ))).getServerVersion();

        assertTrue(version.startsWith("MongoDB 7"));
    }

    /**
     * Feature 13 con MongoDB (contrato sqlyog-tunnel-multidbs §5): la BD se siembra por el tunel (insertMany,
     * insertOne, createIndex como runCommand) y se repiten las comprobaciones del navegador, el guardado y la consola.
     * Al acabar se vuelve a sembrar por la via directa, para que el resto de tests no dependa del orden.
     */
    @Test
    void testHttpTunnelToMongoBehavesLikeTheDriver() throws SQLException {
        String testHttp = System.getenv("JAVAYOG_TEST_HTTP");
        String mongoFromTunnel = System.getenv("JAVAYOG_TEST_MONGO_FROM_TUNNEL");
        Assumptions.assumeTrue(testHttp != null && mongoFromTunnel != null, "HTTP tunnel to MongoDB not configured");
        String[] target = mongoFromTunnel.split(":", 2);
        Map<String, Object> tunnelForm = Primitives.of(
            "connectionName", "mongo-tunnel", "dbEngine", "mongodb", "host", target[0], "port", target[1], "userName", mongo[2],
            "userPassword", mongo[3], "databaseName", DATABASE, "httpEnabled", true, "httpUrl", testHttp.split("\\|")[0], "httpTimeoutS", 20
        );
        String version = TestConnectionService.getInstance().invoke(TestConnectionDto.fromPrimitives(tunnelForm)).getServerVersion();
        FieldsException uriByHttp = assertThrows(FieldsException.class, () -> CreateConnectionService.getInstance().invoke(CreateConnectionDto.fromPrimitives(
            Primitives.merge(tunnelForm, Primitives.of("connectionName", "mongo-uri-http", "host", "mongodb+srv://cluster0.example.net"))
        )));
        String directUuid = connectionUuid;
        connectionUuid = String.valueOf(CreateConnectionService.getInstance().invoke(CreateConnectionDto.fromPrimitives(tunnelForm))
            .getConnection().get(ConnectionsKeyEnum.CONNECTION_UUID));
        isTunnelRun = true;
        try {
            seedData(connectionUuid);
            testDatabasesCollectionsAndFind();
            testConsoleLikeMongosh();
            testSaveKeepsTypesAndProductionAsks();
            ExecuteMongoCommandResultDto updated = command(connectionUuid, "db.users.updateMany({age: {$gt: 30}}, {$set: {senior: true}})", false);
            ExecuteMongoCommandResultDto distinct = command(connectionUuid, "db.users.distinct(\"name\", {})", false);
            ExecuteMongoCommandResultDto show = command(connectionUuid, "show collections", false);
            assertTrue(updated.getOutput().contains("\"matchedCount\": NumberLong(2)"));
            assertTrue(distinct.getOutput().contains("Carla R."));
            assertEquals("orders\nusers", String.join("\n", show.getOutput().lines().sorted().toList()));
        }
        finally {
            isTunnelRun = false;
            connectionUuid = directUuid;
            seedData(connectionUuid);
        }
        assertTrue(version.startsWith("MongoDB 7") && version.contains("HTTP tunnel"));
        assertEquals(1, uriByHttp.getErrors().size());
    }

    private static Map<String, Object> getForm(String connectionName, String password) {
        return Primitives.of(
            "connectionName", connectionName, "dbEngine", "mongodb",
            "host", mongo[0], "port", mongo[1], "userName", mongo[2], "userPassword", password, "databaseName", DATABASE
        );
    }

    private static String createConnection(String connectionName, boolean isProduction) {
        return String.valueOf(CreateConnectionService.getInstance().invoke(CreateConnectionDto.fromPrimitives(Primitives.merge(
            getForm(connectionName, mongo[3]), Primitives.of("isProduction", isProduction)
        ))).getConnection().get(ConnectionsKeyEnum.CONNECTION_UUID));
    }

    private FindMongoDocumentsResultDto find(String filterJson, String sortJson) {
        return FindMongoDocumentsService.getInstance().invoke(FindMongoDocumentsDto.fromPrimitives(Primitives.of(
            "connectionUuid", connectionUuid, "sessionKey", BROWSER_SESSION, "databaseName", DATABASE, "collectionName", "users",
            "filterJson", filterJson, "sortJson", sortJson
        )));
    }

    private static SaveMongoDocumentResultDto save(String connectionUuid, String documentJson, boolean isNew, boolean isDelete) {
        return SaveMongoDocumentService.getInstance().invoke(SaveMongoDocumentDto.fromPrimitives(Primitives.of(
            "connectionUuid", connectionUuid, "sessionKey", BROWSER_SESSION, "databaseName", DATABASE, "collectionName", "users",
            "documentJson", documentJson, "isNew", isNew, "isDelete", isDelete
        )));
    }

    private static ExecuteMongoCommandResultDto command(String connectionUuid, String commandLine, boolean isConfirmed) {
        return ExecuteMongoCommandService.getInstance().invoke(ExecuteMongoCommandDto.fromPrimitives(Primitives.of(
            "connectionUuid", connectionUuid, "sessionKey", CONSOLE_SESSION, "databaseName", DATABASE, "commandLine", commandLine, "isConfirmed", isConfirmed
        )));
    }
}
