package modules.mongo.domain.enums;

public final class MongoMessageEnum {

    public static final String FIELD_IS_REQUIRED_X = "{0} is required";
    public static final String COULD_NOT_READ_X_X = "Could not read {0}: {1}";
    public static final String COULD_NOT_SAVE_X_X = "Could not save in {0}: {1}";
    public static final String INVALID_JSON_X_X = "Invalid JSON in {0}: {1}";
    public static final String DOCUMENT_WITHOUT_ID = "The document has no _id: use New to insert it";
    public static final String NEW_AND_DELETE_TOGETHER = "A document cannot be inserted and deleted at once (isNew and isDelete)";
    public static final String DOCUMENT_X_NOT_FOUND = "Document {0} not found (deleted or its _id changed)";
    public static final String NOTHING_TO_EXECUTE = "Nothing to execute";
    public static final String UNKNOWN_COMMAND_X = "Unknown command: {0}";
    public static final String UNKNOWN_METHOD_X = "Unsupported method {0} (find, findOne, countDocuments, distinct, aggregate, insertOne, insertMany, updateOne, updateMany, replaceOne, deleteOne, deleteMany, getIndexes, createIndex, dropIndex, drop)";
    public static final String WRONG_ARGUMENTS_X = "Wrong arguments for {0}";
    public static final String PRODUCTION_X_CONFIRM_X = "Production connection {0}: confirm before running {1}";

    private MongoMessageEnum() {
    }
}
