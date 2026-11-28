package modules.redis.domain.enums;

public final class RedisMessageEnum {

    public static final String FIELD_IS_REQUIRED_X = "{0} is required";
    public static final String COULD_NOT_READ_X_X = "Could not read {0}: {1}";
    public static final String COULD_NOT_CHANGE_X_X = "Could not change {0}: {1}";
    public static final String UNKNOWN_OPERATION_X = "Unknown operation {0}";
    public static final String OPERATION_X_NOT_FOR_TYPE_X = "Operation {0} does not apply to a {1} key";
    public static final String KEY_X_NOT_FOUND = "Key {0} not found";
    public static final String NOTHING_TO_EXECUTE = "Nothing to execute";
    public static final String PRODUCTION_X_CONFIRM_X = "Production connection {0}: confirm before running {1}";
    public static final String INVALID_SCORE_X = "Invalid score {0}";
    public static final String INVALID_TTL_X = "Invalid expiration {0}: whole seconds greater than 0, or empty";
    public static final String INVALID_EXPIRE_TTL_X = "Invalid expiration {0}: whole seconds greater than 0";
    public static final String INVALID_LIST_INDEX_X = "Invalid list index {0}: a whole number";
    public static final String KEY_X_ALREADY_EXISTS = "Key {0} already exists: edit it in the value panel";
    public static final String TYPE_X_NOT_CREATABLE = "A {0} key cannot be created here: string, hash, list, set or zset";

    private RedisMessageEnum() {
    }
}
