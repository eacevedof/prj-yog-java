package modules.explorer.application.getcreatedatabaseoptions;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import modules.shared.domain.services.SqlDialect;
import modules.shared.application.Primitives;

import static core.config.Global.__;

import modules.connections.infrastructure.factories.TargetSessionFactory;

import modules.history.application.recordqueryhistory.QueryHistoryTracer;

import modules.explorer.domain.enums.ExplorerKeyEnum;
import modules.explorer.domain.enums.ExplorerMessageEnum;
import modules.explorer.domain.services.DatabaseCreateSqlBuilder;
import modules.explorer.infrastructure.repositories.DatabaseOptionsReaderMssqlRepository;
import modules.explorer.infrastructure.repositories.DatabaseOptionsReaderMysqlRepository;
import modules.explorer.infrastructure.repositories.DatabaseOptionsReaderPgsqlRepository;
import modules.explorer.domain.exceptions.ExplorerException;

/**
 * Lo que abre el "Crear BD": las opciones que acepta el CREATE del motor de la conexion (MySQL/MariaDB, PostgreSQL o
 * SQL Server).
 */
public final class GetCreateDatabaseOptionsService {

    private static final String COLUMN_DEFAULT_ENCRYPTION = "DEFAULT_ENCRYPTION";
    private static final String COLUMN_SCHEMA_COMMENT = "SCHEMA_COMMENT";

    private GetCreateDatabaseOptionsService() {
    }

    public static GetCreateDatabaseOptionsService getInstance() {
        return new GetCreateDatabaseOptionsService();
    }

    /**
     * @throws ExplorerException si falta un campo o el motor no crea BDs
     * @throws SQLException si la BD falla: sube tal cual hasta el controller
     * @throws modules.connections.domain.exceptions.ConnectionsException
     */
    public GetCreateDatabaseOptionsResultDto invoke(GetCreateDatabaseOptionsDto getCreateDatabaseOptionsDto) throws SQLException {
        return QueryHistoryTracer.getInstance(getCreateDatabaseOptionsDto.getConnectionUuid(), "").getTraced(() -> getResult(getCreateDatabaseOptionsDto));
    }

    private GetCreateDatabaseOptionsResultDto getResult(GetCreateDatabaseOptionsDto getCreateDatabaseOptionsDto) throws SQLException {
        failIfWrongInput(getCreateDatabaseOptionsDto);
        TargetSessionFactory targetSessionFactory = TargetSessionFactory.getInstance();
        String dbEngine = targetSessionFactory.getDbEngine(getCreateDatabaseOptionsDto.getConnectionUuid());
        if (!DatabaseCreateSqlBuilder.getInstance(dbEngine).isSupported()) {
            throw ExplorerException.badRequestCustom(__(ExplorerMessageEnum.CREATE_DATABASE_NOT_SUPPORTED_X, dbEngine));
        }
        Connection pdo = targetSessionFactory.getSession(getCreateDatabaseOptionsDto.getConnectionUuid(), getCreateDatabaseOptionsDto.getSessionKey());
        return switch (dbEngine) {
            case SqlDialect.MYSQL -> getMysqlResult(pdo);
            case SqlDialect.PGSQL -> getPgsqlResult(pdo);
            default -> getMssqlResult(pdo);
        };
    }

    private GetCreateDatabaseOptionsResultDto getMysqlResult(Connection pdo) throws SQLException {
        DatabaseOptionsReaderMysqlRepository databaseOptionsReaderMysqlRepository = DatabaseOptionsReaderMysqlRepository.getInstance(pdo);
        Map<String, Object> serverDefaults = databaseOptionsReaderMysqlRepository.getServerDefaults();
        List<String> schemataColumnNames = databaseOptionsReaderMysqlRepository.getSchemataColumnNames();
        return GetCreateDatabaseOptionsResultDto.fromPrimitives(Primitives.of(
            "dbEngine", SqlDialect.MYSQL,
            "defaultCharacterSet", serverDefaults.get(ExplorerKeyEnum.CHARACTER_SET),
            "defaultCollation", serverDefaults.get(ExplorerKeyEnum.COLLATION),
            "collationsByCharacterSet", databaseOptionsReaderMysqlRepository.getCollationsByCharacterSet(),
            "isEncryptionSupported", schemataColumnNames.contains(COLUMN_DEFAULT_ENCRYPTION),
            "isCommentSupported", schemataColumnNames.contains(COLUMN_SCHEMA_COMMENT)
        ));
    }

    private GetCreateDatabaseOptionsResultDto getPgsqlResult(Connection pdo) throws SQLException {
        DatabaseOptionsReaderPgsqlRepository databaseOptionsReaderPgsqlRepository = DatabaseOptionsReaderPgsqlRepository.getInstance(pdo);
        return GetCreateDatabaseOptionsResultDto.fromPrimitives(Primitives.of(
            "dbEngine", SqlDialect.PGSQL,
            "owners", databaseOptionsReaderPgsqlRepository.getRoleNames(),
            "defaultOwner", databaseOptionsReaderPgsqlRepository.getCurrentRole()
        ));
    }

    private GetCreateDatabaseOptionsResultDto getMssqlResult(Connection pdo) throws SQLException {
        DatabaseOptionsReaderMssqlRepository databaseOptionsReaderMssqlRepository = DatabaseOptionsReaderMssqlRepository.getInstance(pdo);
        return GetCreateDatabaseOptionsResultDto.fromPrimitives(Primitives.of(
            "dbEngine", SqlDialect.MSSQL,
            "defaultCollation", databaseOptionsReaderMssqlRepository.getServerCollation(),
            "collations", databaseOptionsReaderMssqlRepository.getCollationNames()
        ));
    }

    /**
     * @throws ExplorerException
     */
    private void failIfWrongInput(GetCreateDatabaseOptionsDto getCreateDatabaseOptionsDto) {
        if (getCreateDatabaseOptionsDto.getConnectionUuid().isEmpty()) {
            throw ExplorerException.badRequestCustom(__(ExplorerMessageEnum.FIELD_IS_REQUIRED_X, "connectionUuid"));
        }
        if (getCreateDatabaseOptionsDto.getSessionKey().isEmpty()) {
            throw ExplorerException.badRequestCustom(__(ExplorerMessageEnum.FIELD_IS_REQUIRED_X, "sessionKey"));
        }
    }
}
