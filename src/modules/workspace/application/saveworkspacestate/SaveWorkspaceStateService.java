package modules.workspace.application.saveworkspacestate;

import modules.shared.infrastructure.components.date.DateTimer;

import static core.config.Global.__;

import modules.workspace.domain.enums.WorkspaceKeyEnum;
import modules.workspace.infrastructure.repositories.WorkspaceStateSqliteRepository;
import modules.workspace.domain.exceptions.WorkspaceException;

/**
 * Guarda un dato del espacio de trabajo (un filtro del panel, la BD activa de una conexion) para el siguiente arranque:
 * solo las claves de WorkspaceKeyEnum.
 */
public final class SaveWorkspaceStateService {

    private SaveWorkspaceStateService() {
    }

    public static SaveWorkspaceStateService getInstance() {
        return new SaveWorkspaceStateService();
    }

    /**
     * @throws WorkspaceException
     */
    public void invoke(SaveWorkspaceStateDto saveWorkspaceStateDto) {
        failIfWrongInput(saveWorkspaceStateDto);

        WorkspaceStateSqliteRepository.getInstance().upsertState(
            saveWorkspaceStateDto.getStateKey(), saveWorkspaceStateDto.getStateValue(), DateTimer.getInstance().getNowDt()
        );
    }

    /**
     * @throws WorkspaceException
     */
    private void failIfWrongInput(SaveWorkspaceStateDto saveWorkspaceStateDto) {
        String stateKey = saveWorkspaceStateDto.getStateKey();
        if (!WorkspaceKeyEnum.isStateKey(stateKey)) {
            throw WorkspaceException.badRequestCustom(__("Unknown workspace state {0}", stateKey));
        }
        if (saveWorkspaceStateDto.getStateValue().length() > WorkspaceKeyEnum.MAX_STATE_VALUE_LENGTH) {
            throw WorkspaceException.badRequestCustom(__("{0} is too long", stateKey));
        }
    }
}
