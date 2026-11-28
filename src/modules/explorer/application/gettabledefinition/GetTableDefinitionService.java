package modules.explorer.application.gettabledefinition;

import java.sql.Connection;
import java.sql.SQLException;

import modules.shared.domain.services.SqlDialect;
import modules.shared.application.Primitives;

import static core.config.Global.__;

import modules.connections.infrastructure.factories.TargetSessionFactory;

import modules.history.application.recordqueryhistory.QueryHistoryTracer;

import modules.explorer.domain.enums.ExplorerMessageEnum;
import modules.explorer.infrastructure.repositories.TableDefinitionReaderMysqlRepository;
import modules.explorer.domain.exceptions.ExplorerException;

/**
 * Lo que abre el editor de tablas ("Alter Table" de SQLyog): la definicion de la tabla y los motores y cotejamientos
 * del servidor. Solo MySQL/MariaDB.
 */
public final class GetTableDefinitionService {

    private GetTableDefinitionService() {
    }

    public static GetTableDefinitionService getInstance() {
        return new GetTableDefinitionService();
    }

    /**
     * @throws ExplorerException
     * @throws SQLException si la BD falla: sube tal cual hasta el controller
     * @throws modules.connections.domain.exceptions.ConnectionsException
     */
    public GetTableDefinitionResultDto invoke(GetTableDefinitionDto getTableDefinitionDto) throws SQLException {
        return QueryHistoryTracer.getInstance(getTableDefinitionDto.getConnectionUuid(), getTableDefinitionDto.getDatabaseName()).getTraced(() -> getResult(getTableDefinitionDto));
    }

    private GetTableDefinitionResultDto getResult(GetTableDefinitionDto getTableDefinitionDto) throws SQLException {
        failIfWrongInput(getTableDefinitionDto);
        TargetSessionFactory targetSessionFactory = TargetSessionFactory.getInstance();
        String dbEngine = targetSessionFactory.getDbEngine(getTableDefinitionDto.getConnectionUuid());
        if (!SqlDialect.MYSQL.equals(dbEngine)) {
            throw ExplorerException.badRequestCustom(__(ExplorerMessageEnum.ONLY_MYSQL_TABLE_EDITOR_X, dbEngine));
        }
        Connection pdo = targetSessionFactory.getSessionPdo(getTableDefinitionDto.getConnectionUuid(), getTableDefinitionDto.getSessionKey());
        TableDefinitionReaderMysqlRepository tableDefinitionReaderMysqlRepository = TableDefinitionReaderMysqlRepository.getInstance(pdo);
        return GetTableDefinitionResultDto.fromPrimitives(Primitives.of(
            "tableDefinition", tableDefinitionReaderMysqlRepository.getTableDefinition(getTableDefinitionDto.getDatabaseName(), getTableDefinitionDto.getTableName()),
            "engines", tableDefinitionReaderMysqlRepository.getEngines(),
            "collations", tableDefinitionReaderMysqlRepository.getCollations()
        ));
    }

    /**
     * @throws ExplorerException
     */
    private void failIfWrongInput(GetTableDefinitionDto getTableDefinitionDto) {
        if (getTableDefinitionDto.getConnectionUuid().isEmpty()) {
            throw ExplorerException.badRequestCustom(__(ExplorerMessageEnum.FIELD_IS_REQUIRED_X, "connectionUuid"));
        }
        if (getTableDefinitionDto.getSessionKey().isEmpty()) {
            throw ExplorerException.badRequestCustom(__(ExplorerMessageEnum.FIELD_IS_REQUIRED_X, "sessionKey"));
        }
        if (getTableDefinitionDto.getDatabaseName().isEmpty()) {
            throw ExplorerException.badRequestCustom(__(ExplorerMessageEnum.FIELD_IS_REQUIRED_X, "databaseName"));
        }
        if (getTableDefinitionDto.getTableName().isEmpty()) {
            throw ExplorerException.badRequestCustom(__(ExplorerMessageEnum.FIELD_IS_REQUIRED_X, "tableName"));
        }
    }
}
