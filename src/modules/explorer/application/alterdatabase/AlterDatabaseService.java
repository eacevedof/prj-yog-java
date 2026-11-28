package modules.explorer.application.alterdatabase;

import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import modules.shared.domain.services.SqlDialect;
import modules.shared.application.Primitives;

import static core.config.Global.__;

import modules.connections.domain.entities.ConnectionEntity;
import modules.connections.infrastructure.factories.TargetSessionFactory;
import modules.connections.infrastructure.repositories.ConnectionsReaderSqliteRepository;

import modules.history.application.recordqueryhistory.RecordQueryHistoryDto;
import modules.history.application.recordqueryhistory.RecordQueryHistoryService;

import modules.explorer.domain.enums.ExplorerMessageEnum;
import modules.explorer.domain.services.DatabaseAlterSqlBuilder;
import modules.explorer.infrastructure.repositories.ExplorerWriterJdbcRepository;
import modules.explorer.domain.exceptions.ExplorerException;

/**
 * Guarda el "Alter Database" (MySQL/MariaDB): un ALTER DATABASE con lo que cambio (juego de caracteres y cotejamiento,
 * cifrado, solo lectura, comentario). En produccion pide confirmacion (428).
 */
public final class AlterDatabaseService {

    private AlterDatabaseService() {
    }

    public static AlterDatabaseService getInstance() {
        return new AlterDatabaseService();
    }

    /**
     * @throws ExplorerException
     * @throws SQLException si el servidor rechaza el ALTER: sube tal cual hasta el controller
     * @throws modules.connections.domain.exceptions.ConnectionsException
     */
    public AlterDatabaseResultDto invoke(AlterDatabaseDto alterDatabaseDto) throws SQLException {
        failIfWrongInput(alterDatabaseDto);
        TargetSessionFactory targetSessionFactory = TargetSessionFactory.getInstance();
        String dbEngine = targetSessionFactory.getDbEngine(alterDatabaseDto.getConnectionUuid());
        if (!SqlDialect.MYSQL.equals(dbEngine)) {
            throw ExplorerException.badRequestCustom(__(ExplorerMessageEnum.ONLY_MYSQL_DATABASE_EDITOR_X, dbEngine));
        }
        DatabaseAlterSqlBuilder databaseAlterSqlBuilder = DatabaseAlterSqlBuilder.getInstance();
        List<String> errors = databaseAlterSqlBuilder.getErrors(alterDatabaseDto.getEdited());
        if (!errors.isEmpty()) throw ExplorerException.badRequestCustom(__(ExplorerMessageEnum.WRONG_CHARSET_OR_COLLATION_X, String.join(", ", errors)));
        String alterSql = databaseAlterSqlBuilder.getAlterSql(alterDatabaseDto.getOriginal(), alterDatabaseDto.getEdited());
        if (alterSql.isEmpty()) throw ExplorerException.badRequestCustom(__(ExplorerMessageEnum.NOTHING_TO_ALTER));
        failIfProductionNotConfirmed(alterDatabaseDto);

        long startNanos = System.nanoTime();
        boolean hasError = true;
        try {
            ExplorerWriterJdbcRepository.getInstance(targetSessionFactory.getSession(alterDatabaseDto.getConnectionUuid(), alterDatabaseDto.getSessionKey()))
                .executeStatements(List.of(alterSql));
            hasError = false;
        }
        finally {
            RecordQueryHistoryService.getInstance().invoke(RecordQueryHistoryDto.fromPrimitives(Primitives.of(
                "connectionUuid", alterDatabaseDto.getConnectionUuid(),
                "databaseName", alterDatabaseDto.getOriginal().databaseName(),
                "sqlQuery", alterSql,
                "elapsedMs", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos),
                "hasError", hasError
            )));
        }
        return AlterDatabaseResultDto.fromPrimitives(Primitives.of("alterSql", alterSql));
    }

    /**
     * @throws ExplorerException
     */
    private void failIfProductionNotConfirmed(AlterDatabaseDto alterDatabaseDto) {
        if (alterDatabaseDto.isConfirmed()) return;
        ConnectionEntity connectionEntity = ConnectionsReaderSqliteRepository.getInstance().getConnectionByUuid(alterDatabaseDto.getConnectionUuid());
        if (connectionEntity == null || !connectionEntity.isProduction()) return;
        throw ExplorerException.preconditionRequiredCustom(__(
            ExplorerMessageEnum.PRODUCTION_X_ALTER_DATABASE_X, connectionEntity.getConnectionName(), alterDatabaseDto.getOriginal().databaseName()
        ));
    }

    /**
     * @throws ExplorerException
     */
    private void failIfWrongInput(AlterDatabaseDto alterDatabaseDto) {
        if (alterDatabaseDto.getConnectionUuid().isEmpty()) {
            throw ExplorerException.badRequestCustom(__(ExplorerMessageEnum.FIELD_IS_REQUIRED_X, "connectionUuid"));
        }
        if (alterDatabaseDto.getSessionKey().isEmpty()) {
            throw ExplorerException.badRequestCustom(__(ExplorerMessageEnum.FIELD_IS_REQUIRED_X, "sessionKey"));
        }
        if (alterDatabaseDto.getOriginal() == null || alterDatabaseDto.getEdited() == null) {
            throw ExplorerException.badRequestCustom(__(ExplorerMessageEnum.FIELD_IS_REQUIRED_X, "original/edited"));
        }
        if (!alterDatabaseDto.getOriginal().databaseName().equals(alterDatabaseDto.getEdited().databaseName())) {
            throw ExplorerException.badRequestCustom(__(
                ExplorerMessageEnum.DATABASE_NAME_MISMATCH_X_X, alterDatabaseDto.getOriginal().databaseName(), alterDatabaseDto.getEdited().databaseName()
            ));
        }
    }
}
