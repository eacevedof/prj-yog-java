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
import modules.connections.infrastructure.factories.RedisSessionFactory;

import modules.redis.domain.enums.RedisKeyEnum;
import modules.redis.application.changekey.ChangeKeyDto;
import modules.redis.application.createkey.CreateKeyDto;
import modules.redis.application.executecommand.ExecuteRedisCommandDto;
import modules.redis.application.executecommand.ExecuteRedisCommandResultDto;
import modules.redis.application.getkeyvalue.GetKeyValueDto;
import modules.redis.application.getkeyvalue.GetKeyValueResultDto;
import modules.redis.application.scankeys.ScanKeysDto;
import modules.redis.application.scankeys.ScanKeysResultDto;
import modules.redis.application.changekey.ChangeKeyService;
import modules.redis.application.createkey.CreateKeyService;
import modules.redis.application.executecommand.ExecuteRedisCommandService;
import modules.redis.application.getkeyvalue.GetKeyValueService;
import modules.redis.application.scankeys.ScanKeysService;
import modules.redis.domain.exceptions.RedisException;

/**
 * Redis: solo si existe JAVAYOG_TEST_REDIS=host:puerto:password (make test-mysql levanta un Redis 7 desechable). Usa la
 * BD 7 y la vacia al empezar. Con JAVAYOG_TEST_SSH y JAVAYOG_TEST_REDIS_FROM_SSH=host:puerto, tambien por el tunel SSH.
 */
final class RedisIntegrationTest extends AbstractTestCase {

    private static final String DATABASE_INDEX = "7";
    private static final String BROWSER_SESSION = "ctab-redis:redis-browser";
    private static final String CONSOLE_SESSION = "ctab-redis:redis-console";

    private static String[] redis;
    private static String connectionUuid;
    private static String productionUuid;

    @BeforeAll
    static void seedKeys() {
        String testRedis = System.getenv("JAVAYOG_TEST_REDIS");
        Assumptions.assumeTrue(testRedis != null && !testRedis.isBlank(), "JAVAYOG_TEST_REDIS not set: Redis tests skipped");
        redis = testRedis.split(":", 3);

        bootFreshDatabase("RedisIntegrationTest");
        connectionUuid = createConnection("redis-it", false);
        productionUuid = createConnection("redis-it-prod", true);
        for (String command : List.of(
            "FLUSHDB",
            "SET user:1:name \"Ána \\\"la\\\" grande\"",
            "SET user:2:name bob EX 3600",
            "HSET user:1 email ana@x.es plan pro",
            "RPUSH queue:jobs a b c",
            "SADD tags red green",
            "ZADD ranking 10 ana 2.5 bob",
            "XADD events * type login user ana",
            "SET blob \"\\xca\\xfe\\xba\\xbe\""
        )) {
            ExecuteRedisCommandResultDto result = command(connectionUuid, command, false);
            org.junit.jupiter.api.Assertions.assertFalse(result.isError(), command + " -> " + result.getOutput());
        }
    }

    @AfterAll
    static void closeSessions() throws SQLException {
        if (connectionUuid == null) return;
        command(connectionUuid, "FLUSHDB", false);
        RedisSessionFactory.getInstance().closeAll();
    }

    @Test
    void testConnectionTestAndValidation() throws SQLException {
        String version = TestConnectionService.getInstance().invoke(TestConnectionDto.fromPrimitives(getForm("redis-test", redis[2]))).getServerVersion();
        SQLException wrongPassword = assertThrows(SQLException.class, () -> TestConnectionService.getInstance().invoke(
            TestConnectionDto.fromPrimitives(getForm("redis-bad", "wrong"))
        ));
        FieldsException wrongFields = assertThrows(FieldsException.class, () -> CreateConnectionService.getInstance().invoke(CreateConnectionDto.fromPrimitives(Primitives.merge(
            getForm("redis-wrong", ""), Primitives.of("databaseName", "abc", "httpEnabled", true, "httpUrl", "https://x/y")
        ))));

        assertTrue(version.startsWith("Redis 7"));
        assertTrue(wrongPassword.getMessage().contains("WRONGPASS"));
        assertEquals(1, wrongFields.getErrors().size());
    }

