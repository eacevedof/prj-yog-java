package modules.redis.infrastructure.repositories;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.Pipeline;
import redis.clients.jedis.Response;
import redis.clients.jedis.params.ScanParams;
import redis.clients.jedis.resps.ScanResult;
import redis.clients.jedis.resps.StreamEntry;
import redis.clients.jedis.resps.Tuple;

import modules.shared.application.Primitives;

import modules.redis.domain.enums.RedisKeyEnum;
import modules.redis.domain.services.RedisReplyFormatter;

/**
 * Lo que lee el navegador de Redis: claves por SCAN (nunca KEYS, que bloquea el servidor) con su tipo y TTL, y el valor
 * de una clave segun su tipo, con un tope de elementos.
 */
public final class RedisReaderRepository extends AbstractRedisReaderRepository {

    private final Jedis jedis;

    private RedisReaderRepository(Jedis jedis) {
        this.jedis = jedis;
    }

    public static RedisReaderRepository getInstance(Jedis jedis) {
        return new RedisReaderRepository(jedis);
    }

    /**
     * Una vuelta de SCAN: las claves que encajan con el patron (el servidor mira unas "count" por vuelta, puede
     * devolver menos o ninguna) y su tipo y TTL en un pipeline. next_cursor "0" = no hay mas.
     */
    @Override
    public Map<String, Object> scanKeys(String pattern, String cursor, int count) {
        ScanResult<String> scanResult = jedis.scan(cursor, new ScanParams().match(pattern).count(count));
        List<String> keyNames = scanResult.getResult();
        List<Response<String>> types = new ArrayList<>();
        List<Response<Long>> ttls = new ArrayList<>();
        // Jedis 5 sigue "en pipeline" tras sync() hasta que se cierra: sin el close, el siguiente uso de esta
        // conexion falla con "Cannot use Jedis when in Pipeline"
        try (Pipeline pipeline = jedis.pipelined()) {
            for (String keyName : keyNames) {
                types.add(pipeline.type(keyName));
                ttls.add(pipeline.pttl(keyName));
            }
            pipeline.sync();
        }
        List<Map<String, Object>> keys = new ArrayList<>();
        for (int i = 0; i < keyNames.size(); i++) {
            keys.add(Primitives.of(
                RedisKeyEnum.KEY, keyNames.get(i),
                RedisKeyEnum.TYPE, types.get(i).get(),
                RedisKeyEnum.TTL_MS, ttls.get(i).get()
            ));
        }
        return Primitives.of("keys", keys, "nextCursor", scanResult.getCursor());
    }

    @Override
    public boolean hasKey(String key) {
        return jedis.exists(key);
    }

    /**
     * Tipo, TTL (-1 sin caducidad), tamaño y valor: un string entero (en hexadecimal si es binario) o, en las
     * colecciones, sus primeros "limit" elementos como filas. Tipo "none" si la clave no existe.
     */
    @Override
    public Map<String, Object> getKeyValue(String key, int limit) {
        String type = jedis.type(key);
        Map<String, Object> keyValue = new LinkedHashMap<>();
        keyValue.put(RedisKeyEnum.KEY, key);
        keyValue.put(RedisKeyEnum.TYPE, type);
        keyValue.put(RedisKeyEnum.TTL_MS, jedis.pttl(key));
        byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
        List<String> columns = List.of();
        List<List<String>> rows = new ArrayList<>();
        long size = 0;
        switch (type) {
            case RedisKeyEnum.TYPE_STRING -> {
                byte[] bytes = jedis.get(keyBytes);
                String text = bytes == null ? "" : RedisReplyFormatter.getUtf8OrNull(bytes);
                keyValue.put("isBinary", text == null);
                keyValue.put("stringValue", text == null ? getHexText(bytes) : text);
                size = bytes == null ? 0 : bytes.length;
            }
            case RedisKeyEnum.TYPE_HASH -> {
                columns = List.of("field", "value");
                size = jedis.hlen(keyBytes);
                String cursor = ScanParams.SCAN_POINTER_START;
                do {
                    ScanResult<Map.Entry<byte[], byte[]>> page = jedis.hscan(keyBytes, cursor.getBytes(StandardCharsets.UTF_8), new ScanParams().count(Math.min(limit, 1000)));
                    for (Map.Entry<byte[], byte[]> entry : page.getResult()) {
                        if (rows.size() >= limit) break;
                        rows.add(List.of(getDisplayText(entry.getKey()), getDisplayText(entry.getValue())));
                    }
                    cursor = page.getCursor();
                } while (!ScanParams.SCAN_POINTER_START.equals(cursor) && rows.size() < limit);
            }
            case RedisKeyEnum.TYPE_LIST -> {
                columns = List.of("index", "value");
                size = jedis.llen(keyBytes);
                List<byte[]> items = jedis.lrange(keyBytes, 0, limit - 1);
                for (int i = 0; i < items.size(); i++) rows.add(List.of(String.valueOf(i), getDisplayText(items.get(i))));
            }
            case RedisKeyEnum.TYPE_SET -> {
                columns = List.of("member");
                size = jedis.scard(keyBytes);
                String cursor = ScanParams.SCAN_POINTER_START;
                do {
                    ScanResult<byte[]> page = jedis.sscan(keyBytes, cursor.getBytes(StandardCharsets.UTF_8), new ScanParams().count(Math.min(limit, 1000)));
                    for (byte[] member : page.getResult()) {
                        if (rows.size() >= limit) break;
                        rows.add(List.of(getDisplayText(member)));
                    }
                    cursor = page.getCursor();
                } while (!ScanParams.SCAN_POINTER_START.equals(cursor) && rows.size() < limit);
                rows.sort((left, right) -> left.getFirst().compareTo(right.getFirst()));
            }
            case RedisKeyEnum.TYPE_ZSET -> {
                columns = List.of("member", "score");
                size = jedis.zcard(keyBytes);
                for (Tuple tuple : jedis.zrangeWithScores(keyBytes, 0, limit - 1)) {
                    rows.add(List.of(getDisplayText(tuple.getBinaryElement()), getScoreText(tuple.getScore())));
                }
            }
            case RedisKeyEnum.TYPE_STREAM -> {
                columns = List.of("id", "fields");
                size = jedis.xlen(key);
                for (StreamEntry streamEntry : jedis.xrange(key, "-", "+", limit)) {
                    rows.add(List.of(streamEntry.getID().toString(), streamEntry.getFields().toString()));
                }
            }
            default -> {
            }
        }
        keyValue.put(RedisKeyEnum.SIZE, size);
        keyValue.put("columns", columns);
        keyValue.put("rows", rows);
        keyValue.putIfAbsent("isBinary", false);
        keyValue.putIfAbsent("stringValue", "");
        return keyValue;
    }

    /**
     * Un elemento de una coleccion como el string: el texto si es UTF-8 y, si es binario, 0x en hexadecimal (leido
     * como String, Jedis lo decodifica con caracteres de reemplazo y se ve, y se reescribe, cambiado).
     */
    private String getDisplayText(byte[] bytes) {
        String text = RedisReplyFormatter.getUtf8OrNull(bytes);
        return text == null ? getHexText(bytes) : text;
    }

    private String getHexText(byte[] bytes) {
        return "0x" + HexFormat.of().formatHex(bytes);
    }

    /**
     * 1.0 → "1", 2.5 → "2.5": como los ensena redis-cli.
     */
    private String getScoreText(double score) {
        return score == Math.rint(score) && !Double.isInfinite(score) ? String.valueOf((long) score) : String.valueOf(score);
    }
}
