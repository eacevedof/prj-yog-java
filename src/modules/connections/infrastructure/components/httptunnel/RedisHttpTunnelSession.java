package modules.connections.infrastructure.components.httptunnel;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.List;

/**
 * Redis por el tunel PHP (.../redis; contrato en el vault: sqlyog-tunnel-multidbs §4). Sin estado: cada peticion abre
 * su conexion, hace AUTH y SELECT del &lt;db&gt;, asi que el indice de BD de la sesion (un SELECT n de la consola) se
 * recuerda aqui y viaja en cada peticion. Cada comando va como una linea de redis-cli con todos sus argumentos entre
 * comillas dobles; varios comandos, en un lote (uno por linea, un &lt;result&gt; por comando).
 */
public final class RedisHttpTunnelSession {

    private final HttpTunnelClient httpTunnelClient;
    private volatile String currentDatabase;

    private RedisHttpTunnelSession(HttpTunnelClient httpTunnelClient, String initialDatabase) {
        this.httpTunnelClient = httpTunnelClient;
        this.currentDatabase = initialDatabase == null || initialDatabase.isBlank() ? "0" : initialDatabase.trim();
    }

    public static RedisHttpTunnelSession getInstance(HttpTunnelClient httpTunnelClient, String initialDatabase) {
        return new RedisHttpTunnelSession(httpTunnelClient, initialDatabase);
    }

    public String getCurrentDatabase() {
        return currentDatabase;
    }

    /**
     * SELECT n de la consola: el tunel no guarda la sesion, asi que solo cambia el &lt;db&gt; de las siguientes.
     */
    public void setCurrentDatabase(String databaseIndex) {
        this.currentDatabase = databaseIndex;
    }

    /**
     * Un comando (sus argumentos, el primero es el nombre): su &lt;result&gt;.
     * @throws SQLException con el texto de Redis (codigo 0), 1064 si el tunel no entiende la linea o 2003 si el
     *                      destino no esta permitido
     */
    public HttpTunnelResult execute(List<byte[]> arguments) throws SQLException {
        return httpTunnelClient.execute(getCommandLine(arguments), currentDatabase, false).getFirst();
    }

    /**
     * Varios comandos en una peticion: un resultado por comando, en orden.
     * @throws SQLException si alguno falla (el tunel para ahi)
     */
    public List<HttpTunnelResult> executeBatch(List<List<byte[]>> commands) throws SQLException {
        if (commands.isEmpty()) return List.of();
        if (commands.size() == 1) return List.of(execute(commands.getFirst()));
        StringBuilder script = new StringBuilder();
        for (List<byte[]> command : commands) {
            script.append(getCommandLine(command)).append('\n');
        }
        return httpTunnelClient.execute(script.toString(), currentDatabase, true);
    }

    /**
     * La linea de redis-cli: cada argumento entre comillas dobles con \" \\ \n \r \t y \xHH (lo que no es UTF-8 o es
     * un control); asi un argumento con espacios, comillas o bytes binarios llega igual.
     */
    public static String getCommandLine(List<byte[]> arguments) {
        StringBuilder line = new StringBuilder();
        for (byte[] argument : arguments) {
            if (!line.isEmpty()) line.append(' ');
            line.append(getQuoted(argument));
        }
        return line.toString();
    }

    private static String getQuoted(byte[] argument) {
        boolean isUtf8 = isUtf8(argument);
        StringBuilder quoted = new StringBuilder("\"");
        for (byte value : argument) {
            int unsigned = value & 0xFF;
            switch (unsigned) {
                case '"' -> quoted.append("\\\"");
                case '\\' -> quoted.append("\\\\");
                case '\n' -> quoted.append("\\n");
                case '\r' -> quoted.append("\\r");
                case '\t' -> quoted.append("\\t");
                default -> {
                    if (unsigned < 0x20 || unsigned == 0x7F || (unsigned >= 0x80 && !isUtf8)) quoted.append(String.format("\\x%02x", unsigned));
                    else quoted.append((char) unsigned);
                }
            }
        }
        // los bytes UTF-8 se han puesto uno a uno como char: se recomponen en su texto
        String quotedLatin = quoted.append('"').toString();
        return isUtf8 ? new String(quotedLatin.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8) : quotedLatin;
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
