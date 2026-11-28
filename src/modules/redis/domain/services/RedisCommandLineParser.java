package modules.redis.domain.services;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Parte una linea de la consola en argumentos como redis-cli: separados por espacios; "..." admite escapes (\n, \t,
 * \", \\ y \xHH, que es un byte tal cual) y '...' es literal (con \' para la comilla). Los argumentos son bytes: el
 * texto va en UTF-8 y un \xHH es ese byte, asi se puede guardar un valor binario.
 */
public final class RedisCommandLineParser {

    private RedisCommandLineParser() {
    }

    public static RedisCommandLineParser getInstance() {
        return new RedisCommandLineParser();
    }

    /**
     * Los argumentos como texto (para mirar el comando y sus subcomandos).
     * @throws IllegalArgumentException si una comilla queda sin cerrar
     */
    public List<String> getArguments(String commandLine) {
        return getRawArguments(commandLine).stream().map(bytes -> new String(bytes, StandardCharsets.UTF_8)).toList();
    }

    /**
     * Los argumentos como bytes, para mandarlos a Redis.
     * @throws IllegalArgumentException si una comilla queda sin cerrar
     */
    public List<byte[]> getRawArguments(String commandLine) {
        List<byte[]> arguments = new ArrayList<>();
        int length = commandLine.length();
        int i = 0;
        while (i < length) {
            while (i < length && Character.isWhitespace(commandLine.charAt(i))) i++;
            if (i >= length) break;
            ByteArrayOutputStream argument = new ByteArrayOutputStream();
            char quote = commandLine.charAt(i);
            if (quote == '"' || quote == '\'') {
                i = readQuoted(commandLine, i, quote, argument);
            }
            else {
                int start = i;
                while (i < length && !Character.isWhitespace(commandLine.charAt(i))) i++;
                appendText(argument, commandLine.substring(start, i));
            }
            arguments.add(argument.toByteArray());
        }
        return arguments;
    }

    private int readQuoted(String commandLine, int start, char quote, ByteArrayOutputStream argument) {
        int i = start + 1;
        while (i < commandLine.length()) {
            char current = commandLine.charAt(i);
            if (current == quote) return i + 1;
            if (current == '\\' && i + 1 < commandLine.length()) {
                char escaped = commandLine.charAt(i + 1);
                if (quote == '\'') {
                    appendText(argument, escaped == '\'' ? "'" : "\\" + escaped);
                    i += 2;
                    continue;
                }
                if (escaped == 'x' && i + 3 < commandLine.length() && isHex(commandLine.substring(i + 2, i + 4))) {
                    argument.write(Integer.parseInt(commandLine.substring(i + 2, i + 4), 16));
                    i += 4;
                    continue;
                }
                appendText(argument, String.valueOf(switch (escaped) {
                    case 'n' -> '\n';
                    case 'r' -> '\r';
                    case 't' -> '\t';
                    case 'b' -> '\b';
                    case 'a' -> '\u0007';
                    default -> escaped;
                }));
                i += 2;
                continue;
            }
            // un caracter fuera del BMP ocupa dos char: se copian juntos
            int codePointEnd = i + Character.charCount(commandLine.codePointAt(i));
            appendText(argument, commandLine.substring(i, codePointEnd));
            i = codePointEnd;
        }
        throw new IllegalArgumentException("Unbalanced quotes in: " + commandLine);
    }

    private void appendText(ByteArrayOutputStream argument, String text) {
        argument.writeBytes(text.getBytes(StandardCharsets.UTF_8));
    }

    private boolean isHex(String text) {
        return text.chars().allMatch(character -> Character.digit(character, 16) != -1);
    }
}
