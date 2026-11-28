package modules.workspace.application.savequerytabs;

import java.util.Map;

import modules.shared.infrastructure.components.date.DateTimer;
import static modules.shared.application.Primitives.getTrimmedString;

import static core.config.Global.__;

import modules.workspace.domain.enums.WorkspaceKeyEnum;
import modules.workspace.infrastructure.repositories.QueryTabsWriterSqliteRepository;
import modules.workspace.infrastructure.repositories.WorkspaceStateSqliteRepository;
import modules.workspace.domain.exceptions.WorkspaceException;

/**
 * Guarda las pestañas de query de una conexion (titulo, SQL y cual esta delante) y su BD activa, para recuperarlas al
 * volver a abrirla, tambien despues de reiniciar la app.
 */
public final class SaveQueryTabsService {

    private SaveQueryTabsService() {
    }

    public static SaveQueryTabsService getInstance() {
        return new SaveQueryTabsService();
    }

    /**
     * @throws WorkspaceException
     */
    public void invoke(SaveQueryTabsDto saveQueryTabsDto) {
        failIfWrongInput(saveQueryTabsDto);

        String updatedAt = DateTimer.getInstance().getNowDt();
        String connectionUuid = saveQueryTabsDto.getConnectionUuid();
        QueryTabsWriterSqliteRepository.getInstance().replaceQueryTabs(connectionUuid, saveQueryTabsDto.getQueryTabs(), updatedAt);
        WorkspaceStateSqliteRepository.getInstance().upsertState(
            WorkspaceKeyEnum.ACTIVE_DATABASE_PREFIX + connectionUuid, saveQueryTabsDto.getActiveDatabase(), updatedAt
        );
    }

    /**
     * @throws WorkspaceException
     */
    private void failIfWrongInput(SaveQueryTabsDto saveQueryTabsDto) {
        if (saveQueryTabsDto.getConnectionUuid().isEmpty()) {
            throw WorkspaceException.badRequestCustom(__("{0} is required", "connectionUuid"));
        }
        for (Map<String, Object> queryTab : saveQueryTabsDto.getQueryTabs()) {
            String tabTitle = getTrimmedString(queryTab.get(WorkspaceKeyEnum.TAB_TITLE));
            if (tabTitle.isEmpty()) {
                throw WorkspaceException.badRequestCustom(__("{0} is required", WorkspaceKeyEnum.TAB_TITLE));
            }
            if (tabTitle.length() > WorkspaceKeyEnum.MAX_TAB_TITLE_LENGTH) {
                throw WorkspaceException.badRequestCustom(__("{0} is too long", WorkspaceKeyEnum.TAB_TITLE));
            }
        }
    }
}
