package modules.redis.domain.services;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * La respuesta de un comando como la ensena redis-cli: (nil), (integer) 3, "texto" (con escapes), y las listas
 * numeradas "1) ..." con sus anidadas sangradas. Un valor que no es UTF-8 sale con escapes \xHH.
 */
public final class RedisReplyFormatter {

    private RedisReplyFormatter() {
    }

    public static RedisReplyFormatter getInstance() {
        return new RedisReplyFormatter();
    }

    public String getText(Object reply) {
        StringBuilder text = new StringBuilder();
        append(text, reply, "");
        return text.toString();
    }

    private void append(StringBuilder text, Object reply, String indent) {
        switch (reply) {
            case null -> text.append("(nil)");
            case Long number -> text.append("(integer) ").append(number);
            case Integer number -> text.append("(integer) ").append(number);
            case Double number -> text.append("(double) ").append(number);
            case Boolean bool -> text.append(bool ? "(true)" : "(false)");
            case byte[] bytes -> text.append(getQuoted(bytes));
            case String string -> text.append(getQuoted(string.getBytes(StandardCharsets.UTF_8)));
            case List<?> items -> appendList(text, items, indent);
            case Map<?, ?> map -> appendList(text, map.entrySet().stream().flatMap(entry -> java.util.stream.Stream.of(entry.getKey(), entry.getValue())).toList(), indent);
            default -> text.append(reply);
        }
    }

    private void appendList(StringBuilder text, List<?> items, String indent) {
        if (items.isEmpty()) {
            text.append("(empty array)");
            return;
        }
        int width = String.valueOf(items.size()).length();
        for (int i = 0; i < items.size(); i++) {
            String number = String.format("%" + width + "d) ", i + 1);
            if (i > 0) text.append('\n').append(indent);
            text.append(number);
            append(text, items.get(i), indent + " ".repeat(number.length()));
        }
    }

    /**
     * "texto" con \" \\ \n \r \t y \xHH para lo que no se puede ensenar.
     */
    public String getQuoted(byte[] bytes) {
        String decoded = getUtf8OrNull(bytes);
        StringBuilder quoted = new StringBuilder("\"");
        if (decoded == null) {
            for (byte value : bytes) {
                int unsigned = value & 0xFF;
                if (unsigned >= 0x20 && unsigned < 0x7F && unsigned != '"' && unsigned != '\\') quoted.append((char) unsigned);
                else quoted.append(String.format("\\x%02x", unsigned));
            }
            return quoted.append('"').toString();
        }
        for (int i = 0; i < decoded.length(); i++) {
            char current = decoded.charAt(i);
            switch (current) {
                case '"' -> quoted.append("\\\"");
                case '\\' -> quoted.append("\\\\");
                case '\n' -> quoted.append("\\n");
                case '\r' -> quoted.append("\\r");
                case '\t' -> quoted.append("\\t");
                default -> {
                    if (current < 0x20) quoted.append(String.format("\\x%02x", (int) current));
                    else quoted.append(current);
                }
            }
        }
        return quoted.append('"').toString();
    }

    /**
     * El texto si los bytes son UTF-8 valido; null si son binarios.
     */
    public static String getUtf8OrNull(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString();
        }
        catch (CharacterCodingException e) {
            return null;
        }
    }
}
