package modules.dbcopy.application.listcopyobjects;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import modules.shared.application.Primitives;

import static core.config.Global.__;

import modules.connections.domain.enums.DbEngineEnum;
import modules.connections.infrastructure.factories.TargetSessionFactory;

import modules.history.application.recordqueryhistory.QueryHistoryTracer;

import modules.explorer.domain.enums.ExplorerKeyEnum;
import modules.explorer.infrastructure.factories.ExplorerReaderRepositoryFactory;
import modules.explorer.infrastructure.repositories.AbstractExplorerReaderRepository;

import modules.dbcopy.domain.enums.DbCopyKeyEnum;
import modules.dbcopy.domain.enums.DbCopyMessageEnum;
import modules.dbcopy.infrastructure.repositories.DbCopyReaderMysqlRepository;
import modules.dbcopy.domain.exceptions.DbCopyException;

/**
 * Feature 29: lo que se puede copiar de una BD (tablas, vistas, procedimientos, funciones, triggers y eventos), para
 * el arbol con casillas del dialogo "Copiar BD".
 */
public final class ListCopyObjectsService {

    private ListCopyObjectsService() {
    }

    public static ListCopyObjectsService getInstance() {
        return new ListCopyObjectsService();
    }

    /**
     * @throws DbCopyException
     * @throws SQLException si la BD falla: sube tal cual hasta el controller
     * @throws modules.connections.domain.exceptions.ConnectionsException
     */
    public ListCopyObjectsResultDto invoke(ListCopyObjectsDto listCopyObjectsDto) throws SQLException {
        return QueryHistoryTracer.getInstance(listCopyObjectsDto.getConnectionUuid(), listCopyObjectsDto.getDatabaseName()).getTraced(() -> getResult(listCopyObjectsDto));
    }

    private ListCopyObjectsResultDto getResult(ListCopyObjectsDto listCopyObjectsDto) throws SQLException {
        failIfWrongInput(listCopyObjectsDto);

        TargetSessionFactory targetSessionFactory = TargetSessionFactory.getInstance();
        String dbEngine = targetSessionFactory.getDbEngine(listCopyObjectsDto.getConnectionUuid());
        if (!DbEngineEnum.isSql(dbEngine)) {
            throw DbCopyException.badRequestCustom(__(DbCopyMessageEnum.NOT_SQL_ENGINE_X_X, listCopyObjectsDto.getDatabaseName(), dbEngine));
        }
        if (!DbEngineEnum.MYSQL.equals(dbEngine)) return getObjectsOfOtherEngine(listCopyObjectsDto);
        DbCopyReaderMysqlRepository dbCopyReaderMysqlRepository = DbCopyReaderMysqlRepository.getInstance(
            targetSessionFactory.getSession(listCopyObjectsDto.getConnectionUuid(), listCopyObjectsDto.getSessionKey())
        );
        return ListCopyObjectsResultDto.fromPrimitives(dbCopyReaderMysqlRepository.getObjects(listCopyObjectsDto.getDatabaseName()));
    }

    /**
     * PostgreSQL, SQL Server y SQLite: lo que lista el arbol (tablas, vistas, rutinas y triggers; eventos no hay).
     * Entre motores solo se copian las tablas, pero se ve todo.
     */
    private ListCopyObjectsResultDto getObjectsOfOtherEngine(ListCopyObjectsDto listCopyObjectsDto) throws SQLException {
        AbstractExplorerReaderRepository explorerReaderRepository = ExplorerReaderRepositoryFactory.getInstance().getRepository(
            listCopyObjectsDto.getConnectionUuid(), listCopyObjectsDto.getSessionKey()
        );
        String databaseName = listCopyObjectsDto.getDatabaseName();
        Map<String, Object> tablesAndViews = explorerReaderRepository.getTablesAndViews(databaseName);
        Map<String, Object> routines = explorerReaderRepository.getRoutines(databaseName);
        return ListCopyObjectsResultDto.fromPrimitives(Primitives.of(
            DbCopyKeyEnum.TABLES, tablesAndViews.getOrDefault(ExplorerKeyEnum.TABLES, List.of()),
            DbCopyKeyEnum.VIEWS, tablesAndViews.getOrDefault(ExplorerKeyEnum.VIEWS, List.of()),
            DbCopyKeyEnum.PROCEDURES, routines.getOrDefault(ExplorerKeyEnum.PROCEDURES, List.of()),
            DbCopyKeyEnum.FUNCTIONS, routines.getOrDefault(ExplorerKeyEnum.FUNCTIONS, List.of()),
            DbCopyKeyEnum.TRIGGERS, explorerReaderRepository.getTriggerNames(databaseName),
            DbCopyKeyEnum.EVENTS, List.of(),
            DbCopyKeyEnum.TRIGGER_TABLES, Primitives.of()
        ));
    }

    /**
     * @throws DbCopyException
     */
    private void failIfWrongInput(ListCopyObjectsDto listCopyObjectsDto) {
        if (listCopyObjectsDto.getConnectionUuid().isEmpty()) {
            throw DbCopyException.badRequestCustom(__(DbCopyMessageEnum.FIELD_IS_REQUIRED_X, "connectionUuid"));
        }
        if (listCopyObjectsDto.getSessionKey().isEmpty()) {
            throw DbCopyException.badRequestCustom(__(DbCopyMessageEnum.FIELD_IS_REQUIRED_X, "sessionKey"));
        }
        if (listCopyObjectsDto.getDatabaseName().isEmpty()) {
            throw DbCopyException.badRequestCustom(__(DbCopyMessageEnum.FIELD_IS_REQUIRED_X, "databaseName"));
        }
    }
}
