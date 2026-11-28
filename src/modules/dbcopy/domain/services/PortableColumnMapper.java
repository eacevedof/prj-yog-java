package modules.dbcopy.domain.services;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import modules.shared.domain.services.SqlDialect;
import modules.shared.application.Primitives;

import modules.dbcopy.domain.enums.DbCopyTypeFamilyEnum;
import modules.dbcopy.domain.entities.PortableColumnEntity;

/**
 * Copia entre motores: traduce el tipo de una columna de un motor SQL a otro pasando por un tipo comun
 * (DbCopyTypeFamilyEnum). Sin perder datos: un entero sin signo se ensancha (int unsigned -> bigint), un texto sin tope
 * se queda sin tope y lo que no tiene equivalente viaja como texto. Lo que va en una clave o un indice se acota donde el
 * motor no indexa textos ni binarios sin longitud (MySQL, SQL Server).
 */
public final class PortableColumnMapper {

    private static final Pattern ARGUMENTS_PATTERN = Pattern.compile("\\(\\s*(\\d+)\\s*(?:,\\s*(\\d+)\\s*)?\\)");
    private static final Set<String> INTEGER_1 = Set.of("tinyint");
    private static final Set<String> INTEGER_2 = Set.of("smallint", "int2", "smallserial", "serial2", "year");
    private static final Set<String> INTEGER_3 = Set.of("mediumint");
    private static final Set<String> INTEGER_4 = Set.of("int", "integer", "int4", "serial", "serial4");
    private static final Set<String> INTEGER_8 = Set.of("bigint", "int8", "bigserial", "serial8");
    private static final Set<String> DECIMAL_TYPES = Set.of("decimal", "numeric", "dec", "fixed", "money", "smallmoney", "number");
    private static final Set<String> FLOAT_4 = Set.of("float4", "real");
    private static final Set<String> FLOAT_8 = Set.of("double", "float8", "double precision");
    private static final Set<String> BOOLEAN_TYPES = Set.of("bool", "boolean");
    private static final Set<String> TEXT_TYPES = Set.of(
        "char", "varchar", "nchar", "nvarchar", "bpchar", "character", "character varying", "nvarchar2", "varchar2", "string"
    );
    private static final Set<String> JSON_TYPES = Set.of("json", "jsonb");
    private static final Set<String> UUID_TYPES = Set.of("uuid", "uniqueidentifier");
    private static final Set<String> TIME_TYPES = Set.of("time", "timetz", "time with time zone", "time without time zone");
    private static final Set<String> DATETIME_TYPES = Set.of(
        "datetime", "timestamp", "timestamptz", "datetime2", "smalldatetime", "datetimeoffset",
        "timestamp with time zone", "timestamp without time zone"
    );
    private static final Set<String> BINARY_TYPES = Set.of(
        "binary", "varbinary", "tinyblob", "blob", "mediumblob", "longblob", "bytea", "image", "rowversion",
        "geometry", "point", "linestring", "polygon", "multipoint", "multilinestring", "multipolygon", "geometrycollection", "geomcollection"
    );
    // MySQL: hasta aqui un texto o binario indexado cabe en un indice InnoDB (3072 bytes con 4 por caracter)
    private static final int MYSQL_MAX_INDEXED_CHARS = 768;
    private static final int MYSQL_INDEXED_DEFAULT = 255;
    private static final int MYSQL_MAX_VARCHAR = 255;
    private static final int MYSQL_MAX_TEXT_CHARS = 16383;
    private static final int MSSQL_MAX_NVARCHAR = 4000;
    private static final int MSSQL_MAX_VARBINARY = 8000;
    private static final int MSSQL_INDEXED_NVARCHAR = 450;
    private static final int MSSQL_INDEXED_VARBINARY = 900;
    // money y smallmoney de SQL Server: decimal(19,4)
    private static final int MONEY_PRECISION = 19;
    private static final int MONEY_SCALE = 4;
    // bit(64) de MySQL: el unico que no cabe en un bigint con signo
    private static final int MYSQL_BIT_MAX_LENGTH = 64;
    // decimales de segundo: el tope de MySQL y PostgreSQL, el de SQL Server (datetime2, time) y el de su datetime
    private static final int MAX_FRACTION = 6;
    private static final int MSSQL_MAX_FRACTION = 7;
    private static final int MSSQL_DATETIME_FRACTION = 3;