    @Test
    void testScanReturnsKeysWithTypeAndTtl() {
        ScanKeysResultDto all = scanAll("*");
        ScanKeysResultDto users = scanAll("user:*");

        assertEquals(8, all.getKeys().size());
        assertEquals(List.of("user:1", "user:1:name", "user:2:name"), users.getKeys().stream().map(key -> String.valueOf(key.get(RedisKeyEnum.KEY))).sorted().toList());
        Map<String, Object> withTtl = users.getKeys().stream().filter(key -> "user:2:name".equals(key.get(RedisKeyEnum.KEY))).findFirst().orElseThrow();
        assertEquals("string", withTtl.get(RedisKeyEnum.TYPE));
        assertTrue(((Number) withTtl.get(RedisKeyEnum.TTL_MS)).longValue() > 3_000_000);
    }

    @Test
    void testNewKeysOfEachTypeNeverOverwrite() {
        try {
            createKey(Primitives.of("key", "new:string", "type", RedisKeyEnum.TYPE_STRING, "value", "hola", "ttlSeconds", "600"));
            createKey(Primitives.of("key", "new:hash", "type", RedisKeyEnum.TYPE_HASH, "field", "email", "value", "a@x.es"));
            createKey(Primitives.of("key", "new:zset", "type", RedisKeyEnum.TYPE_ZSET, "value", "ana", "score", "3.5"));
            RedisException existing = assertThrows(RedisException.class, () -> createKey(Primitives.of("key", "user:1:name", "type", RedisKeyEnum.TYPE_STRING, "value", "x")));
            RedisException hashWithoutField = assertThrows(RedisException.class, () -> createKey(Primitives.of("key", "new:hash2", "type", RedisKeyEnum.TYPE_HASH, "value", "x")));
            RedisException stream = assertThrows(RedisException.class, () -> createKey(Primitives.of("key", "new:stream", "type", RedisKeyEnum.TYPE_STREAM, "value", "x")));

            GetKeyValueResultDto string = getValue("new:string");
            assertEquals("hola", string.getStringValue());
            assertTrue(string.getTtlMs() > 0);
            assertEquals(List.of(List.of("email", "a@x.es")), getValue("new:hash").getRows());
            assertEquals(List.of(List.of("ana", "3.5")), getValue("new:zset").getRows());
            assertEquals("Ána \"la\" grande", getValue("user:1:name").getStringValue());
            assertEquals(ExceptionEnum.CODE_BAD_REQUEST, existing.getCode());
            assertEquals(ExceptionEnum.CODE_BAD_REQUEST, hashWithoutField.getCode());
            assertEquals(ExceptionEnum.CODE_BAD_REQUEST, stream.getCode());
        }
        finally {
            for (String key : List.of("new:string", "new:hash", "new:zset")) change(connectionUuid, RedisKeyEnum.OP_DELETE, key, Map.of());
        }
    }

    @Test
    void testValuesByType() {
        GetKeyValueResultDto string = getValue("user:1:name");
        GetKeyValueResultDto hash = getValue("user:1");
        GetKeyValueResultDto list = getValue("queue:jobs");
        GetKeyValueResultDto set = getValue("tags");
        GetKeyValueResultDto zset = getValue("ranking");
        GetKeyValueResultDto stream = getValue("events");
        GetKeyValueResultDto binary = getValue("blob");
        RedisException missing = assertThrows(RedisException.class, () -> getValue("nope"));

        assertEquals("Ána \"la\" grande", string.getStringValue());
        assertEquals(-1L, string.getTtlMs());
        assertEquals(List.of(List.of("email", "ana@x.es"), List.of("plan", "pro")), hash.getRows().stream().sorted((a, b) -> a.getFirst().compareTo(b.getFirst())).toList());
        assertEquals(List.of(List.of("0", "a"), List.of("1", "b"), List.of("2", "c")), list.getRows());
        assertEquals(List.of(List.of("green"), List.of("red")), set.getRows());
        assertEquals(List.of(List.of("bob", "2.5"), List.of("ana", "10")), zset.getRows());
        assertEquals(1, stream.getRows().size());
        assertTrue(stream.getRows().getFirst().get(1).contains("login"));
        assertTrue(binary.isBinary());
        assertEquals("0xcafebabe", binary.getStringValue());
        assertEquals(ExceptionEnum.CODE_NOT_FOUND, missing.getCode());
    }

