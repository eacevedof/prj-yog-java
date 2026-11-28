package modules.explorer.application.listtablecolumns;

import java.sql.SQLException;

import modules.shared.application.Primitives;

import static core.config.Global.__;

import modules.history.application.recordqueryhistory.QueryHistoryTracer;

import modules.explorer.domain.enums.ExplorerMessageEnum;
import modules.explorer.infrastructure.factories.ExplorerReaderRepositoryFactory;
import modules.explorer.infrastructure.repositories.AbstractExplorerReaderRepository;
import modules.explorer.domain.exceptions.ExplorerException;

/**
 * Las columnas de una tabla o vista: lo que se ve al desplegarla en el arbol.
 */
public final class ListTableColumnsService {

    private ListTableColumnsService() {
    }

    public static ListTableColumnsService getInstance() {
        return new ListTableColumnsService();
    }

    /**
     * @throws ExplorerException
     * @throws SQLException si la BD falla: sube tal cual hasta el controller
     * @throws modules.connections.domain.exceptions.ConnectionsException
     */
    public ListTableColumnsResultDto invoke(ListTableColumnsDto listTableColumnsDto) throws SQLException {
        return QueryHistoryTracer.getInstance(listTableColumnsDto.getConnectionUuid(), listTableColumnsDto.getDatabaseName()).getTraced(() -> getResult(listTableColumnsDto));
    }

    private ListTableColumnsResultDto getResult(ListTableColumnsDto listTableColumnsDto) throws SQLException {
        failIfWrongInput(listTableColumnsDto);

        String databaseName = listTableColumnsDto.getDatabaseName();
        String tableName = listTableColumnsDto.getTableName();
        AbstractExplorerReaderRepository explorerReaderRepository = ExplorerReaderRepositoryFactory.getInstance().getRepository(
            listTableColumnsDto.getConnectionUuid(), listTableColumnsDto.getSessionKey()
        );
        return ListTableColumnsResultDto.fromPrimitives(Primitives.of(
            "columns", explorerReaderRepository.getTableColumns(databaseName, tableName)
        ));
    }

    /**
     * @throws ExplorerException
     */
    private void failIfWrongInput(ListTableColumnsDto listTableColumnsDto) {
        if (listTableColumnsDto.getConnectionUuid().isEmpty()) {
            throw ExplorerException.badRequestCustom(__(ExplorerMessageEnum.FIELD_IS_REQUIRED_X, "connectionUuid"));
        }
        if (listTableColumnsDto.getSessionKey().isEmpty()) {
            throw ExplorerException.badRequestCustom(__(ExplorerMessageEnum.FIELD_IS_REQUIRED_X, "sessionKey"));
        }
        if (listTableColumnsDto.getDatabaseName().isEmpty()) {
            throw ExplorerException.badRequestCustom(__(ExplorerMessageEnum.FIELD_IS_REQUIRED_X, "databaseName"));
        }
        if (listTableColumnsDto.getTableName().isEmpty()) {
            throw ExplorerException.badRequestCustom(__(ExplorerMessageEnum.FIELD_IS_REQUIRED_X, "tableName"));
        }
    }
}
