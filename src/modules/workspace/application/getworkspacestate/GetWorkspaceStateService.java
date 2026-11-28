package modules.workspace.application.getworkspacestate;

import modules.shared.application.Primitives;

import static core.config.Global.__;

import modules.workspace.domain.enums.WorkspaceKeyEnum;
import modules.workspace.domain.enums.WorkspaceMessageEnum;
import modules.workspace.infrastructure.repositories.WorkspaceStateSqliteRepository;
import modules.workspace.domain.exceptions.WorkspaceException;

/**
 * Un dato guardado del espacio de trabajo; "" si no se ha guardado nunca.
 */
public final class GetWorkspaceStateService {

    private GetWorkspaceStateService() {
    }

    public static GetWorkspaceStateService getInstance() {
        return new GetWorkspaceStateService();
    }

    /**
     * @throws WorkspaceException
     */
    public GetWorkspaceStateResultDto invoke(GetWorkspaceStateDto getWorkspaceStateDto) {
        failIfWrongInput(getWorkspaceStateDto);

        String stateValue = WorkspaceStateSqliteRepository.getInstance().getStateValue(getWorkspaceStateDto.getStateKey());
        return GetWorkspaceStateResultDto.fromPrimitives(Primitives.of("stateValue", stateValue == null ? "" : stateValue));
    }

    /**
     * @throws WorkspaceException
     */
    private void failIfWrongInput(GetWorkspaceStateDto getWorkspaceStateDto) {
        String stateKey = getWorkspaceStateDto.getStateKey();
        if (!WorkspaceKeyEnum.isStateKey(stateKey)) {
            throw WorkspaceException.badRequestCustom(__(WorkspaceMessageEnum.UNKNOWN_WORKSPACE_STATE_X, stateKey));
        }
    }
}
