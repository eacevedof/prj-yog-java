package modules.explorer.application.listdatabases;

import java.sql.SQLException;

import modules.shared.application.Primitives;

import static core.config.Global.__;

import modules.history.application.recordqueryhistory.QueryHistoryTracer;

import modules.explorer.domain.enums.ExplorerMessageEnum;
import modules.explorer.infrastructure.factories.ExplorerReaderRepositoryFactory;
import modules.explorer.infrastructure.repositories.AbstractExplorerReaderRepository;
import modules.explorer.domain.exceptions.ExplorerException;

/**
 * Las BD del servidor que ve el usuario: el primer nivel del arbol. Hace red: la UI lo llama fuera del hilo de JavaFX.
 */
public final class ListDatabasesService {

    private ListDatabasesService() {
    }

    public static ListDatabasesService getInstance() {
        return new ListDatabasesService();
    }

    /**
     * @throws ExplorerException
     * @throws SQLException si la BD falla: sube tal cual hasta el controller
     * @throws modules.connections.domain.exceptions.ConnectionsException
     */
    public ListDatabasesResultDto invoke(ListDatabasesDto listDatabasesDto) throws SQLException {
        return QueryHistoryTracer.getInstance(listDatabasesDto.getConnectionUuid(), "").getTraced(() -> getResult(listDatabasesDto));
    }

    private ListDatabasesResultDto getResult(ListDatabasesDto listDatabasesDto) throws SQLException {
        failIfWrongInput(listDatabasesDto);

        AbstractExplorerReaderRepository explorerReaderRepository = ExplorerReaderRepositoryFactory.getInstance().getRepository(
            listDatabasesDto.getConnectionUuid(), listDatabasesDto.getSessionKey()
        );
        return ListDatabasesResultDto.fromPrimitives(Primitives.of(
            "databaseNames", explorerReaderRepository.getDatabaseNames()
        ));
    }

    /**
     * @throws ExplorerException
     */
    private void failIfWrongInput(ListDatabasesDto listDatabasesDto) {
        if (listDatabasesDto.getConnectionUuid().isEmpty()) {
            throw ExplorerException.badRequestCustom(__(ExplorerMessageEnum.FIELD_IS_REQUIRED_X, "connectionUuid"));
        }
        if (listDatabasesDto.getSessionKey().isEmpty()) {
            throw ExplorerException.badRequestCustom(__(ExplorerMessageEnum.FIELD_IS_REQUIRED_X, "sessionKey"));
        }
    }
}
