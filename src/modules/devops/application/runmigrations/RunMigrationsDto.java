package modules.devops.application.runmigrations;

import java.util.Map;

/**
 * Sin datos de entrada: se pasan todas las migraciones pendientes.
 */
public final class RunMigrationsDto {

    private RunMigrationsDto(Map<String, Object> primitives) {
    }

    public static RunMigrationsDto fromPrimitives(Map<String, Object> primitives) {
        return new RunMigrationsDto(primitives);
    }
}