    @Test
    void testBinaryItemsOfCollectionsShowInHex() {
        command(connectionUuid, "HSET blob:hash bin \"\\xca\\xfe\" txt ok", false);
        command(connectionUuid, "RPUSH blob:list \"\\xff\\x00\" plain", false);

        GetKeyValueResultDto hash = getValue("blob:hash");
        GetKeyValueResultDto list = getValue("blob:list");
        command(connectionUuid, "DEL blob:hash blob:list", false);

        assertEquals(List.of(List.of("bin", "0xcafe"), List.of("txt", "ok")), hash.getRows().stream().sorted((a, b) -> a.getFirst().compareTo(b.getFirst())).toList());
        assertEquals(List.of(List.of("0", "0xff00"), List.of("1", "plain")), list.getRows());
    }

    @Test
    void testChangesByTypeAndProductionConfirmation() {
        change(connectionUuid, RedisKeyEnum.OP_HASH_SET, "edit:hash", Primitives.of("field", "a", "value", "1"));
        change(connectionUuid, RedisKeyEnum.OP_HASH_SET, "edit:hash", Primitives.of("field", "b", "value", "2"));
        change(connectionUuid, RedisKeyEnum.OP_HASH_DELETE, "edit:hash", Primitives.of("field", "a"));
        change(connectionUuid, RedisKeyEnum.OP_LIST_PUSH, "edit:list", Primitives.of("value", "x"));
        change(connectionUuid, RedisKeyEnum.OP_LIST_PUSH, "edit:list", Primitives.of("value", "y"));
        change(connectionUuid, RedisKeyEnum.OP_LIST_SET, "edit:list", Primitives.of("index", "0", "value", "x2"));
        change(connectionUuid, RedisKeyEnum.OP_ZSET_ADD, "edit:zset", Primitives.of("value", "m", "score", "4.5"));
        change(connectionUuid, RedisKeyEnum.OP_STRING_SET, "edit:string", Primitives.of("value", "v1"));
        change(connectionUuid, RedisKeyEnum.OP_EXPIRE, "edit:string", Primitives.of("ttlSeconds", "100"));
        change(connectionUuid, RedisKeyEnum.OP_RENAME, "edit:string", Primitives.of("newKey", "edit:renamed"));
        RedisException production = assertThrows(RedisException.class,
            () -> change(productionUuid, RedisKeyEnum.OP_DELETE, "edit:hash", Primitives.of()));
        RedisException badScore = assertThrows(RedisException.class,
            () -> change(connectionUuid, RedisKeyEnum.OP_ZSET_ADD, "edit:zset", Primitives.of("value", "m", "score", "x")));
        RedisException badTtl = assertThrows(RedisException.class,
            () -> change(connectionUuid, RedisKeyEnum.OP_EXPIRE, "edit:renamed", Primitives.of("ttlSeconds", "abc")));
        RedisException zeroTtl = assertThrows(RedisException.class,
            () -> change(connectionUuid, RedisKeyEnum.OP_EXPIRE, "edit:renamed", Primitives.of("ttlSeconds", "-5")));
        RedisException badIndex = assertThrows(RedisException.class,
            () -> change(connectionUuid, RedisKeyEnum.OP_LIST_SET, "edit:list", Primitives.of("index", "x", "value", "lost")));
        long deleted = change(connectionUuid, RedisKeyEnum.OP_DELETE, "edit:list", Primitives.of());

        assertEquals(List.of(List.of("b", "2")), getValue("edit:hash").getRows());
        assertEquals(List.of(List.of("m", "4.5")), getValue("edit:zset").getRows());
        GetKeyValueResultDto renamed = getValue("edit:renamed");
        assertEquals("v1", renamed.getStringValue());
        assertTrue(renamed.getTtlMs() > 90_000);
        assertEquals(ExceptionEnum.CODE_PRECONDITION_REQUIRED, production.getCode());
        assertEquals(ExceptionEnum.CODE_BAD_REQUEST, badScore.getCode());
        assertEquals(ExceptionEnum.CODE_BAD_REQUEST, badTtl.getCode());
        assertEquals(ExceptionEnum.CODE_BAD_REQUEST, zeroTtl.getCode());
        assertEquals(ExceptionEnum.CODE_BAD_REQUEST, badIndex.getCode());
        assertEquals(1L, deleted);
    }

