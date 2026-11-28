package modules.explorer.application.listdatabaseobjects;

import java.sql.SQLException;

import modules.shared.application.Primitives;

import static core.config.Global.__;

import modules.history.application.recordqueryhistory.QueryHistoryTracer;

import modules.explorer.domain.enums.ExplorerKeyEnum;
import modules.explorer.domain.enums.ExplorerMessageEnum;
import modules.explorer.infrastructure.factories.ExplorerReaderRepositoryFactory;
import modules.explorer.infrastructure.repositories.AbstractExplorerReaderRepository;
import modules.explorer.domain.exceptions.ExplorerException;

/**
 * Lo que cuelga de una BD en el arbol: tablas, vistas, procedimientos, funciones y triggers. Se pide al desplegarla.
 */
public final class ListDatabaseObjectsService {

    private ListDatabaseObjectsService() {
    }

    public static ListDatabaseObjectsService getInstance() {
        return new ListDatabaseObjectsService();
    }

    /**
     * @throws ExplorerException
     * @throws SQLException si la BD falla: sube tal cual hasta el controller
     * @throws modules.connections.domain.exceptions.ConnectionsException
     */
    public ListDatabaseObjectsResultDto invoke(ListDatabaseObjectsDto listDatabaseObjectsDto) throws SQLException {
        return QueryHistoryTracer.getInstance(listDatabaseObjectsDto.getConnectionUuid(), listDatabaseObjectsDto.getDatabaseName()).getTraced(() -> getResult(listDatabaseObjectsDto));
    }

    private ListDatabaseObjectsResultDto getResult(ListDatabaseObjectsDto listDatabaseObjectsDto) throws SQLException {
        failIfWrongInput(listDatabaseObjectsDto);

        String databaseName = listDatabaseObjectsDto.getDatabaseName();
        AbstractExplorerReaderRepository explorerReaderRepository = ExplorerReaderRepositoryFactory.getInstance().getRepository(
            listDatabaseObjectsDto.getConnectionUuid(), listDatabaseObjectsDto.getSessionKey()
        );
        return ListDatabaseObjectsResultDto.fromPrimitives(Primitives.merge(
            explorerReaderRepository.getTablesAndViews(databaseName),
            explorerReaderRepository.getRoutines(databaseName),
            Primitives.of(ExplorerKeyEnum.TRIGGERS, explorerReaderRepository.getTriggerNames(databaseName))
        ));
    }

    /**
     * @throws ExplorerException
     */
    private void failIfWrongInput(ListDatabaseObjectsDto listDatabaseObjectsDto) {
        if (listDatabaseObjectsDto.getConnectionUuid().isEmpty()) {
            throw ExplorerException.badRequestCustom(__(ExplorerMessageEnum.FIELD_IS_REQUIRED_X, "connectionUuid"));
        }
        if (listDatabaseObjectsDto.getSessionKey().isEmpty()) {
            throw ExplorerException.badRequestCustom(__(ExplorerMessageEnum.FIELD_IS_REQUIRED_X, "sessionKey"));
        }
        if (listDatabaseObjectsDto.getDatabaseName().isEmpty()) {
            throw ExplorerException.badRequestCustom(__(ExplorerMessageEnum.FIELD_IS_REQUIRED_X, "databaseName"));
        }
    }
}
