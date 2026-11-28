package modules.shared.infrastructure.controllers;

import java.util.function.Consumer;

import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.input.KeyEvent;

/**
 * El arbol de objetos de una conexion abierta, colgado bajo su nodo en el panel de conexiones (como el Object Browser
 * de SQLyog). El panel solo sabe de conexiones; lo que hay debajo (BD, carpetas, tablas, columnas) lo pinta y lo
 * maneja quien implementa esto. Los nodos de debajo llevan valores propios del implementador, nunca la conexion.
 */
public interface ConnectionTreeHandler {

    /**
     * Engancha el arbol bajo el nodo de la conexion y empieza a cargarlo.
     * @param connectionItem el nodo de la conexion en el panel: sus hijos pasan a ser los del implementador
     * @param onViewChanged repintar las celdas (la BD activa va en negrita)
     * @param onStatusChanged texto de estado ("9 BD", un error...) para la barra del panel
     */
    void attach(TreeItem<Object> connectionItem, Runnable onViewChanged, Consumer<String> onStatusChanged);

    /**
     * Pinta una celda de un nodo propio (texto, icono, clases CSS y menu contextual).
     */
    void updateCell(TreeCell<Object> treeCell, TreeItem<Object> treeItem);

    /**
     * Se eligio un nodo propio (p. ej. una BD: pasa a ser la activa).
     */
    void onSelected(TreeItem<Object> treeItem);

    /**
     * Doble clic en un nodo propio.
     */
    void onActivated(TreeItem<Object> treeItem);

    /**
     * Tecla sobre un nodo propio (F5, F11...); se consume si se atiende.
     */
    void onKeyPressed(TreeItem<Object> treeItem, KeyEvent keyEvent);

    /**
     * Refrescar: la BD del nodo, o todas las BD si treeItem es la conexion o null.
     */
    void refresh(TreeItem<Object> treeItem);

    /**
     * Los filtros del panel: nombre de BD y nombre de objeto (tablas, vistas, rutinas, triggers).
     */
    void setFilters(String databaseFilter, String objectFilter);

    /**
     * Lo que se suelta en el editor al arrastrar el nodo (lo mismo que mete el doble clic: el nombre entrecomillado de
     * la BD, tabla, vista o columna); vacio = el nodo no se arrastra.
     */
    default String getDragText(TreeItem<Object> treeItem) {
        return "";
    }

    /**
     * Si el motor de la conexion crea BDs (boton y menu "Crear BD" del panel).
     */
    default boolean canCreateDatabase() {
        return false;
    }

    /**
     * Abre el dialogo "Crear BD"; al crearla, el arbol recarga sus BD.
     * @param connectionName el nombre de la conexion, para el titulo del dialogo
     */
    default void createDatabase(String connectionName) {
    }
}
