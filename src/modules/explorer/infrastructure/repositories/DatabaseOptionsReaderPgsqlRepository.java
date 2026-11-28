package modules.explorer.infrastructure.repositories;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import modules.shared.domain.services.SqlDialect;
import modules.shared.infrastructure.repositories.AbstractJdbcRepository;
import static modules.shared.application.Primitives.getTrimmedString;

/**
 * Lo que lee el "Create Database" de PostgreSQL (un esquema): los roles que pueden ser su propietario y el rol actual.
 */
public final class DatabaseOptionsReaderPgsqlRepository extends AbstractJdbcRepository {

    private DatabaseOptionsReaderPgsqlRepository(Connection pdo) {
        super(pdo, SqlDialect.PGSQL);
    }

    public static DatabaseOptionsReaderPgsqlRepository getInstance(Connection pdo) {
        return new DatabaseOptionsReaderPgsqlRepository(pdo);
    }

    /**
     * Los roles sin los internos (pg_*).
     */
    public List<String> getRoleNames() throws SQLException {
        List<String> roleNames = new ArrayList<>();
        for (Map<String, Object> row : query("""
            -- getRoleNames
            SELECT rolname AS role_name
            FROM pg_catalog.pg_roles
            WHERE 1=1
            AND rolname NOT LIKE 'pg\\_%'
            ORDER BY rolname
            """)) {
            roleNames.add(getTrimmedString(row.get("role_name")));
        }
        return roleNames;
    }

    public String getCurrentRole() throws SQLException {
        return getTrimmedString(query("""
            -- getCurrentRole
            SELECT current_user AS role_name
            """).getFirst().get("role_name"));
    }
}
