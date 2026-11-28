package modules.connections.domain.enums;

/**
 * Tamaños del panel de conexiones.
 */
public final class ConnectionsConfigEnum {

    // lo que se suma al texto mas largo del arbol al ajustar el panel: la barra de desplazamiento vertical y un respiro
    public static final double TREE_SCROLLBAR_MARGIN = 20;
    // el ajuste espera a que el arbol deje de cambiar (cargar una BD trae muchas filas seguidas)
    public static final double TREE_WIDTH_DELAY_MS = 150;

    private ConnectionsConfigEnum() {
    }
}
