package modules.explorer.application.getdatabaseoptions;

import java.sql.Connection;
import java.sql.SQLException;

import modules.shared.domain.services.SqlDialect;
import modules.shared.application.Primitives;

import static core.config.Global.__;

import modules.connections.infrastructure.factories.TargetSessionFactory;

import modules.history.application.recordqueryhistory.QueryHistoryTracer;

import modules.explorer.domain.enums.ExplorerMessageEnum;
import modules.explorer.domain.entities.DatabaseOptionsEntity;
import modules.explorer.infrastructure.repositories.DatabaseOptionsReaderMysqlRepository;
import modules.explorer.domain.exceptions.ExplorerException;

/**
 * Lo que abre el "Alter Database" (MySQL/MariaDB): las opciones de la BD y los juegos de caracteres del servidor con
 * sus cotejamientos.
 */
public final class GetDatabaseOptionsService {

    private GetDatabaseOptionsService() {
    }

    public static GetDatabaseOptionsService getInstance() {
        return new GetDatabaseOptionsService();
    }

    /**
     * @throws ExplorerException
     * @throws SQLException si la BD falla: sube tal cual hasta el controller
     * @throws modules.connections.domain.exceptions.ConnectionsException
     */
    public GetDatabaseOptionsResultDto invoke(GetDatabaseOptionsDto getDatabaseOptionsDto) throws SQLException {
        return QueryHistoryTracer.getInstance(getDatabaseOptionsDto.getConnectionUuid(), getDatabaseOptionsDto.getDatabaseName()).getTraced(() -> getResult(getDatabaseOptionsDto));
    }

    private GetDatabaseOptionsResultDto getResult(GetDatabaseOptionsDto getDatabaseOptionsDto) throws SQLException {
        failIfWrongInput(getDatabaseOptionsDto);
        TargetSessionFactory targetSessionFactory = TargetSessionFactory.getInstance();
        String dbEngine = targetSessionFactory.getDbEngine(getDatabaseOptionsDto.getConnectionUuid());
        if (!SqlDialect.MYSQL.equals(dbEngine)) {
            throw ExplorerException.badRequestCustom(__(ExplorerMessageEnum.ONLY_MYSQL_DATABASE_EDITOR_X, dbEngine));
        }
        Connection pdo = targetSessionFactory.getSession(getDatabaseOptionsDto.getConnectionUuid(), getDatabaseOptionsDto.getSessionKey());
        DatabaseOptionsReaderMysqlRepository databaseOptionsReaderMysqlRepository = DatabaseOptionsReaderMysqlRepository.getInstance(pdo);
        DatabaseOptionsEntity databaseOptions = databaseOptionsReaderMysqlRepository.getDatabaseOptions(getDatabaseOptionsDto.getDatabaseName());
        if (databaseOptions == null) {
            throw ExplorerException.notFoundCustom(__(ExplorerMessageEnum.DATABASE_X_NOT_FOUND, getDatabaseOptionsDto.getDatabaseName()));
        }
        return GetDatabaseOptionsResultDto.fromPrimitives(Primitives.of(
            "databaseOptions", databaseOptions,
            "collationsByCharacterSet", databaseOptionsReaderMysqlRepository.getCollationsByCharacterSet()
        ));
    }

    /**
     * @throws ExplorerException
     */
    private void failIfWrongInput(GetDatabaseOptionsDto getDatabaseOptionsDto) {
        if (getDatabaseOptionsDto.getConnectionUuid().isEmpty()) {
            throw ExplorerException.badRequestCustom(__(ExplorerMessageEnum.FIELD_IS_REQUIRED_X, "connectionUuid"));
        }
        if (getDatabaseOptionsDto.getSessionKey().isEmpty()) {
            throw ExplorerException.badRequestCustom(__(ExplorerMessageEnum.FIELD_IS_REQUIRED_X, "sessionKey"));
        }
        if (getDatabaseOptionsDto.getDatabaseName().isEmpty()) {
            throw ExplorerException.badRequestCustom(__(ExplorerMessageEnum.FIELD_IS_REQUIRED_X, "databaseName"));
        }
    }
}
