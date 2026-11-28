package modules.shared.infrastructure.controllers;

import java.util.Map;

/**
 * Lo que la ventana principal necesita de la vista de una conexion abierta, sea del motor que sea (SQL: arbol + editor;
 * Redis: navegador de claves + consola...): prepararla con la conexion y cerrar sus sesiones al cerrar la pestaña.
 */
public interface ConnectionViewController {

    /**
     * @param connection la conexion guardada (claves de ConnectionsKeyEnum, sin secretos)
     */
    void setup(Map<String, Object> connection);

    /**
     * Corta lo que este en marcha y cierra las sesiones de esta pestaña (fuera del hilo de la UI si hace red).
     */
    void close();

    /**
     * Guarda lo que se debe recuperar al volver a abrir la conexion (las pestañas de query con su SQL...). Lo llama la
     * ventana al salir de la app; close() tambien lo hace.
     */
    default void saveState() {
    }

    /**
     * El arbol de objetos que va bajo la conexion en el panel; null si la vista no tiene (Redis y MongoDB llevan su
     * propio navegador dentro de la pestaña).
     */
    default ConnectionTreeHandler getConnectionTreeHandler() {
        return null;
    }
}