    private PortableColumnMapper() {
    }

    public static PortableColumnMapper getInstance() {
        return new PortableColumnMapper();
    }

    /**
     * La columna tal como la da el arbol (column_type, data_type, extra de AbstractExplorerReaderRepository) reducida al
     * tipo comun.
     */
    public PortableColumnEntity getPortableColumn(
        String sourceEngine, String name, String columnType, String dataType, boolean isNullable, String extra
    ) {
        SqlDialect sourceDialect = SqlDialect.getInstance(sourceEngine);
        String lowerColumnType = columnType == null ? "" : columnType.toLowerCase(Locale.ROOT).trim();
        String baseType = getBaseType(dataType, lowerColumnType);
        Matcher argumentsMatcher = ARGUMENTS_PATTERN.matcher(lowerColumnType);
        boolean hasArguments = argumentsMatcher.find();
        int firstArgument = hasArguments ? Integer.parseInt(argumentsMatcher.group(1)) : 0;
        int secondArgument = hasArguments && argumentsMatcher.group(2) != null ? Integer.parseInt(argumentsMatcher.group(2)) : 0;
        boolean isMaxLength = lowerColumnType.contains("(max)");
        boolean isAutoIncrement = extra != null && extra.toLowerCase(Locale.ROOT).contains("auto_increment");
        boolean isUnsigned = lowerColumnType.contains("unsigned") || (sourceDialect.isMssql() && "tinyint".equals(baseType));

        if (sourceDialect.isMysql() && "bit".equals(baseType)) {
            if (firstArgument <= 1) return getColumn(name, DbCopyTypeFamilyEnum.BOOLEAN, 0, 0, false, false, isNullable, false, true);
            return getColumn(name, DbCopyTypeFamilyEnum.INTEGER, 8, 0, firstArgument == MYSQL_BIT_MAX_LENGTH, false, isNullable, false, true);
        }
        if (sourceDialect.isMysql() && "year".equals(baseType)) {
            return getColumn(name, DbCopyTypeFamilyEnum.INTEGER, 2, 0, false, false, isNullable, false, true);
        }
        if ("bit".equals(baseType)) return getColumn(name, DbCopyTypeFamilyEnum.BOOLEAN, 0, 0, false, false, isNullable, false, false);
        if (sourceDialect.isMssql() && ("timestamp".equals(baseType) || "rowversion".equals(baseType))) {
            return getColumn(name, DbCopyTypeFamilyEnum.BINARY, 8, 0, false, true, isNullable, false, false);
        }

        int integerSize = getIntegerSize(baseType);
        // SQLite: todo entero es de 8 bytes (INT, INTEGER, TINYINT... comparten afinidad)
        if (sourceDialect.isSqlite() && baseType.contains("int")) integerSize = 8;
        if (integerSize > 0) {
            return getColumn(name, DbCopyTypeFamilyEnum.INTEGER, integerSize, 0, isUnsigned, false, isNullable, isAutoIncrement, false);
        }
        if (DECIMAL_TYPES.contains(baseType)) {
            if (baseType.contains("money")) return getColumn(name, DbCopyTypeFamilyEnum.DECIMAL, MONEY_PRECISION, MONEY_SCALE, false, false, isNullable, false, false);
            return getColumn(name, DbCopyTypeFamilyEnum.DECIMAL, firstArgument, secondArgument, false, false, isNullable, false, false);
        }
        int floatSize = getFloatSize(sourceDialect, baseType, firstArgument);
        if (floatSize > 0) return getColumn(name, DbCopyTypeFamilyEnum.FLOAT, floatSize, 0, false, false, isNullable, false, false);
        if (BOOLEAN_TYPES.contains(baseType)) return getColumn(name, DbCopyTypeFamilyEnum.BOOLEAN, 0, 0, false, false, isNullable, false, false);
        if (JSON_TYPES.contains(baseType)) return getColumn(name, DbCopyTypeFamilyEnum.JSON, 0, 0, false, false, isNullable, false, false);
        if (UUID_TYPES.contains(baseType)) return getColumn(name, DbCopyTypeFamilyEnum.UUID, 0, 0, false, false, isNullable, false, false);
        if ("date".equals(baseType)) return getColumn(name, DbCopyTypeFamilyEnum.DATE, 0, 0, false, false, isNullable, false, false);
        if (TIME_TYPES.contains(baseType)) {
            int fraction = hasArguments ? firstArgument : getDefaultFraction(sourceDialect, baseType);
            return getColumn(name, DbCopyTypeFamilyEnum.TIME, fraction, 0, false, false, isNullable, false, false);
        }
        if (DATETIME_TYPES.contains(baseType)) {
            int fraction = hasArguments ? firstArgument : getDefaultFraction(sourceDialect, baseType);
            return getColumn(name, DbCopyTypeFamilyEnum.DATETIME, fraction, 0, false, false, isNullable, false, false);
        }
        if (BINARY_TYPES.contains(baseType) || (sourceDialect.isSqlite() && baseType.contains("blob"))) {
            boolean isSized = hasArguments && !isMaxLength && (baseType.equals("binary") || baseType.equals("varbinary"));
            return getColumn(name, DbCopyTypeFamilyEnum.BINARY, isSized ? firstArgument : 0, 0, false, "binary".equals(baseType), isNullable, false, false);
        }
        if (TEXT_TYPES.contains(baseType)) {
            boolean isSized = hasArguments && !isMaxLength;
            boolean isFixed = Set.of("char", "nchar", "bpchar", "character").contains(baseType);
            return getColumn(name, DbCopyTypeFamilyEnum.TEXT, isSized ? firstArgument : 0, 0, false, isFixed && isSized, isNullable, false, false);
        }
        if ("tinytext".equals(baseType)) return getColumn(name, DbCopyTypeFamilyEnum.TEXT, MYSQL_MAX_VARCHAR, 0, false, false, isNullable, false, false);
        if (sourceDialect.isSqlite() && (baseType.contains("real") || baseType.contains("floa") || baseType.contains("doub"))) {
            return getColumn(name, DbCopyTypeFamilyEnum.FLOAT, 8, 0, false, false, isNullable, false, false);
        }
        if (sourceDialect.isMysql() && ("enum".equals(baseType) || "set".equals(baseType))) {
            return getColumn(name, DbCopyTypeFamilyEnum.TEXT, MYSQL_MAX_VARCHAR, 0, false, false, isNullable, false, false);
        }
        return getColumn(name, DbCopyTypeFamilyEnum.TEXT, 0, 0, false, false, isNullable, false, false);
    }

