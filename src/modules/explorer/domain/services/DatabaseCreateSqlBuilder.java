package modules.explorer.domain.services;

import java.util.ArrayList;
import java.util.List;

import modules.shared.domain.services.SqlDialect;
import modules.shared.domain.services.SqlIdentifierQuoter;
import modules.shared.domain.services.SqlLiteralQuoter;

import modules.explorer.domain.entities.DatabaseCreationEntity;

/**
 * El "Create Database": MySQL/MariaDB CREATE DATABASE con juego de caracteres, cotejamiento, cifrado y comentario;
 * PostgreSQL CREATE SCHEMA con propietario (en el arbol la "BD" de PostgreSQL es un esquema); SQL Server CREATE
 * DATABASE con cotejamiento (sin IF NOT EXISTS: se pregunta con DB_ID). SQLite no crea BDs.
 */
public final class DatabaseCreateSqlBuilder {

    private static final String NAME_PATTERN = "[A-Za-z0-9_]*";
    private static final int MAX_NAME_LENGTH = 64;

    private final String dbEngine;
    private final SqlIdentifierQuoter sqlIdentifierQuoter;
    private final SqlLiteralQuoter sqlLiteralQuoter;

    private DatabaseCreateSqlBuilder(String dbEngine) {
        this.dbEngine = dbEngine;
        this.sqlIdentifierQuoter = SqlIdentifierQuoter.getInstance(dbEngine);
        this.sqlLiteralQuoter = SqlLiteralQuoter.getInstance(dbEngine);
    }

    public static DatabaseCreateSqlBuilder getInstance(String dbEngine) {
        return new DatabaseCreateSqlBuilder(dbEngine);
    }

    public boolean isSupported() {
        return SqlDialect.MYSQL.equals(dbEngine) || SqlDialect.PGSQL.equals(dbEngine) || SqlDialect.MSSQL.equals(dbEngine);
    }

    /**
     * Vacio si el motor no crea BDs o falta el nombre.
     */
    public String getCreateSql(DatabaseCreationEntity databaseCreationEntity) {
        if (databaseCreationEntity.databaseName().isEmpty()) return "";
        return switch (dbEngine) {
            case SqlDialect.MYSQL -> getMysqlCreateSql(databaseCreationEntity);
            case SqlDialect.PGSQL -> getPgsqlCreateSql(databaseCreationEntity);
            case SqlDialect.MSSQL -> getMssqlCreateSql(databaseCreationEntity);
            default -> "";
        };
    }

    private String getMysqlCreateSql(DatabaseCreationEntity databaseCreationEntity) {
        List<String> parts = new ArrayList<>();
        parts.add("CREATE DATABASE" + (databaseCreationEntity.isIfNotExists() ? " IF NOT EXISTS" : ""));
        parts.add(sqlIdentifierQuoter.getQuoted(databaseCreationEntity.databaseName()));
        if (!databaseCreationEntity.characterSet().isEmpty()) parts.add("CHARACTER SET = " + databaseCreationEntity.characterSet());
        if (!databaseCreationEntity.collation().isEmpty()) parts.add("COLLATE = " + databaseCreationEntity.collation());
        if (!databaseCreationEntity.encryption().isEmpty()) parts.add("ENCRYPTION = '" + databaseCreationEntity.encryption() + "'");
        if (!databaseCreationEntity.comment().isEmpty()) parts.add("COMMENT = " + sqlLiteralQuoter.getLiteral(databaseCreationEntity.comment()));
        return String.join(" ", parts);
    }

    private String getPgsqlCreateSql(DatabaseCreationEntity databaseCreationEntity) {
        String createSql = "CREATE SCHEMA" + (databaseCreationEntity.isIfNotExists() ? " IF NOT EXISTS " : " ")
            + sqlIdentifierQuoter.getQuoted(databaseCreationEntity.databaseName());
        if (databaseCreationEntity.owner().isEmpty()) return createSql;
        return createSql + " AUTHORIZATION " + sqlIdentifierQuoter.getQuoted(databaseCreationEntity.owner());
    }

    private String getMssqlCreateSql(DatabaseCreationEntity databaseCreationEntity) {
        String createSql = "CREATE DATABASE " + sqlIdentifierQuoter.getQuoted(databaseCreationEntity.databaseName())
            + (databaseCreationEntity.collation().isEmpty() ? "" : " COLLATE " + databaseCreationEntity.collation());
        if (!databaseCreationEntity.isIfNotExists()) return createSql;
        return "IF DB_ID(" + sqlLiteralQuoter.getLiteral(databaseCreationEntity.databaseName()) + ") IS NULL " + createSql;
    }

    /**
     * Lo que impide crear: sin nombre, un nombre de mas de 64 caracteres, o un juego o cotejamiento que no son nombres
     * (van sin entrecomillar).
     */
    public List<String> getErrors(DatabaseCreationEntity databaseCreationEntity) {
        List<String> errors = new ArrayList<>();
        if (databaseCreationEntity.databaseName().isEmpty()) errors.add("databaseName");
        if (databaseCreationEntity.databaseName().length() > MAX_NAME_LENGTH) errors.add(databaseCreationEntity.databaseName());
        if (!databaseCreationEntity.characterSet().matches(NAME_PATTERN)) errors.add(databaseCreationEntity.characterSet());
        if (!databaseCreationEntity.collation().matches(NAME_PATTERN)) errors.add(databaseCreationEntity.collation());
        boolean isEncryptionValid = databaseCreationEntity.encryption().isEmpty()
            || DatabaseCreationEntity.ENCRYPTION_YES.equals(databaseCreationEntity.encryption())
            || DatabaseCreationEntity.ENCRYPTION_NO.equals(databaseCreationEntity.encryption());
        if (!isEncryptionValid) errors.add(databaseCreationEntity.encryption());
        return errors;
    }
}
