package core.boot;

import java.nio.file.Path;

import modules.shared.domain.enums.AppEnvKeyEnum;

import core.config.Global;

/**
 * Rutas de la app. Los datos del usuario (BD local, logs) viven fuera del repo, en ~/.javayog, salvo que
 * JAVAYOG_HOME diga otra cosa (los tests la apuntan a storage/). Con "/" tambien en Windows.
 */
public final class BootPathsEnum {

    public static final String PATH_MIGRATIONS = "modules/devops/infrastructure/migrations";

    private BootPathsEnum() {
    }

    public static String getPathAppHome() {
        String appHome = Global.getenv(AppEnvKeyEnum.JAVAYOG_HOME);
        if (appHome != null && !appHome.isBlank()) {
            return appHome.trim().replace("\\", "/");
        }
        return Path.of(System.getProperty("user.home"), ".javayog").toString().replace("\\", "/");
    }

    /**
     * Motor "Ficheros": el SQLite de trabajo de una conexion, con sus CSV y JSON cargados como tablas.
     */
    public static String getPathFilesWorkingDb(String connectionUuid) {
        return getPathAppHome() + "/files-engine/" + connectionUuid + ".sqlite";
    }

    public static String getPathSqlite() {
        return getPathAppHome() + "/javayog.sqlite";
    }
}
