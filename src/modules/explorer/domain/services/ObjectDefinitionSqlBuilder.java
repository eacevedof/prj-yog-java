package modules.explorer.domain.services;

import java.util.List;

import modules.shared.domain.services.SqlDialect;
import modules.shared.domain.services.SqlIdentifierQuoter;
import modules.shared.domain.services.SqlLiteralQuoter;

import modules.explorer.domain.enums.ExplorerNodeTypeEnum;

/**
 * El SQL que ensena la definicion de un objeto del arbol en una pestaña nueva: SHOW CREATE ... en MySQL; en
 * PostgreSQL, que no tiene SHOW CREATE, las funciones pg_get_*def del catalogo; en SQLite, la columna sql de
 * sqlite_master. Vacio si el motor no tiene forma de sacarla (una tabla de PostgreSQL: su DDL esta en Info).
 */
public final class ObjectDefinitionSqlBuilder {

    private final SqlDialect sqlDialect;
    private final SqlIdentifierQuoter sqlIdentifierQuoter;
    private final SqlLiteralQuoter sqlLiteralQuoter;

    private ObjectDefinitionSqlBuilder(String dbEngine) {
        this.sqlDialect = SqlDialect.getInstance(dbEngine);
        this.sqlIdentifierQuoter = SqlIdentifierQuoter.getInstance(dbEngine);
        this.sqlLiteralQuoter = SqlLiteralQuoter.getInstance(dbEngine);
    }

    public static ObjectDefinitionSqlBuilder getInstance(String dbEngine) {
        return new ObjectDefinitionSqlBuilder(dbEngine);
    }

    /**
     * "SHOW CREATE ..." en MySQL, que es lo que se ve en el menu contextual; vacio en el resto de motores (la vista pone
     * su propio texto).
     */
    public String getShowCreateLabel(String nodeType) {
        return sqlDialect.isMysql() ? "SHOW CREATE " + getMysqlKeyword(nodeType) : "";
    }

    public String getDefinitionSql(String nodeType, String databaseName, String objectName) {
        if (sqlDialect.isPgsql()) return getPgsqlSql(nodeType, databaseName, objectName);
        if (sqlDialect.isSqlite()) return getSqliteSql(nodeType, objectName);
        if (sqlDialect.isMssql()) return getMssqlSql(nodeType, databaseName, objectName);
        if (ExplorerNodeTypeEnum.DATABASE.equals(nodeType)) {
            return "SHOW CREATE DATABASE " + sqlIdentifierQuoter.getQuoted(databaseName) + ";";
        }
        return "SHOW CREATE " + getMysqlKeyword(nodeType) + " " + sqlIdentifierQuoter.getQuoted(databaseName, objectName) + ";";
    }

    private String getPgsqlSql(String nodeType, String schemaName, String objectName) {
        String schemaLiteral = sqlLiteralQuoter.getLiteral(schemaName);
        String objectLiteral = sqlLiteralQuoter.getLiteral(objectName);
        return switch (nodeType) {
            case ExplorerNodeTypeEnum.DATABASE -> """
                SELECT 'CREATE SCHEMA ' || quote_ident(nspname) || ' AUTHORIZATION ' || quote_ident(pg_catalog.pg_get_userbyid(nspowner)) || ';' AS definition
                FROM pg_catalog.pg_namespace
                WHERE nspname = %s;""".formatted(schemaLiteral);
            case ExplorerNodeTypeEnum.VIEW -> """
                SELECT 'CREATE OR REPLACE VIEW ' || quote_ident(n.nspname) || '.' || quote_ident(c.relname) || ' AS' || chr(10)
                    || pg_catalog.pg_get_viewdef(c.oid, true) AS definition
                FROM pg_catalog.pg_class c
                JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
                WHERE n.nspname = %s AND c.relname = %s;""".formatted(schemaLiteral, objectLiteral);
            case ExplorerNodeTypeEnum.PROCEDURE, ExplorerNodeTypeEnum.FUNCTION -> """
                SELECT pg_catalog.pg_get_functiondef(p.oid) AS definition
                FROM pg_catalog.pg_proc p
                JOIN pg_catalog.pg_namespace n ON n.oid = p.pronamespace
                WHERE n.nspname = %s AND p.proname = %s;""".formatted(schemaLiteral, objectLiteral);
            case ExplorerNodeTypeEnum.TRIGGER -> """
                SELECT pg_catalog.pg_get_triggerdef(t.oid, true) || ';' AS definition
                FROM pg_catalog.pg_trigger t
                JOIN pg_catalog.pg_class c ON c.oid = t.tgrelid
                JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
                WHERE n.nspname = %s AND t.tgname = %s AND NOT t.tgisinternal;""".formatted(schemaLiteral, objectLiteral);
            default -> "";
        };
    }

    /**
     * SQL Server: el texto de sys.sql_modules de esa BD (vistas, rutinas y triggers). "esquema.objeto" o, sin punto, dbo.
     */
    private String getMssqlSql(String nodeType, String databaseName, String objectName) {
        if (ExplorerNodeTypeEnum.DATABASE.equals(nodeType)) {
            return "SELECT 'CREATE DATABASE ' + QUOTENAME(name) + ' COLLATE ' + collation_name + ';' AS definition FROM sys.databases WHERE name = "
                + sqlLiteralQuoter.getLiteral(databaseName) + ";";
        }
        if (!List.of(ExplorerNodeTypeEnum.VIEW, ExplorerNodeTypeEnum.PROCEDURE, ExplorerNodeTypeEnum.FUNCTION, ExplorerNodeTypeEnum.TRIGGER).contains(nodeType)) {
            return "";
        }
        int dotIndex = objectName.indexOf('.');
        String schemaName = dotIndex == -1 ? SqlDialect.MSSQL_DEFAULT_SCHEMA : objectName.substring(0, dotIndex);
        String catalog = sqlIdentifierQuoter.getQuoted(databaseName);
        return """
            SELECT m.definition AS definition
            FROM %1$s.sys.sql_modules m
            JOIN %1$s.sys.objects o ON o.object_id = m.object_id
            JOIN %1$s.sys.schemas s ON s.schema_id = o.schema_id
            WHERE s.name = %2$s AND o.name = %3$s;""".formatted(
            catalog, sqlLiteralQuoter.getLiteral(schemaName), sqlLiteralQuoter.getLiteral(objectName.substring(dotIndex + 1))
        );
    }

    private String getSqliteSql(String nodeType, String objectName) {
        if (ExplorerNodeTypeEnum.DATABASE.equals(nodeType)) return "";
        return "SELECT sql AS definition FROM sqlite_master WHERE name = " + sqlLiteralQuoter.getLiteral(objectName) + ";";
    }

    private String getMysqlKeyword(String nodeType) {
        return switch (nodeType) {
            case ExplorerNodeTypeEnum.DATABASE -> "DATABASE";
            case ExplorerNodeTypeEnum.VIEW -> "VIEW";
            case ExplorerNodeTypeEnum.PROCEDURE -> "PROCEDURE";
            case ExplorerNodeTypeEnum.FUNCTION -> "FUNCTION";
            case ExplorerNodeTypeEnum.TRIGGER -> "TRIGGER";
            default -> "TABLE";
        };
    }
}
