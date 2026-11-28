package modules.workspace.application.getworkspacestate;

import modules.shared.application.Primitives;

import static core.config.Global.__;

import modules.workspace.domain.enums.WorkspaceKeyEnum;
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
        String stateKey = getWorkspaceStateDto.getStateKey();
        if (!WorkspaceKeyEnum.isStateKey(stateKey)) {
            throw WorkspaceException.badRequestCustom(__("Unknown workspace state {0}", stateKey));
        }
        String stateValue = WorkspaceStateSqliteRepository.getInstance().getStateValue(stateKey);
        return GetWorkspaceStateResultDto.fromPrimitives(Primitives.of("stateValue", stateValue == null ? "" : stateValue));
    }
}
