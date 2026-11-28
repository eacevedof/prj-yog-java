package modules.devops.application.runmigrations;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class RunMigrationsResultDto {

    private final List<String> executedMigrations;

    private RunMigrationsResultDto(Map<String, Object> primitives) {
        List<String> migrations = new ArrayList<>();
        if (primitives.get("executedMigrations") instanceof List<?> list) {
            list.forEach(migration -> migrations.add(String.valueOf(migration)));
        }
        this.executedMigrations = List.copyOf(migrations);
    }

    public static RunMigrationsResultDto fromPrimitives(Map<String, Object> primitives) {
        return new RunMigrationsResultDto(primitives);
    }

    public List<String> getExecutedMigrations() {
        return executedMigrations;
    }
}