    @Test
    void testConsoleFormatsLikeRedisCliAndAsksInProduction() throws SQLException {
        command(connectionUuid, "DEL counter", false);
        ExecuteRedisCommandResultDto lrange = command(connectionUuid, "LRANGE queue:jobs 0 -1", false);
        ExecuteRedisCommandResultDto incr = command(connectionUuid, "INCR counter", false);
        ExecuteRedisCommandResultDto missing = command(connectionUuid, "GET nope", false);
        ExecuteRedisCommandResultDto wrongType = command(connectionUuid, "LPUSH user:1 x", false);
        ExecuteRedisCommandResultDto selected = command(connectionUuid, "SELECT 3", false);
        command(connectionUuid, "SELECT " + DATABASE_INDEX, false);
        RedisException production = assertThrows(RedisException.class, () -> command(productionUuid, "FLUSHDB", false));
        RedisException unbalanced = assertThrows(RedisException.class, () -> command(connectionUuid, "SET a \"b", false));

        assertEquals("1) \"a\"\n2) \"b\"\n3) \"c\"", lrange.getOutput());
        assertEquals("(integer) 1", incr.getOutput());
        assertEquals("(nil)", missing.getOutput());
        assertTrue(wrongType.isError());
        assertTrue(wrongType.getOutput().startsWith("(error) WRONGTYPE"));
        assertEquals(3L, selected.getCurrentDatabase());
        assertEquals(7L, incr.getCurrentDatabase());
        assertEquals(ExceptionEnum.CODE_PRECONDITION_REQUIRED, production.getCode());
        assertEquals(ExceptionEnum.CODE_BAD_REQUEST, unbalanced.getCode());
    }

    /**
     * Feature 12 con Redis: por el tunel SSH.
     */
    @Test
    void testSshTunnelToRedis() throws SQLException {
        String testSsh = System.getenv("JAVAYOG_TEST_SSH");
        String redisFromSsh = System.getenv("JAVAYOG_TEST_REDIS_FROM_SSH");
        Assumptions.assumeTrue(testSsh != null && redisFromSsh != null, "SSH tunnel to Redis not configured");
        String[] ssh = testSsh.split(":", 6);
        String[] target = redisFromSsh.split(":", 2);

        String version = TestConnectionService.getInstance().invoke(TestConnectionDto.fromPrimitives(Primitives.of(
            "connectionName", "redis-ssh", "dbEngine", "redis", "host", target[0], "port", target[1], "userPassword", redis[2],
            "databaseName", DATABASE_INDEX, "sshEnabled", true, "sshHost", ssh[0], "sshPort", ssh[1], "sshUser", ssh[2], "sshPassword", ssh[3]
        ))).getServerVersion();

        assertTrue(version.startsWith("Redis 7"));
    }

