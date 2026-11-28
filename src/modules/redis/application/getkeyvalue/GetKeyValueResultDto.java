package modules.redis.application.getkeyvalue;

import java.util.List;
import java.util.Map;

import static modules.shared.application.Primitives.getBool;
import static modules.shared.application.Primitives.getTrimmedString;

import modules.redis.domain.enums.RedisKeyEnum;

public final class GetKeyValueResultDto {

    private final String key;
    private final String type;
    private final long ttlMs;
    private final long size;
    private final String stringValue;
    private final boolean isBinary;
    private final List<String> columns;
    private final List<List<String>> rows;

    @SuppressWarnings("unchecked")
    private GetKeyValueResultDto(Map<String, Object> primitives) {
        this.key = String.valueOf(primitives.get(RedisKeyEnum.KEY));
        this.type = getTrimmedString(primitives.get(RedisKeyEnum.TYPE));
        this.ttlMs = primitives.get(RedisKeyEnum.TTL_MS) instanceof Number number ? number.longValue() : -1;
        this.size = primitives.get(RedisKeyEnum.SIZE) instanceof Number number ? number.longValue() : 0;
        this.stringValue = String.valueOf(primitives.getOrDefault("stringValue", ""));
        this.isBinary = getBool(primitives.get(RedisKeyEnum.IS_BINARY));
        this.columns = List.copyOf((List<String>) primitives.getOrDefault(RedisKeyEnum.COLUMNS, List.of()));
        this.rows = List.copyOf((List<List<String>>) primitives.getOrDefault(RedisKeyEnum.ROWS, List.of()));
    }

    public static GetKeyValueResultDto fromPrimitives(Map<String, Object> primitives) {
        return new GetKeyValueResultDto(primitives);
    }

    public String getKey() {
        return key;
    }

    /**
     * string, hash, list, set, zset, stream.
     */
    public String getType() {
        return type;
    }

    /**
     * Milisegundos hasta que caduca; -1 si no caduca.
     */
    public long getTtlMs() {
        return ttlMs;
    }

    /**
     * Bytes de un string; elementos de una coleccion (aunque solo se lean los primeros).
     */
    public long getSize() {
        return size;
    }

    public String getStringValue() {
        return stringValue;
    }

    /**
     * El string no es UTF-8: stringValue va en hexadecimal (0x...) y no se edita.
     */
    public boolean isBinary() {
        return isBinary;
    }

    public List<String> getColumns() {
        return columns;
    }

    public List<List<String>> getRows() {
        return rows;
    }
}
