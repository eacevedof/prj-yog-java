package modules.workspace.application;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import modules.shared.application.Primitives;
import static modules.shared.application.Primitives.getBool;
import static modules.shared.application.Primitives.getTrimmedString;

import testcase.AbstractTestCase;

import modules.workspace.domain.enums.WorkspaceConfigEnum;
import modules.workspace.domain.enums.WorkspaceKeyEnum;
import modules.workspace.application.getquerytabs.GetQueryTabsDto;
import modules.workspace.application.getquerytabs.GetQueryTabsResultDto;
import modules.workspace.application.getworkspacestate.GetWorkspaceStateDto;
import modules.workspace.application.resetworkspace.ResetWorkspaceDto;
import modules.workspace.application.resetworkspace.ResetWorkspaceResultDto;
import modules.workspace.application.savequerytabs.SaveQueryTabsDto;
import modules.workspace.application.saveworkspacestate.SaveWorkspaceStateDto;
import modules.workspace.application.getquerytabs.GetQueryTabsService;
import modules.workspace.application.getworkspacestate.GetWorkspaceStateService;
import modules.workspace.application.resetworkspace.ResetWorkspaceService;
import modules.workspace.application.savequerytabs.SaveQueryTabsService;
import modules.workspace.application.saveworkspacestate.SaveWorkspaceStateService;
import modules.workspace.domain.exceptions.WorkspaceException;

/**
 * Lo que sobrevive al reinicio: los filtros del panel y las pestañas de query de cada conexion con su SQL.
 */
final class WorkspaceStateServicesTest extends AbstractTestCase {

    private static final String CONNECTION_UUID = "conn-ws-1";

    @BeforeEach
    void bootDatabase() {
        bootFreshDatabase("WorkspaceStateServicesTest");
    }

    @Test
    void testFiltersAreEmptyUntilSavedAndKeepTheirSpaces() {
        assertEquals("", getState(WorkspaceKeyEnum.DATABASE_FILTER));

        saveState(WorkspaceKeyEnum.DATABASE_FILTER, "myp ");
        saveState(WorkspaceKeyEnum.OBJECT_FILTER, "cust");
        saveState(WorkspaceKeyEnum.DATABASE_FILTER, "db_my ");

        assertEquals("db_my ", getState(WorkspaceKeyEnum.DATABASE_FILTER));
        assertEquals("cust", getState(WorkspaceKeyEnum.OBJECT_FILTER));
    }

    @Test
    void testRejectsUnknownKeysAndTooLongValues() {
        assertThrows(WorkspaceException.class, () -> saveState("panel.font", "x"));
        assertThrows(WorkspaceException.class, () -> saveState(WorkspaceKeyEnum.ACTIVE_DATABASE_PREFIX, "x"));
        assertThrows(WorkspaceException.class, () -> saveState(WorkspaceKeyEnum.OBJECT_FILTER, "x".repeat(WorkspaceConfigEnum.MAX_STATE_VALUE_LENGTH + 1)));
        assertThrows(WorkspaceException.class, () -> getState("panel.font"));
    }

    @Test
    void testQueryTabsAreReplacedInOrderWithTheirSqlAndActiveDatabase() {
        GetQueryTabsResultDto nothingSaved = getQueryTabs(CONNECTION_UUID);
        assertTrue(nothingSaved.getQueryTabs().isEmpty());
        assertEquals("", nothingSaved.getActiveDatabase());

        saveQueryTabs("db_old", List.of(getQueryTab("Query 1", "SELECT 1", false)));
        saveQueryTabs("db_mypromos", List.of(
            getQueryTab("Query 1", "SELECT 1;\n\n  ", false),
            getQueryTab("informes", "SELECT *\nFROM users\nWHERE name = 'O''Brien'", true)
        ));
        saveQueryTabs("other_db", List.of(getQueryTab("Query 1", "SELECT 2", true)), "conn-ws-2");

        GetQueryTabsResultDto restored = getQueryTabs(CONNECTION_UUID);
        assertEquals("db_mypromos", restored.getActiveDatabase());
        assertEquals(2, restored.getQueryTabs().size());
        Map<String, Object> firstTab = restored.getQueryTabs().getFirst();
        Map<String, Object> secondTab = restored.getQueryTabs().get(1);
        assertEquals("Query 1", getTrimmedString(firstTab.get(WorkspaceKeyEnum.TAB_TITLE)));
        assertEquals("SELECT 1;\n\n  ", String.valueOf(firstTab.get(WorkspaceKeyEnum.SQL_TEXT)));
        assertFalse(getBool(firstTab.get(WorkspaceKeyEnum.IS_SELECTED)));
        assertEquals("informes", getTrimmedString(secondTab.get(WorkspaceKeyEnum.TAB_TITLE)));
        assertEquals("SELECT *\nFROM users\nWHERE name = 'O''Brien'", String.valueOf(secondTab.get(WorkspaceKeyEnum.SQL_TEXT)));
        assertTrue(getBool(secondTab.get(WorkspaceKeyEnum.IS_SELECTED)));
        assertEquals(1, getQueryTabs("conn-ws-2").getQueryTabs().size());
    }

