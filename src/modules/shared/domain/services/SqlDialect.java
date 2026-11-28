package modules.shared.domain.services;

import java.util.List;

/**
 * Lo que cambia entre los motores SQL (MySQL/MariaDB, PostgreSQL, SQLite y SQL Server) en el SQL que genera o trocea
 * multidbs. El motor es el db_engine de la conexion ("mysql", "pgsql", "sqlite", "mssql"); uno desconocido se trata como
 * MySQL.
 * <p>
 * En PostgreSQL, lo que el arbol llama "BD" es un esquema de la BD conectada: la "BD activa" es el search_path.
 * SQLite tiene una sola BD ("main"). En SQL Server el arbol lista las BDs del servidor, como en MySQL, y un objeto de un
 * esquema que no es dbo se llama "esquema.objeto".
 */
public final class SqlDialect {

    public static final String MYSQL = "mysql";
    public static final String PGSQL = "pgsql";
    public static final String SQLITE = "sqlite";
    public static final String MSSQL = "mssql";
    // el motor "Ficheros" trabaja sobre un SQLite con los CSV y JSON cargados: para el SQL, es SQLite
    public static final String FILES = "files";
    public static final List<String> SQL_ENGINES = List.of(MYSQL, PGSQL, SQLITE, MSSQL);
    public static final String MSSQL_DEFAULT_SCHEMA = "dbo";
    public static final String SQLITE_MAIN = "main";

    private final String dbEngine;

    private SqlDialect(String dbEngine) {
        if (FILES.equals(dbEngine)) {
            this.dbEngine = SQLITE;
            return;
        }
        this.dbEngine = SQL_ENGINES.contains(dbEngine) ? dbEngine : MYSQL;
    }

    public static SqlDialect getInstance(String dbEngine) {
        return new SqlDialect(dbEngine);
    }

    public String getDbEngine() {
        return dbEngine;
    }

    public boolean isMysql() {
        return MYSQL.equals(dbEngine);
    }

    public boolean isPgsql() {
        return PGSQL.equals(dbEngine);
    }

    public boolean isSqlite() {
        return SQLITE.equals(dbEngine);
    }

    public boolean isMssql() {
        return MSSQL.equals(dbEngine);
    }

    /**
     * ` en MySQL; [ en SQL Server; " en PostgreSQL y SQLite (estandar SQL).
     */
    public char getIdentifierQuote() {
        if (isMysql()) return '`';
        return isMssql() ? '[' : '"';
    }

    /**
     * La comilla que cierra: ] en SQL Server, la misma que abre en el resto.
     */
    public char getIdentifierCloseQuote() {
        return isMssql() ? ']' : getIdentifierQuote();
    }

    /**
     * SQL Server no tiene LIMIT: SELECT TOP n.
     */
    public boolean isTopLimit() {
        return isMssql();
    }

    /**
     * SQL Server: los lotes se separan con una linea "GO" (como en SSMS y sqlcmd) y el ; no corta, porque un CREATE
     * PROCEDURE va solo en su lote y las variables (@x) solo viven dentro de el.
     */
    public boolean isGoBatchSeparator() {
        return isMssql();
    }

    /**
     * Solo MySQL acepta UPDATE/DELETE ... LIMIT 1 (en SQLite depende de como se compilo).
     */
    public boolean isLimitInUpdateSupported() {
        return isMysql();
    }

    /**
     * Un INSERT sin columnas (todas a su DEFAULT).
     */
    public String getEmptyInsertSuffix() {
        return isMysql() ? " () VALUES ()" : " DEFAULT VALUES";
    }

    /**
     * # es comentario solo en MySQL (en PostgreSQL es un operador).
     */
    public boolean isHashComment() {
        return isMysql();
    }

    /**
     * La linea "DELIMITER $$" es del cliente mysql.
     */
    public boolean isDelimiterCommand() {
        return isMysql();
    }

    /**
     * $$ ... $$ y $tag$ ... $tag$: cuerpos de funciones de PostgreSQL.
     */
    public boolean isDollarQuoting() {
        return isPgsql();
    }

    /**
     * CREATE TRIGGER ... BEGIN ...; ...; END de SQLite: los ; de dentro no cortan.
     */
    public boolean isTriggerBlock() {
        return isSqlite();
    }

    /**
     * La "BD activa" de la sesion: la BD en MySQL y SQL Server, el primer esquema del search_path en PostgreSQL, "main"
     * en SQLite.
     */
    public String getCurrentDatabaseSql() {
        if (isPgsql()) return "SELECT current_schema()";
        if (isSqlite()) return "SELECT '" + SQLITE_MAIN + "'";
        if (isMssql()) return "SELECT DB_NAME()";
        return "SELECT DATABASE()";
    }

    /**
     * La version que ensena la prueba de conexion.
     */
    public String getServerVersionSql() {
        if (isSqlite()) return "SELECT 'SQLite ' || sqlite_version()";
        if (isMssql()) {
            return "SELECT 'SQL Server ' + CAST(SERVERPROPERTY('ProductVersion') AS varchar(50)) + ' ' + CAST(SERVERPROPERTY('Edition') AS varchar(100))";
        }
        return "SELECT VERSION()";
    }
}
