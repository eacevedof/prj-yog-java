package modules.workspace.infrastructure.components;

import java.io.File;

import modules.shared.application.Primitives;

import modules.workspace.application.getworkspacestate.GetWorkspaceStateDto;
import modules.workspace.application.saveworkspacestate.SaveWorkspaceStateDto;
import modules.workspace.application.getworkspacestate.GetWorkspaceStateService;
import modules.workspace.application.saveworkspacestate.SaveWorkspaceStateService;

/**
 * La ultima carpeta de un dialogo de ficheros (Volcado SQL, Exportar como, Ejecutar fichero SQL), guardada en
 * app_workspace_state para que siga ahi tras reiniciar: manda la ultima que se eligio o en la que se escribio. Si no
 * se puede leer o guardar, se dice por stderr y el dialogo sigue con la carpeta del usuario.
 */
public final class LastDirectoryComponent {

    private final String stateKey;

    private LastDirectoryComponent(String stateKey) {
        this.stateKey = stateKey;
    }

    /**
     * stateKey: una de las WorkspaceKeyEnum.*_DIRECTORY.
     */
    public static LastDirectoryComponent getInstance(String stateKey) {
        return new LastDirectoryComponent(stateKey);
    }

    /**
     * La guardada si aun existe; si no, la carpeta del usuario.
     */
    public File getDirectory() {
        try {
            String savedPath = GetWorkspaceStateService.getInstance().invoke(GetWorkspaceStateDto.fromPrimitives(Primitives.of(
                "stateKey", stateKey
            ))).getStateValue().trim();
            if (!savedPath.isEmpty() && new File(savedPath).isDirectory()) return new File(savedPath);
        }
        catch (RuntimeException e) {
            System.err.println("multidbs: no se pudo leer la carpeta guardada " + stateKey + ": " + e.getMessage());
        }
        return new File(System.getProperty("user.home"));
    }

    /**
     * La carpeta del fichero (o la propia carpeta si ya lo es); nada si no hay ruta.
     */
    public void saveDirectoryOf(File file) {
        if (file == null) return;
        File directory = file.isDirectory() ? file : file.getAbsoluteFile().getParentFile();
        if (directory == null) return;
        try {
            SaveWorkspaceStateService.getInstance().invoke(SaveWorkspaceStateDto.fromPrimitives(Primitives.of(
                "stateKey", stateKey,
                "stateValue", directory.getPath()
            )));
        }
        catch (RuntimeException e) {
            System.err.println("multidbs: no se pudo guardar la carpeta " + stateKey + ": " + e.getMessage());
        }
    }
}
