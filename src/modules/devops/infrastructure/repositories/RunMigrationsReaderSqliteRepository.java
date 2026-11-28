package modules.devops.infrastructure.repositories;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import modules.shared.infrastructure.repositories.AbstractSqliteRepository;

import core.boot.BootPathsEnum;

public final class RunMigrationsReaderSqliteRepository extends AbstractSqliteRepository {

    private RunMigrationsReaderSqliteRepository() {
    }

    public static RunMigrationsReaderSqliteRepository getInstance() {
        return new RunMigrationsReaderSqliteRepository();
    }

    public Set<String> getDoneMigrations() {
        String sql = """
            -- getDoneMigrations
            SELECT migration_file
            FROM `app_migrations`
            """;
        Set<String> doneMigrations = new HashSet<>();
        for (Map<String, Object> row : query(sql)) {
            doneMigrations.add(String.valueOf(row.get("migration_file")));
        }
        return doneMigrations;
    }

    /**
     * El .sql desde el classpath (src/modules/devops/infrastructure/migrations).
     * @throws IllegalStateException si no esta en el classpath
     */
    public String getMigrationSql(String migrationFile) {
        String resourcePath = "/" + BootPathsEnum.PATH_MIGRATIONS + "/" + migrationFile;
        try (InputStream inputStream = getClass().getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                throw new IllegalStateException("migration not found in classpath: " + resourcePath);
            }
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