    /**
     * El tipo en el motor destino. isIndexed: la columna va en la clave primaria o en un indice. isIdentity: es la
     * columna autoincremental de la clave (un entero sin signo de 8 bytes se queda en bigint para poder serlo).
     */
    public String getTargetType(String targetEngine, PortableColumnEntity column, boolean isIndexed, boolean isIdentity) {
        SqlDialect targetDialect = SqlDialect.getInstance(targetEngine);
        return switch (column.getFamily()) {
            case DbCopyTypeFamilyEnum.INTEGER -> getIntegerType(targetDialect, column, isIdentity);
            case DbCopyTypeFamilyEnum.DECIMAL -> getDecimalType(targetDialect, column.getSize(), column.getScale());
            case DbCopyTypeFamilyEnum.FLOAT -> getFloatType(targetDialect, column.getSize());
            case DbCopyTypeFamilyEnum.BOOLEAN -> getByDialect(targetDialect, "tinyint(1)", "boolean", "bit", "BOOLEAN");
            case DbCopyTypeFamilyEnum.JSON -> getByDialect(targetDialect, "json", "jsonb", "nvarchar(max)", "TEXT");
            case DbCopyTypeFamilyEnum.UUID -> getByDialect(targetDialect, "char(36)", "uuid", "uniqueidentifier", "TEXT");
            case DbCopyTypeFamilyEnum.DATE -> getByDialect(targetDialect, "date", "date", "date", "DATE");
            case DbCopyTypeFamilyEnum.TIME -> getByDialect(
                targetDialect, "time" + getFraction(column.getSize(), MAX_FRACTION), "time" + getFraction(column.getSize(), MAX_FRACTION), "time" + getFraction(column.getSize(), MSSQL_MAX_FRACTION), "TIME"
            );
            case DbCopyTypeFamilyEnum.DATETIME -> getByDialect(
                targetDialect, "datetime" + getFraction(column.getSize(), MAX_FRACTION), "timestamp" + getFraction(column.getSize(), MAX_FRACTION),
                "datetime2" + getFraction(column.getSize(), MSSQL_MAX_FRACTION), "DATETIME"
            );
            case DbCopyTypeFamilyEnum.BINARY -> getBinaryType(targetDialect, column, isIndexed);
            default -> getTextType(targetDialect, column, isIndexed);
        };
    }

