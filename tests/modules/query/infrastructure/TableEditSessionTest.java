package modules.query.infrastructure;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import testcase.AbstractTestCase;

import modules.query.domain.enums.TableChangeKeyEnum;
import modules.query.domain.entities.PreparedSqlEntity;
import modules.query.domain.services.TableChangesSqlBuilder;
import modules.query.infrastructure.components.TableEditSession;

/**
 * La rejilla editable (feature 6) sin JavaFX: el registro de cambios y el SQL que sale de ellos.
 */
final class TableEditSessionTest extends AbstractTestCase {

    private static final List<String> COLUMNS = List.of("id", "email", "name");

    @Test
    void testEditingMarksTheRowAndRevertingUnmarksIt() {
        List<String> row = mutableRow("1", "a@x.es", "Ana");
        TableEditSession tableEditSession = getSession(List.of(row));

        tableEditSession.setValue(row, 2, "Anabel");
        assertEquals(TableEditSession.STATE_MODIFIED, tableEditSession.getState(row));

        tableEditSession.setValue(row, 2, "Ana");
        assertEquals(null, tableEditSession.getState(row));
        assertFalse(tableEditSession.hasChanges());
    }

    @Test
    @SuppressWarnings("unchecked")
    void testChangesUseTheOriginalKeyAndOnlyTheModifiedColumns() {
        List<String> row = mutableRow("1", "a@x.es", "Ana");
        TableEditSession tableEditSession = getSession(List.of(row));

        tableEditSession.setValue(row, 0, "10");
        tableEditSession.setValue(row, 2, null);
        Map<String, Object> change = tableEditSession.getChanges().getFirst();

        assertEquals(TableChangeKeyEnum.TYPE_UPDATE, change.get(TableChangeKeyEnum.CHANGE_TYPE));
        assertEquals(Map.of("id", "1"), change.get(TableChangeKeyEnum.KEY_VALUES));
        Map<String, String> newValues = (Map<String, String>) change.get(TableChangeKeyEnum.NEW_VALUES);
        assertEquals("10", newValues.get("id"));
        assertTrue(newValues.containsKey("name") && newValues.get("name") == null);
        assertFalse(newValues.containsKey("email"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testDuplicateLeavesAutoIncrementOutAndNewRowsCanBeDropped() {
        List<String> row = mutableRow("1", "a@x.es", "Ana");
        TableEditSession tableEditSession = getSession(List.of(row));

        List<String> duplicatedRow = tableEditSession.duplicateRow(row);
        List<String> emptyRow = tableEditSession.newRow();
        boolean isEmptyRowGone = tableEditSession.deleteRow(emptyRow);
        Map<String, Object> insert = tableEditSession.getChanges().getFirst();

        assertEquals(Arrays.asList(null, "a@x.es", "Ana"), duplicatedRow);
        assertTrue(isEmptyRowGone);
        assertEquals(1, tableEditSession.getChangeCount());
        assertEquals(Map.of("email", "a@x.es", "name", "Ana"), insert.get(TableChangeKeyEnum.NEW_VALUES));
    }

    @Test
    void testDeletingALoadedRowRestoresItsValuesAndGoesFirst() {
        List<String> row1 = mutableRow("1", "a@x.es", "Ana");
        List<String> row2 = mutableRow("2", "b@x.es", "Bob");
        TableEditSession tableEditSession = getSession(List.of(row1, row2));

        tableEditSession.newRow();
        tableEditSession.setValue(row2, 2, "Roberto");
        tableEditSession.deleteRow(row2);
        tableEditSession.setValue(row1, 2, "Anabel");
        List<String> changeTypes = tableEditSession.getChanges().stream().map(change -> String.valueOf(change.get(TableChangeKeyEnum.CHANGE_TYPE))).toList();

        assertEquals("Bob", row2.get(2));
        assertEquals(List.of(TableChangeKeyEnum.TYPE_DELETE, TableChangeKeyEnum.TYPE_UPDATE, TableChangeKeyEnum.TYPE_INSERT), changeTypes);
    }

    @Test
    void testSqlBuilderUsesParametersAndTheWholePrimaryKey() {
        List<String> row = mutableRow("1", "a@x.es", "Ana");
        TableEditSession tableEditSession = getSession(List.of(row));
        tableEditSession.setValue(row, 2, "O'Neil");
        tableEditSession.duplicateRow(row);

        List<PreparedSqlEntity> statements = TableChangesSqlBuilder.getInstance().getStatements("shop", "users", Set.of("id"), tableEditSession.getChanges());

        assertEquals("UPDATE `shop`.`users` SET `name` = ? WHERE `id` = ? LIMIT 1", statements.getFirst().getSql());
        assertEquals(Arrays.asList("O'Neil", "1"), statements.getFirst().getParams());
        assertTrue(statements.getFirst().expectsOneRow());
        assertEquals("INSERT INTO `shop`.`users` (`email`, `name`) VALUES (?, ?)", statements.get(1).getSql());
        assertFalse(statements.get(1).expectsOneRow());
    }

    @Test
    void testSqlBuilderRefusesChangesWithoutTheFullKey() {
        List<Map<String, Object>> changes = List.of(Map.of(
            TableChangeKeyEnum.CHANGE_TYPE, TableChangeKeyEnum.TYPE_DELETE,
            TableChangeKeyEnum.KEY_VALUES, Map.of("id", "1")
        ));

        assertThrows(IllegalArgumentException.class, () -> TableChangesSqlBuilder.getInstance().getStatements(
            "shop", "order_lines", new LinkedHashSet<>(List.of("order_id", "line")), changes
        ));
    }

    @Test
    void testSetDefaultGoesAsDefaultInUpdateAndInsertAndTypingClearsIt() {
        List<String> row = mutableRow("1", "a@x.es", "Ana");
        TableEditSession tableEditSession = getSession(List.of(row));

        tableEditSession.setDefault(row, 2);
        assertTrue(tableEditSession.isDefault(row, 2));
        assertEquals(TableEditSession.STATE_MODIFIED, tableEditSession.getState(row));
        List<String> newRow = tableEditSession.newRow();
        tableEditSession.setValue(newRow, 1, "b@x.es");
        tableEditSession.setDefault(newRow, 2);
        List<PreparedSqlEntity> statements = TableChangesSqlBuilder.getInstance().getStatements(
            "shop", "users", Set.of("id"), tableEditSession.getChanges()
        );

        assertEquals("UPDATE `shop`.`users` SET `name` = DEFAULT WHERE `id` = ? LIMIT 1", statements.getFirst().getSql());
        assertEquals(List.of("1"), statements.getFirst().getParams());
        assertEquals("INSERT INTO `shop`.`users` (`email`, `name`) VALUES (?, DEFAULT)", statements.get(1).getSql());
        assertEquals(List.of("b@x.es"), statements.get(1).getParams());

        tableEditSession.setValue(row, 2, "Ana");
        assertFalse(tableEditSession.isDefault(row, 2));
        assertEquals(null, tableEditSession.getState(row));
    }

    private TableEditSession getSession(List<List<String>> rows) {
        TableEditSession tableEditSession = new TableEditSession(COLUMNS, Set.of("id"), Set.of("id"));
        tableEditSession.trackLoadedRows(rows);
        return tableEditSession;
    }

    private List<String> mutableRow(String... values) {
        return new ArrayList<>(Arrays.asList(values));
    }
}
