package modules.explorer.infrastructure.factories;

import java.sql.Connection;

import modules.shared.domain.services.SqlDialect;

import modules.connections.infrastructure.factories.TargetSessionFactory;

import modules.explorer.infrastructure.repositories.AbstractExplorerReaderRepository;
import modules.explorer.infrastructure.repositories.ExplorerReaderMssqlRepository;
import modules.explorer.infrastructure.repositories.ExplorerReaderMysqlRepository;
import modules.explorer.infrastructure.repositories.ExplorerReaderPgsqlRepository;
import modules.explorer.infrastructure.repositories.ExplorerReaderSqliteRepository;

/**
 * El repositorio del arbol que toca al motor de la conexion, sobre la sesion de esa clave.
 */
public final class ExplorerReaderRepositoryFactory {

    private ExplorerReaderRepositoryFactory() {
    }

    public static ExplorerReaderRepositoryFactory getInstance() {
        return new ExplorerReaderRepositoryFactory();
    }

    /**
     * @throws modules.connections.domain.exceptions.ConnectionsException
     */
    public AbstractExplorerReaderRepository getRepository(String connectionUuid, String sessionKey) {
        TargetSessionFactory targetSessionFactory = TargetSessionFactory.getInstance();
        String dbEngine = targetSessionFactory.getDbEngine(connectionUuid);
        Connection pdo = targetSessionFactory.getSessionPdo(connectionUuid, sessionKey);
        return switch (dbEngine) {
            case SqlDialect.PGSQL -> ExplorerReaderPgsqlRepository.getInstance(pdo);
            case SqlDialect.SQLITE -> ExplorerReaderSqliteRepository.getInstance(pdo);
            case SqlDialect.MSSQL -> ExplorerReaderMssqlRepository.getInstance(pdo);
            default -> ExplorerReaderMysqlRepository.getInstance(pdo);
        };
    }
}
