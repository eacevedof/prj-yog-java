package modules.redis.domain.enums;

/**
 * Valores por defecto de las lecturas de Redis: elementos de una clave y SCAN.
 */
public final class RedisConfigEnum {

    public static final int KEY_VALUE_DEFAULT_LIMIT = 1000;

    public static final String SCAN_DEFAULT_PATTERN = "*";
    public static final String SCAN_FIRST_CURSOR = "0";
    public static final int SCAN_DEFAULT_COUNT = 500;
    // elementos por vuelta al leer un hash, set o zset grande (HSCAN, SSCAN, ZSCAN)
    public static final int ITEMS_SCAN_COUNT = 1000;

    private RedisConfigEnum() {
    }
}
