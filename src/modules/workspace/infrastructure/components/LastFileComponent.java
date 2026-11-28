package modules.workspace.infrastructure.components;

import java.io.File;

import modules.shared.application.Primitives;

import modules.workspace.application.getworkspacestate.GetWorkspaceStateDto;
import modules.workspace.application.saveworkspacestate.SaveWorkspaceStateDto;
import modules.workspace.application.getworkspacestate.GetWorkspaceStateService;
import modules.workspace.application.saveworkspacestate.SaveWorkspaceStateService;

/**
 * El ultimo fichero de un dialogo (el .sql de "Ejecutar fichero SQL"), guardado en app_workspace_state con su ruta
 * entera para que siga ahi tras reiniciar. Si no se puede leer o guardar, se dice por stderr y el dialogo sigue vacio.
 */
public final class LastFileComponent {

    private final String stateKey;

    private LastFileComponent(String stateKey) {
        this.stateKey = stateKey;
    }

    /**
     * stateKey: una de las WorkspaceKeyEnum.*_LAST_FILE.
     */
    public static LastFileComponent getInstance(String stateKey) {
        return new LastFileComponent(stateKey);
    }

    /**
     * El guardado si aun existe; null si no hay o ya no esta.
     */
    public File getFile() {
        try {
            String savedPath = GetWorkspaceStateService.getInstance().invoke(GetWorkspaceStateDto.fromPrimitives(Primitives.of(
                "stateKey", stateKey
            ))).getStateValue().trim();
            if (!savedPath.isEmpty() && new File(savedPath).isFile()) return new File(savedPath);
        }
        catch (RuntimeException e) {
            System.err.println("multidbs: no se pudo leer el fichero guardado " + stateKey + ": " + e.getMessage());
        }
        return null;
    }

    public void saveFile(File file) {
        if (file == null) return;
        try {
            SaveWorkspaceStateService.getInstance().invoke(SaveWorkspaceStateDto.fromPrimitives(Primitives.of(
                "stateKey", stateKey,
                "stateValue", file.getAbsolutePath()
            )));
        }
        catch (RuntimeException e) {
            System.err.println("multidbs: no se pudo guardar el fichero " + stateKey + ": " + e.getMessage());
        }
    }
}
