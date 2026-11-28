package modules.connections.infrastructure.components.httptunnel;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import modules.shared.domain.services.SqlDialect;
import modules.shared.domain.services.SqlLiteralQuoter;

/**
 * Una sesion contra el MySQL a traves del tunel HTTP, presentada como java.sql.Connection: asi el arbol, el editor,
 * la pestaña Datos y el autocompletado funcionan sin saber que no hay JDBC detras. Solo se implementa lo que usa
 * multidbs; el resto lanza SQLFeatureNotSupportedException.
 * <p>
 * El tunel no guarda estado entre peticiones: la BD activa (MySQL y SQL Server) la recuerda esta sesion (USE y setCatalog) y va en cada
 * una. Sin transacciones entre peticiones: lo que necesita atomicidad va en un solo lote ({@link #executeBatch}).
 * Los ? de las sentencias preparadas se sustituyen por literales ({@link SqlLiteralQuoter}).
 */
public final class HttpTunnelSession {

    private static final Pattern USE_PATTERN = Pattern.compile("^\\s*USE\\s+(`(?:[^`]|``)+`|\\[(?:[^\\]]|\\]\\])+\\]|[^\\s;`\\[]+)\\s*;?\\s*$", Pattern.CASE_INSENSITIVE);

    private final HttpTunnelClient httpTunnelClient;
    private final SqlDialect sqlDialect;
    private volatile String currentDatabase;
    private volatile boolean isClosed;

    private HttpTunnelSession(HttpTunnelClient httpTunnelClient, String dbEngine, String initialDatabase) {
        this.httpTunnelClient = httpTunnelClient;
        this.sqlDialect = SqlDialect.getInstance(dbEngine);
        String database = initialDatabase == null ? "" : initialDatabase;
        // como por JDBC: una conexion de PostgreSQL sin BD va a "postgres"
        this.currentDatabase = database.isEmpty() && sqlDialect.isPgsql() ? "postgres" : database;
    }

    /**
     * La sesion como java.sql.Connection. unwrap(HttpTunnelSession.class) devuelve esta sesion. En PostgreSQL y SQLite
     * la BD que viaja en cada peticion es siempre la de la conexion: alli USE y el cambio de esquema no se recuerdan
     * (el tunel no guarda search_path entre peticiones), y el esquema activo es el de por defecto (public).
     */
    public static Connection getConnection(HttpTunnelClient httpTunnelClient, String dbEngine, String initialDatabase) {
        HttpTunnelSession httpTunnelSession = new HttpTunnelSession(httpTunnelClient, dbEngine, initialDatabase);
        return getProxy(Connection.class, httpTunnelSession.new ConnectionHandler());
    }

    public String getCurrentDatabase() {
        return currentDatabase;
    }

    /**
     * Varias sentencias en una sola peticion y en una sola conexion del tunel, en orden; si una falla, el tunel para
     * ahi y la conexion se cierra (un START TRANSACTION sin COMMIT se deshace solo). Devuelve el resultado de la ultima.
     */
    public HttpTunnelResult executeBatch(List<String> statements) throws SQLException {
        throwIfClosed();
        List<HttpTunnelResult> results = httpTunnelClient.execute(String.join(";\n", statements), currentDatabase, true);
        if (results.isEmpty()) throw new SQLException("tunnel: empty answer");
        return results.getLast();
    }

    private List<HttpTunnelResult> execute(String sql) throws SQLException {
        throwIfClosed();
        List<HttpTunnelResult> results = httpTunnelClient.execute(sql, currentDatabase, false);
        Matcher useMatcher = USE_PATTERN.matcher(sql);
        if (isDatabaseRemembered() && useMatcher.matches()) currentDatabase = getUnquoted(useMatcher.group(1));
        return results;
    }

    private void throwIfClosed() throws SQLException {
        if (isClosed) throw new SQLException("tunnel session is closed");
    }

    private String getUnquoted(String identifier) {
        if (identifier.length() >= 2 && identifier.startsWith("`") && identifier.endsWith("`")) {
            return identifier.substring(1, identifier.length() - 1).replace("``", "`");
        }
        if (identifier.length() >= 2 && identifier.startsWith("[") && identifier.endsWith("]")) {
            return identifier.substring(1, identifier.length() - 1).replace("]]", "]");
        }
        return identifier;
    }

    /**
     * MySQL y SQL Server: la BD activa (USE, setCatalog) se recuerda aqui y viaja en el &lt;db&gt; de cada peticion. En
     * PostgreSQL y SQLite la BD es siempre la de la conexion.
     */
    private boolean isDatabaseRemembered() {
        return sqlDialect.isMysql() || sqlDialect.isMssql();
    }

