package modules.explorer.domain.enums;

public final class ExplorerMessageEnum {

    public static final String FIELD_IS_REQUIRED_X = "{0} is required";
    public static final String DATABASE_NAME_MISMATCH_X_X = "The edited database {1} is not the original {0}";
    public static final String INVALID_TABLE_NAME_X = "Invalid table name {0}: only letters, digits and _ (no spaces), not starting with a digit";
    public static final String INVALID_COLUMN_NAME_X = "Invalid column name {0}: only letters, digits and _ (no spaces), not starting with a digit";
    public static final String INVALID_INDEX_NAME_X = "Invalid index or key name {0}: only letters, digits and _ (no spaces)";
    public static final String TABLE_WITHOUT_COLUMNS = "The table needs at least one column";
    public static final String DUPLICATED_COLUMN_X = "Column {0} is repeated";
    public static final String DUPLICATED_INDEX_X = "Index {0} is repeated";
    public static final String COLUMN_WITHOUT_TYPE_X = "Column {0} has no data type";
    public static final String INDEX_WITHOUT_COLUMNS_X = "Index {0} has no columns";
    public static final String UNKNOWN_COLUMN_X_IN_X = "Unknown column {0} in {1}";
    public static final String WRONG_FOREIGN_KEY_X = "Foreign key {0} needs the same number of columns on both sides and a referenced table";
    public static final String ONLY_MYSQL_TABLE_EDITOR_X = "Editing tables only works with MySQL/MariaDB for now: the connection is {0}";
    public static final String NOTHING_TO_ALTER = "There is nothing to change";
    public static final String PRODUCTION_X_ALTER_X = "{0} is a production connection: table {1} will be altered";
    public static final String PRODUCTION_X_CREATE_TABLE_X = "{0} is a production connection: table {1} will be created";
    public static final String CREATE_TABLE_NOT_SUPPORTED_X = "Creating tables only works with MySQL/MariaDB, PostgreSQL, SQLite and SQL Server: the connection is {0}";
    public static final String X_NOT_AVAILABLE_IN_X = "{0} is not available in {1}";
    public static final String SQLITE_AUTO_INCREMENT_X = "In SQLite only a single INTEGER primary key can auto-increment: {0}";
    public static final String AUTO_INCREMENT_WITH_DEFAULT_X = "Column {0} cannot auto-increment and have a DEFAULT";
    public static final String PRODUCTION_X_DROP_X = "{0} is a production connection: table {1} will be dropped with all its data";
    public static final String ONLY_MYSQL_DATABASE_EDITOR_X = "Altering databases only works with MySQL/MariaDB for now: the connection is {0}";
    public static final String DATABASE_X_NOT_FOUND = "Database {0} not found";
    public static final String PRODUCTION_X_ALTER_DATABASE_X = "{0} is a production connection: database {1} will be altered";
    public static final String WRONG_CHARSET_OR_COLLATION_X = "Wrong character set or collation: {0}";
    public static final String PRODUCTION_X_RENAME_X_TO_X = "{0} is a production connection: {1} will be renamed to {2}";
    public static final String RENAME_SCHEMA_CHANGE_X = "Renaming cannot move the table to another schema: {0}";
    public static final String CREATE_DATABASE_NOT_SUPPORTED_X = "Creating databases only works with MySQL/MariaDB, PostgreSQL and SQL Server: the connection is {0}";
    public static final String WRONG_DATABASE_OPTIONS_X = "Wrong database name or option: {0}";
    public static final String PRODUCTION_X_CREATE_DATABASE_X = "{0} is a production connection: database {1} will be created";
    public static final String DROP_DATABASE_NOT_SUPPORTED_X = "Dropping databases only works with MySQL/MariaDB, PostgreSQL and SQL Server: the connection is {0}";
    public static final String SYSTEM_DATABASE_X_NOT_DROPPABLE = "{0} is a system database of the server: it cannot be dropped from here";
    public static final String PRODUCTION_X_DROP_DATABASE_X = "{0} is a production connection: database {1} will be dropped with all its tables and data";

    private ExplorerMessageEnum() {
    }
}
