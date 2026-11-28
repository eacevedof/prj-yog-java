package modules.explorer.domain;

import org.junit.jupiter.api.Test;

import testcase.AbstractTestCase;

import modules.explorer.domain.entities.DatabaseOptionsEntity;
import modules.explorer.domain.services.DatabaseAlterSqlBuilder;

/**
 * El ALTER DATABASE solo lleva lo que cambia; quitar el solo lectura va primero y ponerlo, al final; lo que el servidor
 * no tiene nunca se escribe.
 */
final class DatabaseAlterSqlBuilderTest extends AbstractTestCase {

    private final DatabaseAlterSqlBuilder databaseAlterSqlBuilder = DatabaseAlterSqlBuilder.getInstance();

    @Test
    void testOnlyWhatChangesInTheRightOrder() {
        DatabaseOptionsEntity mysqlReadOnly = new DatabaseOptionsEntity("db_shop", "utf8mb4", "utf8mb4_0900_ai_ci", true, false, true, true, false, "");

        String nothing = databaseAlterSqlBuilder.getAlterSql(mysqlReadOnly, mysqlReadOnly.withEdited("utf8mb4", "utf8mb4_0900_ai_ci", false, true, "x"));
        String writableLatin1 = databaseAlterSqlBuilder.getAlterSql(mysqlReadOnly, mysqlReadOnly.withEdited("latin1", "latin1_swedish_ci", true, false, ""));
        String onlyCollation = databaseAlterSqlBuilder.getAlterSql(mysqlReadOnly, mysqlReadOnly.withEdited("utf8mb4", "utf8mb4_bin", false, true, ""));

        assertEquals("", nothing);
        assertEquals("ALTER DATABASE `db_shop` READ ONLY = 0 CHARACTER SET = latin1 COLLATE = latin1_swedish_ci ENCRYPTION = 'Y'", writableLatin1);
        assertEquals("ALTER DATABASE `db_shop` CHARACTER SET = utf8mb4 COLLATE = utf8mb4_bin", onlyCollation);
    }

    @Test
    void testMariaDbCommentIsQuotedAndWrongNamesAreErrors() {
        DatabaseOptionsEntity mariaDb = new DatabaseOptionsEntity("db_shop", "utf8mb4", "utf8mb4_general_ci", false, false, false, false, true, "");

        String withComment = databaseAlterSqlBuilder.getAlterSql(mariaDb, mariaDb.withEdited("utf8mb4", "utf8mb4_general_ci", true, true, "it's the shop"));

        assertEquals("ALTER DATABASE `db_shop` COMMENT = 'it\\'s the shop'", withComment);
        assertEquals(1, databaseAlterSqlBuilder.getErrors(mariaDb.withEdited("utf8mb4; DROP", "utf8mb4_general_ci", false, false, "")).size());
    }
}