    @SuppressWarnings("unchecked")
    private static <T> T getProxy(Class<T> jdbcInterface, Object handler) {
        return (T) Proxy.newProxyInstance(
            HttpTunnelSession.class.getClassLoader(),
            new Class<?>[]{jdbcInterface},
            new DispatchingHandler(handler)
        );
    }

    /**
     * Pasa cada llamada al metodo publico del mismo nombre y parametros del handler; si no lo tiene, no esta soportado.
     */
    private record DispatchingHandler(Object handler) implements InvocationHandler {

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            switch (method.getName()) {
                case "toString" -> {
                    if (method.getParameterCount() == 0) return "HttpTunnel" + handler.getClass().getSimpleName();
                }
                case "hashCode" -> {
                    if (method.getParameterCount() == 0) return System.identityHashCode(proxy);
                }
                case "equals" -> {
                    if (method.getParameterCount() == 1) return proxy == args[0];
                }
                default -> {
                }
            }
            Method handlerMethod;
            try {
                handlerMethod = handler.getClass().getMethod(method.getName(), method.getParameterTypes());
            }
            catch (NoSuchMethodException e) {
                throw new SQLFeatureNotSupportedException("not supported over the HTTP tunnel: " + method.getName());
            }
            try {
                handlerMethod.setAccessible(true);
                return handlerMethod.invoke(handler, args);
            }
            catch (InvocationTargetException e) {
                throw e.getCause();
            }
        }
    }

    public final class ConnectionHandler {

        public Statement createStatement() {
            return getProxy(Statement.class, new StatementHandler(null));
        }

        public PreparedStatement prepareStatement(String sql) {
            return getProxy(PreparedStatement.class, new StatementHandler(sql));
        }

        public void setCatalog(String databaseName) {
            if (!isDatabaseRemembered()) return;
            currentDatabase = databaseName == null ? "" : databaseName;
        }

        public String getCatalog() {
            return currentDatabase;
        }

        /**
         * PostgreSQL por el tunel: cada peticion es una sesion nueva, asi que el search_path no se puede fijar.
         */
        public void setSchema(String schemaName) {
        }

        public String getSchema() {
            return isDatabaseRemembered() ? currentDatabase : "public";
        }

        public boolean getAutoCommit() {
            return true;
        }

        /**
         * Sin transacciones entre peticiones: lo atomico va por executeBatch.
         */
        public void setAutoCommit(boolean isAutoCommit) throws SQLException {
            if (!isAutoCommit) throw new SQLFeatureNotSupportedException("transactions span one request over the HTTP tunnel");
        }

        public void commit() {
        }

        public void rollback() {
        }

        public boolean isClosed() {
            return isClosed;
        }

        public boolean isValid(int timeoutSeconds) {
            return !isClosed;
        }

        public void close() {
            isClosed = true;
            httpTunnelClient.cancel();
        }

        public java.sql.SQLWarning getWarnings() {
            return null;
        }

        public void clearWarnings() {
        }

        public boolean isWrapperFor(Class<?> type) {
            return type == HttpTunnelSession.class;
        }

        public <T> T unwrap(Class<T> type) throws SQLException {
            if (type == HttpTunnelSession.class) return type.cast(HttpTunnelSession.this);
            throw new SQLException("not a wrapper for " + type.getName());
        }
    }

    public final class StatementHandler {

        private final String preparedSql;
        private final Map<Integer, Object> params = new TreeMap<>();
        private List<HttpTunnelResult> results = List.of();
        private int resultIndex;
        private int maxRows;
        private boolean isStatementClosed;

        StatementHandler(String preparedSql) {
            this.preparedSql = preparedSql;
        }

        public boolean execute(String sql) throws SQLException {
            run(sql);
            return hasCurrentResultSet();
        }

        public boolean execute() throws SQLException {
            return execute(getPreparedSqlWithParams());
        }

        public ResultSet executeQuery(String sql) throws SQLException {
            run(sql);
            ResultSet resultSet = getResultSet();
            if (resultSet == null) throw new SQLException("the statement returned no result set");
            return resultSet;
        }

        public ResultSet executeQuery() throws SQLException {
            return executeQuery(getPreparedSqlWithParams());
        }

        public int executeUpdate(String sql) throws SQLException {
            run(sql);
            return results.isEmpty() ? 0 : (int) results.getFirst().affectedRows();
        }

        public int executeUpdate() throws SQLException {
            return executeUpdate(getPreparedSqlWithParams());
        }

        public ResultSet getResultSet() {
            if (!hasCurrentResultSet()) return null;
            return getProxy(ResultSet.class, new ResultSetHandler(results.get(resultIndex), maxRows));
        }

        public int getUpdateCount() {
            if (resultIndex >= results.size() || results.get(resultIndex).hasResultSet()) return -1;
            return (int) results.get(resultIndex).affectedRows();
        }

        public boolean getMoreResults() {
            resultIndex++;
            return hasCurrentResultSet();
        }

        public void setMaxRows(int maxRows) {
            this.maxRows = Math.max(0, maxRows);
        }

        public int getMaxRows() {
            return maxRows;
        }

        public void setQueryTimeout(int seconds) {
        }

        public void setFetchSize(int rows) {
        }

        public void cancel() {
            httpTunnelClient.cancel();
        }

        public void close() {
            isStatementClosed = true;
        }

        public boolean isClosed() {
            return isStatementClosed;
        }

        public java.sql.SQLWarning getWarnings() {
            return null;
        }

        public void clearWarnings() {
        }

        public void setObject(int index, Object value) {
            params.put(index, value);
        }

        public void setObject(int index, Object value, int sqlType) {
            params.put(index, value);
        }

        public void setString(int index, String value) {
            params.put(index, value);
        }

        public void setInt(int index, int value) {
            params.put(index, value);
        }

        public void setLong(int index, long value) {
            params.put(index, value);
        }

        public void setBoolean(int index, boolean value) {
            params.put(index, value);
        }

        public void setBigDecimal(int index, BigDecimal value) {
            params.put(index, value);
        }

        public void setBytes(int index, byte[] value) {
            params.put(index, value);
        }

        public void setNull(int index, int sqlType) {
            params.put(index, null);
        }

        public void clearParameters() {
            params.clear();
        }

        private void run(String sql) throws SQLException {
            results = HttpTunnelSession.this.execute(sql);
            resultIndex = 0;
        }

        private boolean hasCurrentResultSet() {
            return resultIndex < results.size() && results.get(resultIndex).hasResultSet();
        }

        private String getPreparedSqlWithParams() throws SQLException {
            if (preparedSql == null) throw new SQLException("not a prepared statement");
            List<Object> orderedParams = new ArrayList<>(params.values());
            try {
                return SqlLiteralQuoter.getInstance(sqlDialect.getDbEngine()).getInlined(preparedSql, orderedParams);
            }
            catch (IllegalArgumentException e) {
                throw new SQLException(e.getMessage(), e);
            }
        }
    }

    public static final class ResultSetHandler {

        private final HttpTunnelResult result;
        private final List<List<byte[]>> rows;
        private final int[] sqlTypes;
        private int rowIndex = -1;
        private boolean wasNull;
        private boolean isResultSetClosed;

        ResultSetHandler(HttpTunnelResult result, int maxRows) {
            this.result = result;
            this.rows = maxRows > 0 && result.rows().size() > maxRows ? result.rows().subList(0, maxRows) : result.rows();
            this.sqlTypes = new int[result.columnNames().size()];
            for (int i = 0; i < sqlTypes.length; i++) {
                sqlTypes[i] = getSqlType(result.columnTypes().get(i), i);
            }
        }

        public boolean next() {
            if (rowIndex + 1 >= rows.size()) {
                rowIndex = rows.size();
                return false;
            }
            rowIndex++;
            return true;
        }

        public String getString(int columnIndex) throws SQLException {
            return HttpTunnelResult.getText(getCell(columnIndex));
        }

        public String getString(String columnLabel) throws SQLException {
            return getString(findColumn(columnLabel));
        }

        public byte[] getBytes(int columnIndex) throws SQLException {
            return getCell(columnIndex);
        }

        public byte[] getBytes(String columnLabel) throws SQLException {
            return getBytes(findColumn(columnLabel));
        }

        /**
         * Enteros como Long, binarios como byte[] y el resto como texto (lo que da el tunel).
         */
        public Object getObject(int columnIndex) throws SQLException {
            byte[] cell = getCell(columnIndex);
            if (cell == null) return null;
            int sqlType = sqlTypes[columnIndex - 1];
            if (sqlType == Types.BINARY || sqlType == Types.VARBINARY || sqlType == Types.LONGVARBINARY) return cell;
            String text = HttpTunnelResult.getText(cell);
            if (sqlType == Types.TINYINT || sqlType == Types.SMALLINT || sqlType == Types.INTEGER || sqlType == Types.BIGINT) {
                try {
                    return Long.parseLong(text);
                }
                catch (NumberFormatException e) {
                    return text;
                }
            }
            return text;
        }

        public Object getObject(String columnLabel) throws SQLException {
            return getObject(findColumn(columnLabel));
        }

        /**
         * PDO (mysqlnd) ya da un BIT en decimal ("5" para b'101'); si llegara en bytes, se leen en big-endian.
         */
        public long getLong(int columnIndex) throws SQLException {
            byte[] cell = getCell(columnIndex);
            if (cell == null) return 0;
            String text = HttpTunnelResult.getText(cell).trim();
            try {
                return new BigDecimal(text).longValue();
            }
            catch (NumberFormatException e) {
                if (sqlTypes[columnIndex - 1] != Types.BIT) throw new SQLException("not a number: " + text);
                long bits = 0;
                for (byte bitByte : cell) bits = (bits << 8) | (bitByte & 0xFF);
                return bits;
            }
        }

        public long getLong(String columnLabel) throws SQLException {
            return getLong(findColumn(columnLabel));
        }

        public int getInt(int columnIndex) throws SQLException {
            return (int) getLong(columnIndex);
        }

        public int getInt(String columnLabel) throws SQLException {
            return getInt(findColumn(columnLabel));
        }

        public boolean getBoolean(int columnIndex) throws SQLException {
            return getLong(columnIndex) != 0;
        }

        public boolean wasNull() {
            return wasNull;
        }

        public int findColumn(String columnLabel) throws SQLException {
            for (int i = 0; i < result.columnNames().size(); i++) {
                if (result.columnNames().get(i).equalsIgnoreCase(columnLabel)) return i + 1;
            }
            throw new SQLException("column not found: " + columnLabel);
        }

        public ResultSetMetaData getMetaData() {
            return getProxy(ResultSetMetaData.class, new MetaDataHandler(result, sqlTypes));
        }

        public void close() {
            isResultSetClosed = true;
        }

        public boolean isClosed() {
            return isResultSetClosed;
        }

        public java.sql.SQLWarning getWarnings() {
            return null;
        }

        private byte[] getCell(int columnIndex) throws SQLException {
            if (rowIndex < 0 || rowIndex >= rows.size()) throw new SQLException("no current row");
            List<byte[]> row = rows.get(rowIndex);
            if (columnIndex < 1 || columnIndex > sqlTypes.length) throw new SQLException("column index out of range: " + columnIndex);
            byte[] cell = columnIndex <= row.size() ? row.get(columnIndex - 1) : null;
            wasNull = cell == null;
            return cell;
        }

        /**
         * El tipo literal de SQLyog a java.sql.Types. PDO llama "blob" tambien a TEXT y "char"/"varchar" a BINARY y
         * VARBINARY: se decide por los datos, binario solo si algun valor no es UTF-8 valido.
         */
        private int getSqlType(String literalType, int columnIndex) {
            return switch (literalType) {
                case "tinyint" -> Types.TINYINT;
                case "shortint" -> Types.SMALLINT;
                case "int", "mediumint" -> Types.INTEGER;
                case "bigint" -> Types.BIGINT;
                case "float" -> Types.REAL;
                case "double" -> Types.DOUBLE;
                case "decimal", "newdecimal" -> Types.DECIMAL;
                case "bit" -> Types.BIT;
                case "timestamp", "datetime" -> Types.TIMESTAMP;
                case "date" -> Types.DATE;
                case "time" -> Types.TIME;
                case "year" -> Types.SMALLINT;
                case "json" -> Types.LONGVARCHAR;
                case "geometry" -> Types.BINARY;
                case "tinyblob", "mediumblob", "longblob", "blob" -> isUtf8Column(columnIndex) ? Types.LONGVARCHAR : Types.LONGVARBINARY;
                case "char", "enum", "set" -> isUtf8Column(columnIndex) ? Types.CHAR : Types.BINARY;
                default -> isUtf8Column(columnIndex) ? Types.VARCHAR : Types.VARBINARY;
            };
        }

        private boolean isUtf8Column(int columnIndex) {
            for (List<byte[]> row : rows) {
                byte[] cell = columnIndex < row.size() ? row.get(columnIndex) : null;
                if (cell != null && !isUtf8(cell)) return false;
            }
            return true;
        }

        private static boolean isUtf8(byte[] bytes) {
            try {
                StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes));
                return true;
            }
            catch (CharacterCodingException e) {
                return false;
            }
        }
    }

    public static final class MetaDataHandler {

        private final HttpTunnelResult result;
        private final int[] sqlTypes;

        MetaDataHandler(HttpTunnelResult result, int[] sqlTypes) {
            this.result = result;
            this.sqlTypes = sqlTypes;
        }

        public int getColumnCount() {
            return sqlTypes.length;
        }

        public String getColumnLabel(int columnIndex) {
            return result.columnNames().get(columnIndex - 1);
        }

        public String getColumnName(int columnIndex) {
            return getColumnLabel(columnIndex);
        }

        public int getColumnType(int columnIndex) {
            return sqlTypes[columnIndex - 1];
        }

        public String getColumnTypeName(int columnIndex) {
            return result.columnTypes().get(columnIndex - 1).toUpperCase(Locale.ROOT);
        }

        public int isNullable(int columnIndex) {
            return ResultSetMetaData.columnNullableUnknown;
        }
    }
}
