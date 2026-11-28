package modules.explorer.domain.services;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import modules.shared.domain.services.SqlDialect;
import modules.shared.domain.services.SqlIdentifierQuoter;
import modules.shared.domain.services.SqlLiteralQuoter;

import modules.explorer.domain.entities.DatabaseOptionsEntity;

/**
 * El "Alter Database" (MySQL/MariaDB): un solo ALTER DATABASE con lo que cambia. READ ONLY = 0 va el primero (una BD
 * de solo lectura no deja cambiar nada mas si no se quita en la misma sentencia) y READ ONLY = 1 el ultimo. Juego de
 * caracteres y cotejamiento van juntos. Solo cambia los valores por defecto de las tablas nuevas: las que ya existen se
 * quedan como estan.
 */
public final class DatabaseAlterSqlBuilder {

    private final SqlIdentifierQuoter sqlIdentifierQuoter = SqlIdentifierQuoter.getInstance(SqlDialect.MYSQL);
    private final SqlLiteralQuoter sqlLiteralQuoter = SqlLiteralQuoter.getInstance();

    private DatabaseAlterSqlBuilder() {
    }

    public static DatabaseAlterSqlBuilder getInstance() {
        return new DatabaseAlterSqlBuilder();
    }

    /**
     * Vacio si no cambia nada.
     */
    public String getAlterSql(DatabaseOptionsEntity original, DatabaseOptionsEntity edited) {
        List<String> options = new ArrayList<>();
        boolean isReadOnlyChanged = original.isReadOnlySupported() && original.isReadOnly() != edited.isReadOnly();
        if (isReadOnlyChanged && !edited.isReadOnly()) options.add("READ ONLY = 0");
        boolean isCharsetChanged = !Objects.equals(original.characterSet(), edited.characterSet())
            || !Objects.equals(original.collation(), edited.collation());
        if (isCharsetChanged && !edited.characterSet().isEmpty()) options.add("CHARACTER SET = " + edited.characterSet());
        if (isCharsetChanged && !edited.collation().isEmpty()) options.add("COLLATE = " + edited.collation());
        if (original.isEncryptionSupported() && original.isEncrypted() != edited.isEncrypted()) {
            options.add("ENCRYPTION = " + (edited.isEncrypted() ? "'Y'" : "'N'"));
        }
        if (original.isCommentSupported() && !Objects.equals(original.comment(), edited.comment())) {
            options.add("COMMENT = " + sqlLiteralQuoter.getLiteral(edited.comment()));
        }
        if (isReadOnlyChanged && edited.isReadOnly()) options.add("READ ONLY = 1");
        if (options.isEmpty()) return "";
        return "ALTER DATABASE " + sqlIdentifierQuoter.getQuoted(original.databaseName()) + " " + String.join(" ", options);
    }

    /**
     * Lo que impide guardar: un juego o un cotejamiento que no son nombres (no se entrecomillan en el ALTER).
     */
    public List<String> getErrors(DatabaseOptionsEntity edited) {
        List<String> errors = new ArrayList<>();
        if (!edited.characterSet().matches("[A-Za-z0-9_]*")) errors.add(edited.characterSet());
        if (!edited.collation().matches("[A-Za-z0-9_]*")) errors.add(edited.collation());
        return errors;
    }
}
