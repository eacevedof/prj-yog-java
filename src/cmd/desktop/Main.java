package cmd.desktop;

import core.boot.DesktopMain;

/**
 * Punto de entrada de la app de escritorio (bin/desktop).
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        DesktopMain.getInstance().run(args);
    }
}
