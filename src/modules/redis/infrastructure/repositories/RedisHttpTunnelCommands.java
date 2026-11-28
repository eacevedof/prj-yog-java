package modules.redis.infrastructure.repositories;

import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.util.ArrayList;
import java.util.List;

import org.bson.BsonArray;
import org.bson.BsonValue;

import redis.clients.jedis.exceptions.JedisConnectionException;
import redis.clients.jedis.exceptions.JedisDataException;
import redis.clients.jedis.exceptions.JedisException;

import modules.connections.infrastructure.components.httptunnel.HttpTunnelResult;
import modules.connections.infrastructure.components.httptunnel.RedisHttpTunnelSession;

/**
 * Lo comun a los repositorios de Redis por el tunel HTTP: mandar comandos y leer el bloque (columna "value" y el tipo
 * de respuesta en &lt;t&gt;: string, integer, nil, array; contrato sqlyog-tunnel-multidbs §4).
 * <p>
 * EXCEPCION DOCUMENTADA a «los repositorios no capturan» (DDD 18): el SQLException del tunel se traduce aqui a la
 * familia de Jedis, para que los servicios de Redis traten igual las dos vias sin cambiar (un error de Redis o del
 * comando = JedisDataException; red, HTTP, tiempo o destino no permitido = JedisConnectionException).
 */
final class RedisHttpTunnelCommands {

    private static final String TYPE_INTEGER = "integer";
    private static final String TYPE_NIL = "nil";
    private static final String TYPE_ARRAY = "array";
    // 2003: destino fuera de YOG_ALLOWED_HOSTS o sin conexion con Redis
    private static final int CONNECTION_ERROR_CODE = 2003;

    private final RedisHttpTunnelSession redisHttpTunnelSession;

    private RedisHttpTunnelCommands(RedisHttpTunnelSession redisHttpTunnelSession) {
        this.redisHttpTunnelSession = redisHttpTunnelSession;
    }

    static RedisHttpTunnelCommands getInstance(RedisHttpTunnelSession redisHttpTunnelSession) {
        return new RedisHttpTunnelCommands(redisHttpTunnelSession);
    }

    RedisHttpTunnelSession getSession() {
        return redisHttpTunnelSession;
    }

    /**
     * Un comando: su respuesta como la da Jedis (byte[], Long, List de byte[] o null).
     */
    Object getReply(String... arguments) {
        return getReply(getResult(getArguments(arguments)));
    }

    Object getRawReply(List<byte[]> arguments) {
        return getReply(getResult(arguments));
    }

    /**
     * Varios comandos en una peticion: una respuesta por comando, en orden.
     */
    List<Object> getReplies(List<List<String>> commands) {
        List<List<byte[]>> rawCommands = new ArrayList<>();
        for (List<String> command : commands) rawCommands.add(getArguments(command.toArray(String[]::new)));
        List<HttpTunnelResult> results;
        try {
            results = redisHttpTunnelSession.executeBatch(rawCommands);
        }
        catch (SQLException e) {
            throw getJedisException(e);
        }
        List<Object> replies = new ArrayList<>();
        for (HttpTunnelResult result : results) replies.add(getReply(result));
        return replies;
    }

    String getText(Object reply) {
        if (reply == null) return null;
        if (reply instanceof byte[] bytes) return new String(bytes, StandardCharsets.UTF_8);
        return String.valueOf(reply);
    }

    long getLong(Object reply) {
        if (reply instanceof Long number) return number;
        String text = getText(reply);
        if ("OK".equals(text)) return 1;
        return text != null && text.matches("-?\\d+") ? Long.parseLong(text) : 0;
    }

    /**
     * Los elementos de una respuesta array como texto (un elemento anidado llega como JSON).
     */
    List<String> getTexts(Object reply) {
        List<String> texts = new ArrayList<>();
        if (reply instanceof List<?> items) {
            for (Object item : items) texts.add(getText(item));
        }
        return texts;
    }

    /**
     * Un elemento anidado (el array de claves de SCAN, una entrada de XRANGE): JSON a lista; lo anidado dentro, como
     * su JSON.
     */
    List<String> getJsonTexts(String json) {
        List<String> texts = new ArrayList<>();
        if (json == null || json.isBlank()) return texts;
        for (BsonValue value : BsonArray.parse(json)) {
            texts.add(value.isString() ? value.asString().getValue() : value.isArray() ? getJson(value.asArray()) : value.toString());
        }
        return texts;
    }

    private String getJson(BsonArray bsonArray) {
        List<String> items = new ArrayList<>();
        for (BsonValue value : bsonArray) items.add(value.isString() ? "\"" + value.asString().getValue().replace("\\", "\\\\").replace("\"", "\\\"") + "\"" : value.isArray() ? getJson(value.asArray()) : value.toString());
        return "[" + String.join(",", items) + "]";
    }

    private HttpTunnelResult getResult(List<byte[]> arguments) {
        try {
            return redisHttpTunnelSession.execute(arguments);
        }
        catch (SQLException e) {
            throw getJedisException(e);
        }
    }

    private Object getReply(HttpTunnelResult result) {
        String replyType = result.columnTables().isEmpty() ? "" : result.columnTables().getFirst();
        List<List<byte[]>> rows = result.rows();
        return switch (replyType) {
            case TYPE_NIL -> null;
            case TYPE_INTEGER -> rows.isEmpty() || rows.getFirst().isEmpty() ? 0L : getInteger(rows.getFirst().getFirst());
            case TYPE_ARRAY -> {
                List<Object> items = new ArrayList<>();
                for (List<byte[]> row : rows) items.add(row.isEmpty() ? null : row.getFirst());
                yield items;
            }
            default -> rows.isEmpty() || rows.getFirst().isEmpty() ? null : rows.getFirst().getFirst();
        };
    }

    /**
     * Un integer que no es un numero es una respuesta rota del tunel: como un fallo de protocolo de Jedis
     * (JedisConnectionException), no un NumberFormatException que se salte el mapeo de errores de los servicios.
     */
    private long getInteger(byte[] value) {
        String text = value == null ? "" : HttpTunnelResult.getText(value).trim();
        try {
            return Long.parseLong(text);
        }
        catch (NumberFormatException e) {
            throw new JedisConnectionException("tunnel: unexpected integer reply: " + text, e);
        }
    }

    private List<byte[]> getArguments(String... arguments) {
        List<byte[]> rawArguments = new ArrayList<>();
        for (String argument : arguments) rawArguments.add(argument.getBytes(StandardCharsets.UTF_8));
        return rawArguments;
    }

    private JedisException getJedisException(SQLException e) {
        if (e instanceof SQLTimeoutException || e.getErrorCode() == CONNECTION_ERROR_CODE || e.getMessage() == null || e.getMessage().startsWith("tunnel:")) {
            return new JedisConnectionException(e.getMessage(), e);
        }
        return new JedisDataException(e.getMessage());
    }
}
