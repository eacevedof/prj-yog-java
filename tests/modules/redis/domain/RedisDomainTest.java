package modules.redis.domain;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

import testcase.AbstractTestCase;

import modules.redis.domain.services.RedisCommandLineParser;
import modules.redis.domain.services.RedisDangerousCommandDetector;
import modules.redis.domain.services.RedisReplyFormatter;

/**
 * La consola de Redis sin servidor: como se parte una linea, que se considera peligroso y como se ensena la respuesta.
 */
final class RedisDomainTest extends AbstractTestCase {

    private final RedisCommandLineParser redisCommandLineParser = RedisCommandLineParser.getInstance();

    @Test
    void testParserHandlesQuotesEscapesAndBytes() {
        assertEquals(List.of("SET", "k", "hola mundo", "it's"), redisCommandLineParser.getArguments("SET k \"hola mundo\" 'it\\'s'"));
        assertEquals(List.of("SET", "k", "a\nb\"c"), redisCommandLineParser.getArguments("  SET   k  \"a\\nb\\\"c\"  "));
        assertEquals(List.of("GET", "Ána"), redisCommandLineParser.getArguments("GET Ána"));
        byte[] binary = redisCommandLineParser.getRawArguments("SET k \"\\xca\\xfe\"").get(2);
        assertEquals(2, binary.length);
        assertEquals((byte) 0xCA, binary[0]);
        assertEquals("Ána", new String(redisCommandLineParser.getRawArguments("\"Ána\"").getFirst(), StandardCharsets.UTF_8));
        assertThrows(IllegalArgumentException.class, () -> redisCommandLineParser.getArguments("SET k \"sin cerrar"));
    }

    @Test
    void testDangerousCommandsIncludeSubcommands() {
        RedisDangerousCommandDetector detector = RedisDangerousCommandDetector.getInstance();

        assertTrue(detector.isDangerous(List.of("flushdb")));
        assertTrue(detector.isDangerous(List.of("DEL", "a")));
        assertTrue(detector.isDangerous(List.of("config", "set", "maxmemory", "1")));
        assertFalse(detector.isDangerous(List.of("CONFIG", "GET", "maxmemory")));
        assertFalse(detector.isDangerous(List.of("GET", "k")));
        assertFalse(detector.isDangerous(List.of()));
    }

    @Test
    void testReplyFormatterLooksLikeRedisCli() {
        RedisReplyFormatter formatter = RedisReplyFormatter.getInstance();
        byte[] ok = "OK".getBytes(StandardCharsets.UTF_8);

        assertEquals("(nil)", formatter.getText(null));
        assertEquals("(integer) 42", formatter.getText(42L));
        assertEquals("\"OK\"", formatter.getText(ok));
        assertEquals("\"a\\nb \\\"c\\\"\"", formatter.getText("a\nb \"c\"".getBytes(StandardCharsets.UTF_8)));
        assertEquals("\"\\xca\\xfe\"", formatter.getText(new byte[]{(byte) 0xCA, (byte) 0xFE}));
        assertEquals("(empty array)", formatter.getText(List.of()));
        assertEquals("1) \"a\"\n2) 1) (integer) 1\n   2) (nil)", formatter.getText(List.of("a".getBytes(StandardCharsets.UTF_8), java.util.Arrays.asList(1L, null))));
    }
}
