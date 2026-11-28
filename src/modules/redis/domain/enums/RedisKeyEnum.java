package modules.redis.domain.enums;

import java.util.List;

/**
 * Las claves de lo que mueven los servicios de Redis, los tipos de Redis y las operaciones sobre una clave.
 */
public final class RedisKeyEnum {

    public static final String KEY = "key";
    public static final String TYPE = "type";
    public static final String TTL_MS = "ttl_ms";
    public static final String SIZE = "size";

    public static final String TYPE_STRING = "string";
    public static final String TYPE_HASH = "hash";
    public static final String TYPE_LIST = "list";
    public static final String TYPE_SET = "set";
    public static final String TYPE_ZSET = "zset";
    public static final String TYPE_STREAM = "stream";
    public static final String TYPE_NONE = "none";

    // operaciones de ChangeKeyService
    public static final String OP_DELETE = "delete";
    public static final String OP_RENAME = "rename";
    public static final String OP_EXPIRE = "expire";
    public static final String OP_PERSIST = "persist";
    public static final String OP_STRING_SET = "string-set";
    public static final String OP_HASH_SET = "hash-set";
    public static final String OP_HASH_DELETE = "hash-delete";
    public static final String OP_LIST_PUSH = "list-push";
    public static final String OP_LIST_SET = "list-set";
    public static final String OP_LIST_REMOVE = "list-remove";
    public static final String OP_SET_ADD = "set-add";
    public static final String OP_SET_REMOVE = "set-remove";
    public static final String OP_ZSET_ADD = "zset-add";
    public static final String OP_ZSET_REMOVE = "zset-remove";

    public static final List<String> OPERATIONS = List.of(
        OP_DELETE, OP_RENAME, OP_EXPIRE, OP_PERSIST, OP_STRING_SET, OP_HASH_SET, OP_HASH_DELETE, OP_LIST_PUSH, OP_LIST_SET,
        OP_LIST_REMOVE, OP_SET_ADD, OP_SET_REMOVE, OP_ZSET_ADD, OP_ZSET_REMOVE
    );

    /**
     * Las que borran o pisan algo que no se puede recuperar: en produccion se confirman.
     */
    public static final List<String> DESTRUCTIVE_OPERATIONS = List.of(OP_DELETE, OP_RENAME);

    /**
     * Los que se crean con "Nueva clave" (un stream se crea con XADD desde la consola).
     */
    public static final List<String> CREATABLE_TYPES = List.of(TYPE_STRING, TYPE_HASH, TYPE_LIST, TYPE_SET, TYPE_ZSET);

    private RedisKeyEnum() {
    }
}
