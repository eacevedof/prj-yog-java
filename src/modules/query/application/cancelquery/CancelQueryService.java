package modules.query.application.cancelquery;

import java.sql.SQLException;

import modules.shared.application.Primitives;

import static core.config.Global.__;

import modules.query.domain.enums.QueryMessageEnum;
import modules.query.infrastructure.components.RunningStatementsComponent;
import modules.query.domain.exceptions.QueryException;

/**
 * Corta lo que se esta ejecutando en una pestaña (KILL QUERY en el servidor) y evita que "ejecutar todo" siga. La
 * ejecucion en curso termina con el error 1317 "Query execution was interrupted", que se ve en Mensajes.
 */
public final class CancelQueryService {

    private CancelQueryService() {
    }

    public static CancelQueryService getInstance() {
        return new CancelQueryService();
    }

    /**
     * @throws QueryException
     * @throws SQLException si la BD falla: sube tal cual hasta el controller
     */
    public CancelQueryResultDto invoke(CancelQueryDto cancelQueryDto) throws SQLException {
        failIfWrongInput(cancelQueryDto);

        boolean wasRunning = RunningStatementsComponent.getInstance().cancel(cancelQueryDto.getSessionKey());
        return CancelQueryResultDto.fromPrimitives(Primitives.of("wasRunning", wasRunning));
    }

    /**
     * @throws QueryException
     */
    private void failIfWrongInput(CancelQueryDto cancelQueryDto) {
        if (cancelQueryDto.getSessionKey().isEmpty()) {
            throw QueryException.badRequestCustom(__(QueryMessageEnum.FIELD_IS_REQUIRED_X, "sessionKey"));
        }
    }
}
