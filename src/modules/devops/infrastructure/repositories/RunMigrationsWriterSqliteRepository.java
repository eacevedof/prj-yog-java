package modules.devops.infrastructure.repositories;

import modules.shared.infrastructure.components.date.DateTimer;
import modules.shared.infrastructure.repositories.AbstractSqliteRepository;

public final class RunMigrationsWriterSqliteRepository extends AbstractSqliteRepository {

    private RunMigrationsWriterSqliteRepository() {
    }

    public static RunMigrationsWriterSqliteRepository getInstance() {
        return new RunMigrationsWriterSqliteRepository();
    }

    public void createMigrationsTableIfNotExists() {
        executeRaw("""
            CREATE TABLE IF NOT EXISTS `app_migrations` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT,
                `migration_file` VARCHAR(255) NOT NULL UNIQUE,
                `executed_at` VARCHAR(19) NOT NULL
            )
            """);
    }

    /**
     * La migracion y su registro van en la misma transaccion: si falla cualquiera de los dos no queda nada y se
     * reintenta entera al arrancar (sin ella, un fallo del INSERT dejaba la migracion aplicada y sin registrar).
     */
    public void runMigration(String migrationFile, String migrationSql) {
        String sql = """
            -- runMigration
            INSERT INTO `app_migrations` (migration_file, executed_at)
            VALUES (?, ?)
            """;
        executeInTransaction(() -> {
            executeRaw(migrationSql);
            execute(sql, migrationFile, DateTimer.getInstance().getNowDt());
        });
    }
}