    /**
     * Feature 13 con Redis (contrato sqlyog-tunnel-multidbs §4): las mismas comprobaciones del navegador y la consola,
     * con la conexion por el tunel PHP (.../redis) en vez de Jedis.
     */
    @Test
    void testHttpTunnelToRedisBehavesLikeJedis() throws SQLException {
        String testHttp = System.getenv("JAVAYOG_TEST_HTTP");
        String redisFromTunnel = System.getenv("JAVAYOG_TEST_REDIS_FROM_TUNNEL");
        Assumptions.assumeTrue(testHttp != null && redisFromTunnel != null, "HTTP tunnel to Redis not configured");
        String[] target = redisFromTunnel.split(":", 2);
        Map<String, Object> tunnelForm = Primitives.of(
            "connectionName", "redis-tunnel", "dbEngine", "redis", "host", target[0], "port", target[1], "userPassword", redis[2],
            "databaseName", DATABASE_INDEX, "httpEnabled", true, "httpUrl", testHttp.split("\\|")[0], "httpTimeoutS", 20
        );
        String version = TestConnectionService.getInstance().invoke(TestConnectionDto.fromPrimitives(tunnelForm)).getServerVersion();
        String directUuid = connectionUuid;
        connectionUuid = String.valueOf(CreateConnectionService.getInstance().invoke(CreateConnectionDto.fromPrimitives(tunnelForm))
            .getConnection().get(ConnectionsKeyEnum.CONNECTION_UUID));
        try {
            testScanReturnsKeysWithTypeAndTtl();
            testValuesByType();
            testNewKeysOfEachTypeNeverOverwrite();
            testChangesByTypeAndProductionConfirmation();
            testConsoleFormatsLikeRedisCliAndAsksInProduction();
            ExecuteRedisCommandResultDto spaced = command(connectionUuid, "SET \"con espacio\" \"a\\tb \\\"c\\\" ñ\"", false);
            assertEquals("\"OK\"", spaced.getOutput());
            assertEquals("a\tb \"c\" ñ", getValue("con espacio").getStringValue());
            command(connectionUuid, "DEL \"con espacio\" edit:hash edit:zset edit:renamed counter", false);
        }
        finally {
            connectionUuid = directUuid;
        }
        assertTrue(version.startsWith("Redis 7") && version.contains("HTTP tunnel"));
    }

    private static Map<String, Object> getForm(String connectionName, String password) {
        return Primitives.of(
            "connectionName", connectionName, "dbEngine", "redis",
            "host", redis[0], "port", redis[1], "userPassword", password, "databaseName", DATABASE_INDEX
        );
    }

    private static String createConnection(String connectionName, boolean isProduction) {
        return String.valueOf(CreateConnectionService.getInstance().invoke(CreateConnectionDto.fromPrimitives(Primitives.merge(
            getForm(connectionName, redis[2]), Primitives.of("isProduction", isProduction)
        ))).getConnection().get(ConnectionsKeyEnum.CONNECTION_UUID));
    }

    private ScanKeysResultDto scanAll(String pattern) {
        return ScanKeysService.getInstance().invoke(ScanKeysDto.fromPrimitives(Primitives.of(
            "connectionUuid", connectionUuid, "sessionKey", BROWSER_SESSION, "pattern", pattern, "count", 1000
        )));
    }

    private GetKeyValueResultDto getValue(String key) {
        return GetKeyValueService.getInstance().invoke(GetKeyValueDto.fromPrimitives(Primitives.of(
            "connectionUuid", connectionUuid, "sessionKey", BROWSER_SESSION, "key", key
        )));
    }

    private void createKey(Map<String, Object> newKey) {
        CreateKeyService.getInstance().invoke(CreateKeyDto.fromPrimitives(Primitives.merge(Primitives.of(
            "connectionUuid", connectionUuid, "sessionKey", BROWSER_SESSION
        ), newKey)));
    }

    private static long change(String connectionUuid, String operation, String key, Map<String, Object> extra) {
        return ChangeKeyService.getInstance().invoke(ChangeKeyDto.fromPrimitives(Primitives.merge(Primitives.of(
            "connectionUuid", connectionUuid, "sessionKey", BROWSER_SESSION, "key", key, "operation", operation
        ), extra))).getAffected();
    }

    private static ExecuteRedisCommandResultDto command(String connectionUuid, String commandLine, boolean isConfirmed) {
        return ExecuteRedisCommandService.getInstance().invoke(ExecuteRedisCommandDto.fromPrimitives(Primitives.of(
            "connectionUuid", connectionUuid, "sessionKey", CONSOLE_SESSION, "commandLine", commandLine, "isConfirmed", isConfirmed
        )));
    }
}