    private String getIntegerType(SqlDialect targetDialect, PortableColumnEntity column, boolean isIdentity) {
        if (targetDialect.isSqlite()) return "INTEGER";
        int size = column.getSize();
        boolean isUnsigned = column.isUnsigned();
        if (targetDialect.isMysql()) {
            String mysqlType = switch (size) {
                case 1 -> "tinyint";
                case 2 -> "smallint";
                case 3 -> "mediumint";
                case 4 -> "int";
                default -> "bigint";
            };
            return isUnsigned ? mysqlType + " unsigned" : mysqlType;
        }
        // sin UNSIGNED: el valor mas alto de un sin signo cabe en el entero del doble de ancho
        int signedSize = isUnsigned ? (size == 3 ? 4 : size * 2) : size;
        // un IDENTITY tiene que ser entero: un bigint unsigned autoincremental se queda en bigint
        if (signedSize > 8) return isIdentity ? "bigint" : targetDialect.isMssql() ? "decimal(20,0)" : "numeric(20,0)";
        if (targetDialect.isMssql() && size == 1 && column.isUnsigned()) return "tinyint";
        if (signedSize <= 2) return "smallint";
        if (signedSize <= 4) return targetDialect.isMssql() ? "int" : "integer";
        return "bigint";
    }

    private String getDecimalType(SqlDialect targetDialect, int precision, int scale) {
        if (targetDialect.isSqlite()) return "NUMERIC";
        if (targetDialect.isPgsql()) return precision == 0 ? "numeric" : "numeric(" + precision + "," + scale + ")";
        if (targetDialect.isMssql()) {
            if (precision == 0) return "decimal(38,10)";
            int mssqlPrecision = Math.min(precision, 38);
            return "decimal(" + mssqlPrecision + "," + Math.min(scale, mssqlPrecision) + ")";
        }
        if (precision == 0) return "decimal(65,30)";
        return "decimal(" + Math.min(precision, 65) + "," + Math.min(scale, 30) + ")";
    }

    private String getFloatType(SqlDialect targetDialect, int size) {
        boolean isSingle = size == 4;
        return getByDialect(targetDialect, isSingle ? "float" : "double", isSingle ? "real" : "double precision", isSingle ? "real" : "float", "REAL");
    }

    private String getTextType(SqlDialect targetDialect, PortableColumnEntity column, boolean isIndexed) {
        int length = column.getSize();
        if (targetDialect.isSqlite()) return "TEXT";
        if (targetDialect.isPgsql()) {
            if (length == 0) return "text";
            return (column.isFixedLength() ? "char(" : "varchar(") + length + ")";
        }
        if (targetDialect.isMssql()) {
            if (length > 0 && length <= MSSQL_MAX_NVARCHAR) return (column.isFixedLength() ? "nchar(" : "nvarchar(") + length + ")";
            return isIndexed ? "nvarchar(" + MSSQL_INDEXED_NVARCHAR + ")" : "nvarchar(max)";
        }
        if (isIndexed) {
            if (length == 0) return "varchar(" + MYSQL_INDEXED_DEFAULT + ")";
            if (column.isFixedLength() && length <= MYSQL_MAX_VARCHAR) return "char(" + length + ")";
            return "varchar(" + Math.min(length, MYSQL_MAX_INDEXED_CHARS) + ")";
        }
        if (length > 0 && length <= MYSQL_MAX_VARCHAR) return (column.isFixedLength() ? "char(" : "varchar(") + length + ")";
        // un varchar largo por columna llena enseguida los 65.535 bytes de fila de MySQL: mejor un TEXT
        if (length > 0 && length <= MYSQL_MAX_TEXT_CHARS) return "text";
        return "longtext";
    }

