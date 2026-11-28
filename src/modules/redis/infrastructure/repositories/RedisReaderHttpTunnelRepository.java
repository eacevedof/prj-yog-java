package modules.redis.infrastructure.repositories;

import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import modules.shared.application.Primitives;

import modules.connections.infrastructure.components.httptunnel.RedisHttpTunnelSession;

import modules.redis.domain.enums.RedisConfigEnum;
import modules.redis.domain.enums.RedisKeyEnum;
import modules.redis.domain.services.RedisReplyFormatter;

/**
 * El navegador de Redis por el tunel HTTP: lo mismo que RedisReaderRepository, con comandos de redis-cli. Lo que Jedis
 * hace con un pipeline (TYPE y PTTL de cada clave) va en un lote: una peticion.
 */
public final class RedisReaderHttpTunnelRepository extends AbstractRedisReaderRepository {

    private final RedisHttpTunnelCommands redisHttpTunnelCommands;
    private final RedisReplyFormatter redisReplyFormatter;

    private RedisReaderHttpTunnelRepository(RedisHttpTunnelSession redisHttpTunnelSession) {
        this.redisReplyFormatter = RedisReplyFormatter.getInstance();
        this.redisHttpTunnelCommands = RedisHttpTunnelCommands.getInstance(redisHttpTunnelSession);
    }

    public static RedisReaderHttpTunnelRepository getInstance(RedisHttpTunnelSession redisHttpTunnelSession) {
        return new RedisReaderHttpTunnelRepository(redisHttpTunnelSession);
    }

    /**
     * SCAN da dos filas: el cursor y el array de claves en JSON.
     */
    @Override
    public Map<String, Object> scanKeys(String pattern, String cursor, int count) {
        List<String> scanRows = redisHttpTunnelCommands.getTexts(
            redisHttpTunnelCommands.getReply("SCAN", cursor, "MATCH", pattern, "COUNT", String.valueOf(count))
        );
        String nextCursor = scanRows.isEmpty() ? "0" : scanRows.getFirst();
        List<String> keyNames = scanRows.size() < 2 ? List.of() : redisHttpTunnelCommands.getJsonTexts(scanRows.get(1));
        List<List<String>> commands = new ArrayList<>();
        for (String keyName : keyNames) {
            commands.add(List.of("TYPE", keyName));
            commands.add(List.of("PTTL", keyName));
        }
        List<Object> replies = commands.isEmpty() ? List.of() : redisHttpTunnelCommands.getReplies(commands);
        List<Map<String, Object>> keys = new ArrayList<>();
        for (int i = 0; i < keyNames.size(); i++) {
            keys.add(Primitives.of(
                RedisKeyEnum.KEY, keyNames.get(i),
                RedisKeyEnum.TYPE, redisHttpTunnelCommands.getText(replies.get(i * 2)),
                RedisKeyEnum.TTL_MS, redisHttpTunnelCommands.getLong(replies.get(i * 2 + 1))
            ));
        }
        return Primitives.of(RedisKeyEnum.KEYS, keys, RedisKeyEnum.NEXT_CURSOR, nextCursor);
    }

    @Override
    public boolean hasKey(String key) {
        return redisHttpTunnelCommands.getLong(redisHttpTunnelCommands.getReply("EXISTS", key)) > 0;
    }

