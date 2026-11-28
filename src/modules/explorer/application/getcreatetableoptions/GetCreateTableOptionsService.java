package modules.explorer.application.getcreatetableoptions;

import java.sql.SQLException;
import java.util.List;

import modules.shared.domain.services.SqlDialect;
import modules.shared.application.Primitives;

import static core.config.Global.__;

import modules.connections.infrastructure.factories.TargetSessionFactory;

import modules.history.application.recordqueryhistory.QueryHistoryTracer;

import modules.explorer.domain.enums.ExplorerMessageEnum;
import modules.explorer.domain.services.TableCreateSqlBuilder;
import modules.explorer.infrastructure.repositories.TableDefinitionReaderMysqlRepository;
import modules.explorer.domain.exceptions.ExplorerException;

/**
 * Lo que abre el editor de tablas en modo "Create Table": el motor (el dialogo esconde lo que es solo de MySQL) y, en
 * MySQL/MariaDB, los motores de tabla y cotejamientos del servidor para Avanzado.
 */
public final class GetCreateTableOptionsService {

    private final TargetSessionFactory targetSessionFactory;
    private GetCreateTableOptionsDto getCreateTableOptionsDto;

    private GetCreateTableOptionsService() {
        this.targetSessionFactory = TargetSessionFactory.getInstance();
    }

    public static GetCreateTableOptionsService getInstance() {
        return new GetCreateTableOptionsService();
    }

    /**
     * @throws ExplorerException
     * @throws SQLException si la BD falla: sube tal cual hasta el controller
     * @throws modules.connections.domain.exceptions.ConnectionsException
     */
    public GetCreateTableOptionsResultDto invoke(GetCreateTableOptionsDto getCreateTableOptionsDto) throws SQLException {
        this.getCreateTableOptionsDto = getCreateTableOptionsDto;
        failIfWrongInput();
        String dbEngine = targetSessionFactory.getDbEngine(getCreateTableOptionsDto.getConnectionUuid());
        failIfNotSupported(dbEngine);
        if (!SqlDialect.MYSQL.equals(dbEngine)) {
            return GetCreateTableOptionsResultDto.fromPrimitives(Primitives.of("dbEngine", dbEngine, "engines", List.of(), "collations", List.of()));
        }
        return QueryHistoryTracer.getInstance(getCreateTableOptionsDto.getConnectionUuid(), getCreateTableOptionsDto.getDatabaseName()).getTraced(() -> {
            TableDefinitionReaderMysqlRepository tableDefinitionReaderMysqlRepository = TableDefinitionReaderMysqlRepository.getInstance(
                targetSessionFactory.getSession(getCreateTableOptionsDto.getConnectionUuid(), getCreateTableOptionsDto.getSessionKey())
            );
            return GetCreateTableOptionsResultDto.fromPrimitives(Primitives.of(
                "dbEngine", dbEngine,
                "engines", tableDefinitionReaderMysqlRepository.getEngines(),
                "collations", tableDefinitionReaderMysqlRepository.getCollations()
            ));
        });
    }

    /**
     * @throws ExplorerException
     */
    private void failIfWrongInput() {
        if (getCreateTableOptionsDto.getConnectionUuid().isEmpty()) {
            throw ExplorerException.badRequestCustom(__(ExplorerMessageEnum.FIELD_IS_REQUIRED_X, "connectionUuid"));
        }
        if (getCreateTableOptionsDto.getSessionKey().isEmpty()) {
            throw ExplorerException.badRequestCustom(__(ExplorerMessageEnum.FIELD_IS_REQUIRED_X, "sessionKey"));
        }
        if (getCreateTableOptionsDto.getDatabaseName().isEmpty()) {
            throw ExplorerException.badRequestCustom(__(ExplorerMessageEnum.FIELD_IS_REQUIRED_X, "databaseName"));
        }
    }

    /**
     * @throws ExplorerException si el motor no tiene tablas (Redis, MongoDB)
     */
    private void failIfNotSupported(String dbEngine) {
        if (TableCreateSqlBuilder.getInstance(dbEngine).isSupported()) return;
        throw ExplorerException.badRequestCustom(__(ExplorerMessageEnum.CREATE_TABLE_NOT_SUPPORTED_X, dbEngine));
    }
}