    private String getBinaryType(SqlDialect targetDialect, PortableColumnEntity column, boolean isIndexed) {
        int length = column.getSize();
        if (targetDialect.isSqlite()) return "BLOB";
        if (targetDialect.isPgsql()) return "bytea";
        if (targetDialect.isMssql()) {
            if (length > 0 && length <= MSSQL_MAX_VARBINARY) return (column.isFixedLength() ? "binary(" : "varbinary(") + length + ")";
            return isIndexed ? "varbinary(" + MSSQL_INDEXED_VARBINARY + ")" : "varbinary(max)";
        }
        if (length > 0 && length <= MYSQL_MAX_VARCHAR) return (column.isFixedLength() ? "binary(" : "varbinary(") + length + ")";
        return isIndexed ? "varbinary(" + MYSQL_INDEXED_DEFAULT + ")" : "longblob";
    }

    private String getByDialect(SqlDialect targetDialect, String mysqlType, String pgsqlType, String mssqlType, String sqliteType) {
        if (targetDialect.isPgsql()) return pgsqlType;
        if (targetDialect.isMssql()) return mssqlType;
        if (targetDialect.isSqlite()) return sqliteType;
        return mysqlType;
    }

    /**
     * "(3)" con los decimales de segundo, sin pasar del maximo del motor; "" si no lleva.
     */
    private String getFraction(int fraction, int maxFraction) {
        return fraction <= 0 ? "" : "(" + Math.min(fraction, maxFraction) + ")";
    }

    /**
     * El tipo base en minusculas: data_type si lo da el motor; si no, column_type sin argumentos ni modificadores.
     */
    private String getBaseType(String dataType, String lowerColumnType) {
        String baseType = dataType == null ? "" : dataType.toLowerCase(Locale.ROOT).trim();
        if (baseType.isEmpty()) baseType = lowerColumnType.replaceAll("\\(.*$", "").replace("unsigned", "").trim();
        if (lowerColumnType.startsWith("timestamp") && lowerColumnType.contains("with time zone")) return "timestamptz";
        if (lowerColumnType.startsWith("double precision")) return "double precision";
        return baseType;
    }

    private int getIntegerSize(String baseType) {
        if (INTEGER_1.contains(baseType)) return 1;
        if (INTEGER_2.contains(baseType)) return 2;
        if (INTEGER_3.contains(baseType)) return 3;
        if (INTEGER_4.contains(baseType)) return 4;
        if (INTEGER_8.contains(baseType)) return 8;
        return 0;
    }

    /**
     * 4 (simple) u 8 (doble); 0 si no es coma flotante. En SQL Server "float" es doble salvo float(1..24); en MySQL
     * "float" es simple.
     */
    private int getFloatSize(SqlDialect sourceDialect, String baseType, int firstArgument) {
        if (FLOAT_4.contains(baseType)) return 4;
        if (FLOAT_8.contains(baseType)) return 8;
        if (!"float".equals(baseType)) return 0;
        if (sourceDialect.isMssql()) return firstArgument > 0 && firstArgument <= 24 ? 4 : 8;
        return firstArgument > 24 ? 8 : 4;
    }

    /**
     * Los decimales de segundo cuando el tipo no los dice: MySQL 0, PostgreSQL 6, SQL Server datetime 3 y datetime2/time 7.
     */
    private int getDefaultFraction(SqlDialect sourceDialect, String baseType) {
        if (sourceDialect.isPgsql() || sourceDialect.isSqlite()) return MAX_FRACTION;
        if (sourceDialect.isMssql()) {
            if ("datetime".equals(baseType)) return MSSQL_DATETIME_FRACTION;
            if ("smalldatetime".equals(baseType)) return 0;
            return MSSQL_MAX_FRACTION;
        }
        return 0;
    }

    private PortableColumnEntity getColumn(
        String name, String family, int size, int scale, boolean isUnsigned, boolean isFixedLength, boolean isNullable,
        boolean isAutoIncrement, boolean isCastToNumber
    ) {
        return PortableColumnEntity.fromPrimitives(Primitives.of(
            "name", name,
            "family", family,
            "size", size,
            "scale", scale,
            "isUnsigned", isUnsigned,
            "isFixedLength", isFixedLength,
            "isNullable", isNullable,
            "isAutoIncrement", isAutoIncrement,
            "isCastToNumber", isCastToNumber
        ));
    }
}