    @Test
    void testQueryTabsNeedConnectionAndTitle() {
        assertThrows(WorkspaceException.class, () -> saveQueryTabs("db", List.of(), ""));
        assertThrows(WorkspaceException.class, () -> saveQueryTabs("db", List.of(getQueryTab(" ", "SELECT 1", true))));
        assertThrows(WorkspaceException.class, () -> getQueryTabs(""));
    }

    @Test
    void testResetWorkspaceForgetsFiltersFoldersActiveDatabaseAndQueryTabs() {
        saveState(WorkspaceKeyEnum.DATABASE_FILTER, "myp");
        saveState(WorkspaceKeyEnum.OBJECT_FILTER, "cust");
        saveState(WorkspaceKeyEnum.SQL_FILE_DIRECTORY, "C:\\dumps");
        saveState(WorkspaceKeyEnum.SQL_FILE_LAST_FILE, "C:\\dumps\\last.sql");
        saveQueryTabs("db_mypromos", List.of(getQueryTab("Query 1", "SELECT 1", true), getQueryTab("informes", "SELECT 2", false)));
        saveQueryTabs("other_db", List.of(getQueryTab("Query 1", "SELECT 3", true)), "conn-ws-2");

        ResetWorkspaceResultDto resetResult = ResetWorkspaceService.getInstance().invoke(ResetWorkspaceDto.fromPrimitives(Primitives.of()));

        // 4 estados + la BD activa de las dos conexiones; 3 pestañas
        assertEquals(6, resetResult.getDeletedStates());
        assertEquals(3, resetResult.getDeletedQueryTabs());
        assertEquals("", getState(WorkspaceKeyEnum.DATABASE_FILTER));
        assertEquals("", getState(WorkspaceKeyEnum.OBJECT_FILTER));
        assertEquals("", getState(WorkspaceKeyEnum.SQL_FILE_DIRECTORY));
        assertEquals("", getState(WorkspaceKeyEnum.SQL_FILE_LAST_FILE));
        GetQueryTabsResultDto restored = getQueryTabs(CONNECTION_UUID);
        assertTrue(restored.getQueryTabs().isEmpty());
        assertEquals("", restored.getActiveDatabase());
        assertTrue(getQueryTabs("conn-ws-2").getQueryTabs().isEmpty());

        ResetWorkspaceResultDto secondReset = ResetWorkspaceService.getInstance().invoke(ResetWorkspaceDto.fromPrimitives(Primitives.of()));
        assertEquals(0, secondReset.getDeletedStates());
        assertEquals(0, secondReset.getDeletedQueryTabs());
    }

    private Map<String, Object> getQueryTab(String title, String sql, boolean isSelected) {
        return Primitives.of(
            WorkspaceKeyEnum.TAB_TITLE, title,
            WorkspaceKeyEnum.SQL_TEXT, sql,
            WorkspaceKeyEnum.IS_SELECTED, isSelected
        );
    }

    private void saveQueryTabs(String activeDatabase, List<Map<String, Object>> queryTabs) {
        saveQueryTabs(activeDatabase, queryTabs, CONNECTION_UUID);
    }

    private void saveQueryTabs(String activeDatabase, List<Map<String, Object>> queryTabs, String connectionUuid) {
        SaveQueryTabsService.getInstance().invoke(SaveQueryTabsDto.fromPrimitives(Primitives.of(
            "connectionUuid", connectionUuid,
            "activeDatabase", activeDatabase,
            "queryTabs", queryTabs
        )));
    }

    private GetQueryTabsResultDto getQueryTabs(String connectionUuid) {
        return GetQueryTabsService.getInstance().invoke(GetQueryTabsDto.fromPrimitives(Primitives.of("connectionUuid", connectionUuid)));
    }

    private void saveState(String stateKey, String stateValue) {
        SaveWorkspaceStateService.getInstance().invoke(SaveWorkspaceStateDto.fromPrimitives(Primitives.of(
            "stateKey", stateKey,
            "stateValue", stateValue
        )));
    }

    private String getState(String stateKey) {
        return GetWorkspaceStateService.getInstance().invoke(GetWorkspaceStateDto.fromPrimitives(Primitives.of("stateKey", stateKey))).getStateValue();
    }
}
