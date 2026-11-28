package modules.devops.domain.enums;

import java.util.List;

/**
 * Las migraciones de la BD local, en orden. Van dentro del jar (classpath), asi que no se pueden listar como una
 * carpeta: cada migracion nueva se anade aqui al final.
 */
public final class MigrationsEnum {

    public static final List<String> FILES = List.of(
        "20261124120000-create-connections-table.sql",
        "20261125120000-create-query-history-table.sql",
        "20261125130000-create-settings-table.sql",
        "20261125140000-add-ssh-tunnel-to-connections.sql",
        "20261125150000-add-http-tunnel-to-connections.sql",
        "20261126150000-create-workspace-state-tables.sql",
        "20261127130000-create-files-tables-table.sql"
    );

    private MigrationsEnum() {
    }
}
