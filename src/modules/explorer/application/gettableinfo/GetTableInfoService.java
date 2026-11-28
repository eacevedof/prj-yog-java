package modules.explorer.application.gettableinfo;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import modules.shared.application.Primitives;
import static modules.shared.application.Primitives.getTrimmedString;

import static core.config.Global.__;

import modules.history.application.recordqueryhistory.QueryHistoryTracer;

import modules.explorer.domain.enums.ExplorerMessageEnum;
import modules.explorer.infrastructure.factories.ExplorerReaderRepositoryFactory;
import modules.explorer.infrastructure.repositories.AbstractExplorerReaderRepository;
import modules.explorer.domain.exceptions.ExplorerException;

/**
 * La pestaña Info de una tabla o vista (feature 10): su DDL, sus indices y su tamaño.
 */
public final class GetTableInfoService {

    private GetTableInfoService() {
    }

    public static GetTableInfoService getInstance() {
        return new GetTableInfoService();
    }

    /**
     * @throws ExplorerException
     * @throws SQLException si la BD falla: sube tal cual hasta el controller
     * @throws modules.connections.domain.exceptions.ConnectionsException
     */
    public GetTableInfoResultDto invoke(GetTableInfoDto getTableInfoDto) throws SQLException {
        return QueryHistoryTracer.getInstance(getTableInfoDto.getConnectionUuid(), getTableInfoDto.getDatabaseName()).getTraced(() -> getResult(getTableInfoDto));
    }

    private GetTableInfoResultDto getResult(GetTableInfoDto getTableInfoDto) throws SQLException {
        failIfWrongInput(getTableInfoDto);

        String databaseName = getTableInfoDto.getDatabaseName();
        String tableName = getTableInfoDto.getTableName();
        AbstractExplorerReaderRepository explorerReaderRepository = ExplorerReaderRepositoryFactory.getInstance().getRepository(
            getTableInfoDto.getConnectionUuid(), getTableInfoDto.getSessionKey()
        );
        Map<String, Object> status = explorerReaderRepository.getTableStatus(databaseName, tableName);
        boolean isView = getTrimmedString(status.get("table_type")).contains("VIEW");
        return GetTableInfoResultDto.fromPrimitives(Primitives.of(
            "databaseName", databaseName,
            "tableName", tableName,
            "isView", isView,
            "createStatement", explorerReaderRepository.getCreateStatement(databaseName, tableName, isView),
            "indexes", isView ? List.of() : explorerReaderRepository.getIndexes(databaseName, tableName),
            "status", status
        ));
    }

    /**
     * @throws ExplorerException
     */
    private void failIfWrongInput(GetTableInfoDto getTableInfoDto) {
        if (getTableInfoDto.getConnectionUuid().isEmpty()) {
            throw ExplorerException.badRequestCustom(__(ExplorerMessageEnum.FIELD_IS_REQUIRED_X, "connectionUuid"));
        }
        if (getTableInfoDto.getSessionKey().isEmpty()) {
            throw ExplorerException.badRequestCustom(__(ExplorerMessageEnum.FIELD_IS_REQUIRED_X, "sessionKey"));
        }
        if (getTableInfoDto.getDatabaseName().isEmpty()) {
            throw ExplorerException.badRequestCustom(__(ExplorerMessageEnum.FIELD_IS_REQUIRED_X, "databaseName"));
        }
        if (getTableInfoDto.getTableName().isEmpty()) {
            throw ExplorerException.badRequestCustom(__(ExplorerMessageEnum.FIELD_IS_REQUIRED_X, "tableName"));
        }
    }
}
