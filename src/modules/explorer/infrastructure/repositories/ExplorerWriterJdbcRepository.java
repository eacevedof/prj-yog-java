package modules.explorer.infrastructure.repositories;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import modules.shared.infrastructure.repositories.AbstractJdbcRepository;

/**
 * Lo que el arbol cambia en la BD destino (duplicar una tabla): sentencias sin parametros, una tras otra. No van en
 * transaccion: en MySQL un CREATE TABLE confirma solo y por el tunel HTTP no hay transacciones entre peticiones.
 */
public final class ExplorerWriterJdbcRepository extends AbstractJdbcRepository {

    private ExplorerWriterJdbcRepository(Connection pdo) {
        super(pdo);
    }

    public static ExplorerWriterJdbcRepository getInstance(Connection pdo) {
        return new ExplorerWriterJdbcRepository(pdo);
    }

    public void executeStatements(List<String> statements) throws SQLException {
        for (String sql : statements) {
            try (Statement statement = pdo.createStatement()) {
                statement.execute(sql);
            }
        }
    }
}
