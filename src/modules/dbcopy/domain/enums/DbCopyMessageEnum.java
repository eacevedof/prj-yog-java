package modules.dbcopy.domain.enums;

public final class DbCopyMessageEnum {

    public static final String FIELD_IS_REQUIRED_X = "{0} is required";
    public static final String NOTHING_SELECTED = "Nothing is selected to copy";
    public static final String NOT_SQL_ENGINE_X_X = "Copying only works between SQL engines (MySQL/MariaDB, PostgreSQL, SQL Server, SQLite): {0} is {1}";
    public static final String ONLY_TABLES_BETWEEN_ENGINES = "between different engines only tables are copied (views, routines, triggers and events are not translated)";
    public static final String SQLITE_ONLY_MAIN_X = "SQLite has a single database (main): {0} cannot be created";
    public static final String WRONG_VALUE_X_X_X = "value {0} is not valid for column {1} ({2})";
    public static final String INDEX_NOT_CREATED_X_X = "index {0} not created: {1}";
    public static final String SAME_SOURCE_AND_TARGET = "Source and target are the same database on the same server: the copy would delete it";
    public static final String TARGET_IS_PRODUCTION_X_X = "Target \"{0}\" is a production connection: database {1} will be written";
    public static final String NO_DEFINITION_X = "the server gives no definition (missing privileges?): {0}";
    public static final String CANCELLED_TABLE_INCOMPLETE = "cancelled: the table was copied partially";
    public static final String DUMP_ONLY_MYSQL_X_X = "The SQL dump only works with MySQL/MariaDB: {0} is {1}";
    public static final String NOTHING_SELECTED_TO_DUMP = "Nothing is selected to export";
    public static final String WRONG_DUMP_MODE_X = "Unknown dump mode {0}";
    public static final String CANNOT_WRITE_X_X = "Cannot write {0}: {1}";
    public static final String WRONG_DATABASE_NAME_X = "Invalid database name {0}: only letters, digits, _, $ and -, up to 64 characters";
    public static final String SAME_DATABASE_NAME_X = "The new name is the same as the current one: {0}";
    public static final String DATABASE_X_NOT_FOUND = "Database {0} not found";
    public static final String DATABASE_X_ALREADY_EXISTS = "Database {0} already exists";
    public static final String PRODUCTION_X_RENAME_X_X = "{0} is a production connection: database {1} will be copied to {2}";
    public static final String PRODUCTION_X_RENAME_DROP_X_X = "{0} is a production connection: database {1} will be copied to {2} and then DROPPED";
    public static final String RENAME_DUMP_FAILED_X = "The dump of the original database failed ({0} object(s) with errors): nothing was created";
    public static final String RENAME_LOAD_FAILED_X_X = "Loading the dump into {0} failed and it was dropped: {1}";
    public static final String RENAME_MISMATCH_X_X = "{0} does not match the original after loading ({1}) and it was dropped: the original is untouched";

    private DbCopyMessageEnum() {
    }
}
