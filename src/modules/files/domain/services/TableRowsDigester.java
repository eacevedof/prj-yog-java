package modules.files.domain.services;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/**
 * La huella (SHA-256) de las columnas y filas de una tabla: se toma al cargar el fichero y al guardar se compara, asi
 * solo se reescriben los ficheros cuya tabla ha cambiado. Distingue NULL de vacio y 1 de "1".
 */
public final class TableRowsDigester {

    private static final String ALGORITHM = "SHA-256";
    private static final char FIELD_SEPARATOR = '\u001F';
    private static final char ROW_SEPARATOR = '\u001E';

    private TableRowsDigester() {
    }

    public static TableRowsDigester getInstance() {
        return new TableRowsDigester();
    }

    public String getDigest(List<String> columnNames, List<List<Object>> rows) {
        MessageDigest messageDigest = getMessageDigest();
        update(messageDigest, String.join(String.valueOf(FIELD_SEPARATOR), columnNames));
        for (List<Object> row : rows) {
            StringBuilder rowText = new StringBuilder().append(ROW_SEPARATOR);
            for (Object value : row) {
                rowText.append(FIELD_SEPARATOR);
                if (value == null) {
                    rowText.append('N');
                    continue;
                }
                rowText.append(value instanceof Number ? 'n' : 's').append(value);
            }
            update(messageDigest, rowText.toString());
        }
        return HexFormat.of().formatHex(messageDigest.digest());
    }

    private void update(MessageDigest messageDigest, String text) {
        messageDigest.update(text.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * SHA-256 viene siempre con la JVM: si faltara, la JVM esta rota.
     */
    private MessageDigest getMessageDigest() {
        try {
            return MessageDigest.getInstance(ALGORITHM);
        }
        catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
