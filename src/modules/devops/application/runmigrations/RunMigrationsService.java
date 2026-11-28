package modules.devops.application.runmigrations;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import modules.shared.application.Primitives;

import modules.devops.domain.enums.MigrationsEnum;
import modules.devops.infrastructure.repositories.RunMigrationsReaderSqliteRepository;
import modules.devops.infrastructure.repositories.RunMigrationsWriterSqliteRepository;

/**
 * Pasa las migraciones pendientes de la BD local al arrancar la app. Cada una se registra en app_migrations y no se
 * vuelve a pasar.
 */
public final class RunMigrationsService {

    private final RunMigrationsReaderSqliteRepository runMigrationsReaderSqliteRepository;
    private final RunMigrationsWriterSqliteRepository runMigrationsWriterSqliteRepository;

    private RunMigrationsService() {
        this.runMigrationsReaderSqliteRepository = RunMigrationsReaderSqliteRepository.getInstance();
        this.runMigrationsWriterSqliteRepository = RunMigrationsWriterSqliteRepository.getInstance();
    }

    public static RunMigrationsService getInstance() {
        return new RunMigrationsService();
    }

    public RunMigrationsResultDto invoke() {
        runMigrationsWriterSqliteRepository.createMigrationsTableIfNotExists();
        Set<String> doneMigrations = runMigrationsReaderSqliteRepository.getDoneMigrations();

        List<String> executedMigrations = new ArrayList<>();
        for (String migrationFile : MigrationsEnum.FILES) {
            if (doneMigrations.contains(migrationFile)) continue;

            runMigrationsWriterSqliteRepository.runMigration(
                migrationFile,
                runMigrationsReaderSqliteRepository.getMigrationSql(migrationFile)
            );
            executedMigrations.add(migrationFile);
        }
        return RunMigrationsResultDto.fromPrimitives(Primitives.of("executedMigrations", executedMigrations));
    }
}
