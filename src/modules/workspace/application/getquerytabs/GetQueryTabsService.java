package modules.workspace.application.getquerytabs;

import modules.shared.application.Primitives;

import static core.config.Global.__;

import modules.workspace.domain.enums.WorkspaceKeyEnum;
import modules.workspace.infrastructure.repositories.QueryTabsReaderSqliteRepository;
import modules.workspace.infrastructure.repositories.WorkspaceStateSqliteRepository;
import modules.workspace.domain.exceptions.WorkspaceException;

/**
 * Las pestañas de query guardadas de una conexion y su BD activa, para abrirla como se dejo.
 */
public final class GetQueryTabsService {

    private GetQueryTabsService() {
    }

    public static GetQueryTabsService getInstance() {
        return new GetQueryTabsService();
    }

    /**
     * @throws WorkspaceException
     */
    public GetQueryTabsResultDto invoke(GetQueryTabsDto getQueryTabsDto) {
        String connectionUuid = getQueryTabsDto.getConnectionUuid();
        if (connectionUuid.isEmpty()) {
            throw WorkspaceException.badRequestCustom(__("{0} is required", "connectionUuid"));
        }
        String activeDatabase = WorkspaceStateSqliteRepository.getInstance().getStateValue(WorkspaceKeyEnum.ACTIVE_DATABASE_PREFIX + connectionUuid);
        return GetQueryTabsResultDto.fromPrimitives(Primitives.of(
            "activeDatabase", activeDatabase == null ? "" : activeDatabase,
            "queryTabs", QueryTabsReaderSqliteRepository.getInstance().getQueryTabs(connectionUuid)
        ));
    }
}
