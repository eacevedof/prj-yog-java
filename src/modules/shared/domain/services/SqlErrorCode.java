package modules.shared.domain.services;

import java.sql.SQLException;

/**
 * El codigo que se ensena de un error SQL: el numero de MySQL (1146) y, si el motor no da numero (PostgreSQL, SQLite),
 * su SQLSTATE (42P01).
 */
public final class SqlErrorCode {

    private SqlErrorCode() {
    }

    public static SqlErrorCode getInstance() {
        return new SqlErrorCode();
    }

    public String getCode(SQLException sqlException) {
        if (sqlException.getErrorCode() != 0) return String.valueOf(sqlException.getErrorCode());
        String sqlState = sqlException.getSQLState();
        return sqlState == null || sqlState.isBlank() ? "0" : sqlState;
    }
}