    @Override
    public Map<String, Object> getKeyValue(String key, int limit) {
        List<Object> typeAndTtl = redisHttpTunnelCommands.getReplies(List.of(List.of("TYPE", key), List.of("PTTL", key)));
        String type = redisHttpTunnelCommands.getText(typeAndTtl.getFirst());
        Map<String, Object> keyValue = new LinkedHashMap<>();
        keyValue.put(RedisKeyEnum.KEY, key);
        keyValue.put(RedisKeyEnum.TYPE, type);
        keyValue.put(RedisKeyEnum.TTL_MS, redisHttpTunnelCommands.getLong(typeAndTtl.get(1)));
        List<String> columns = List.of();
        List<List<String>> rows = new ArrayList<>();
        long size = 0;
        switch (type == null ? "" : type) {
            case RedisKeyEnum.TYPE_STRING -> {
                Object reply = redisHttpTunnelCommands.getReply("GET", key);
                byte[] bytes = reply instanceof byte[] raw ? raw : new byte[0];
                String text = redisReplyFormatter.getUtf8OrNull(bytes);
                keyValue.put(RedisKeyEnum.IS_BINARY, text == null);
                keyValue.put("stringValue", text == null ? "0x" + HexFormat.of().formatHex(bytes) : text);
                size = bytes.length;
            }
            case RedisKeyEnum.TYPE_HASH -> {
                columns = List.of("field", "value");
                size = redisHttpTunnelCommands.getLong(redisHttpTunnelCommands.getReply("HLEN", key));
                for (List<String> pair : getPairs(scanCollection("HSCAN", key, limit * 2))) {
                    if (rows.size() >= limit) break;
                    rows.add(pair);
                }
            }
            case RedisKeyEnum.TYPE_LIST -> {
                columns = List.of("index", "value");
                size = redisHttpTunnelCommands.getLong(redisHttpTunnelCommands.getReply("LLEN", key));
                List<String> items = redisHttpTunnelCommands.getTexts(redisHttpTunnelCommands.getReply("LRANGE", key, "0", String.valueOf(limit - 1)));
                for (int i = 0; i < items.size(); i++) rows.add(List.of(String.valueOf(i), items.get(i)));
            }
            case RedisKeyEnum.TYPE_SET -> {
                columns = List.of("member");
                size = redisHttpTunnelCommands.getLong(redisHttpTunnelCommands.getReply("SCARD", key));
                for (String member : scanCollection("SSCAN", key, limit)) {
                    if (rows.size() >= limit) break;
                    rows.add(List.of(member));
                }
                rows.sort((left, right) -> left.getFirst().compareTo(right.getFirst()));
            }
            case RedisKeyEnum.TYPE_ZSET -> {
                columns = List.of("member", "score");
                size = redisHttpTunnelCommands.getLong(redisHttpTunnelCommands.getReply("ZCARD", key));
                List<String> flat = redisHttpTunnelCommands.getTexts(redisHttpTunnelCommands.getReply("ZRANGE", key, "0", String.valueOf(limit - 1), "WITHSCORES"));
                for (List<String> pair : getPairs(flat)) rows.add(List.of(pair.getFirst(), getScoreText(pair.get(1))));
            }
            case RedisKeyEnum.TYPE_STREAM -> {
                columns = List.of("id", "fields");
                size = redisHttpTunnelCommands.getLong(redisHttpTunnelCommands.getReply("XLEN", key));
                for (String entryJson : redisHttpTunnelCommands.getTexts(redisHttpTunnelCommands.getReply("XRANGE", key, "-", "+", "COUNT", String.valueOf(limit)))) {
                    List<String> entry = redisHttpTunnelCommands.getJsonTexts(entryJson);
                    if (entry.size() < 2) continue;
                    rows.add(List.of(entry.getFirst(), getFieldsText(redisHttpTunnelCommands.getJsonTexts(entry.get(1)))));
                }
            }
            default -> {
            }
        }
        keyValue.put(RedisKeyEnum.SIZE, size);
        keyValue.put(RedisKeyEnum.COLUMNS, columns);
        keyValue.put(RedisKeyEnum.ROWS, rows);
        keyValue.putIfAbsent(RedisKeyEnum.IS_BINARY, false);
        keyValue.putIfAbsent("stringValue", "");
        return keyValue;
    }

    /**
     * HSCAN/SSCAN hasta tener maxItems elementos o acabar: cada vuelta da el cursor y el array (en JSON).
     */
    private List<String> scanCollection(String command, String key, int maxItems) {
        List<String> items = new ArrayList<>();
        String cursor = "0";
        do {
            List<String> page = redisHttpTunnelCommands.getTexts(
                redisHttpTunnelCommands.getReply(command, key, cursor, "COUNT", String.valueOf(Math.min(maxItems, RedisConfigEnum.ITEMS_SCAN_COUNT)))
            );
            if (page.isEmpty()) break;
            cursor = page.getFirst();
            if (page.size() > 1) items.addAll(redisHttpTunnelCommands.getJsonTexts(page.get(1)));
        } while (!"0".equals(cursor) && items.size() < maxItems);
        return items;
    }

    private List<List<String>> getPairs(List<String> flat) {
        List<List<String>> pairs = new ArrayList<>();
        for (int i = 0; i + 1 < flat.size(); i += 2) pairs.add(List.of(flat.get(i), flat.get(i + 1)));
        return pairs;
    }

    /**
     * Como el toString del Map de Jedis: {campo=valor, ...}.
     */
    private String getFieldsText(List<String> flatFields) {
        List<String> fields = new ArrayList<>();
        for (List<String> pair : getPairs(flatFields)) fields.add(pair.getFirst() + "=" + pair.get(1));
        return "{" + String.join(", ", fields) + "}";
    }

    /**
     * 1.0 → "1", 2.5 → "2.5": como RedisReaderRepository.
     */
    private String getScoreText(String score) {
        if (score == null || !score.matches("-?\\d+(\\.\\d+)?([eE][-+]?\\d+)?")) return score;
        double number = Double.parseDouble(score);
        return number == Math.rint(number) && !Double.isInfinite(number) ? String.valueOf((long) number) : String.valueOf(number);
    }
}
