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
 * Lo que lee el "Create Database" de SQL Server: los cotejamientos y el del servidor (el que toma una BD nueva).
 */
public final class DatabaseOptionsReaderMssqlRepository extends AbstractJdbcRepository {

    private DatabaseOptionsReaderMssqlRepository(Connection pdo) {
        super(pdo, SqlDialect.MSSQL);
    }

    public static DatabaseOptionsReaderMssqlRepository getInstance(Connection pdo) {
        return new DatabaseOptionsReaderMssqlRepository(pdo);
    }

    public List<String> getCollationNames() throws SQLException {
        List<String> collationNames = new ArrayList<>();
        for (Map<String, Object> row : query("""
            -- getCollationNames
            SELECT name AS collation_name
            FROM sys.fn_helpcollations()
            ORDER BY name
            """)) {
            collationNames.add(getTrimmedString(row.get("collation_name")));
        }
        return collationNames;
    }

    public String getServerCollation() throws SQLException {
        return getTrimmedString(query("""
            -- getServerCollation
            SELECT CAST(SERVERPROPERTY('Collation') AS nvarchar(128)) AS collation_name
            """).getFirst().get("collation_name"));
    }
}
