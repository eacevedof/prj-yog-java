package modules.workspace.application.resetworkspace;

import modules.shared.application.Primitives;

import modules.workspace.infrastructure.repositories.QueryTabsWriterSqliteRepository;
import modules.workspace.infrastructure.repositories.WorkspaceStateSqliteRepository;

/**
 * "Restablecer espacio de trabajo": deja la app como el primer dia en lo que recuerda de la ventana (los filtros del
 * panel, la BD activa de cada conexion, las carpetas y el ultimo fichero de los dialogos y las pestañas de query). Las
 * conexiones, el tema, el idioma y el historial de queries se quedan.
 */
public final class ResetWorkspaceService {

    private ResetWorkspaceService() {
    }

    public static ResetWorkspaceService getInstance() {
        return new ResetWorkspaceService();
    }

    public ResetWorkspaceResultDto invoke(ResetWorkspaceDto resetWorkspaceDto) {
        int deletedStates = WorkspaceStateSqliteRepository.getInstance().deleteAllStates();
        int deletedQueryTabs = QueryTabsWriterSqliteRepository.getInstance().deleteAllQueryTabs();
        return ResetWorkspaceResultDto.fromPrimitives(Primitives.of(
            "deletedStates", deletedStates,
            "deletedQueryTabs", deletedQueryTabs
        ));
    }
}
