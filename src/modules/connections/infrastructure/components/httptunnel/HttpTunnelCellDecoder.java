package modules.connections.infrastructure.components.httptunnel;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/**
 * Las celdas del tunel llegan en bytes: su texto en UTF-8 (null es NULL) y si lo son.
 */
public final class HttpTunnelCellDecoder {

    private HttpTunnelCellDecoder() {
    }

    public static HttpTunnelCellDecoder getInstance() {
        return new HttpTunnelCellDecoder();
    }

    public String getText(byte[] cell) {
        return cell == null ? null : new String(cell, StandardCharsets.UTF_8);
    }

    /**
     * Si los bytes son UTF-8 valido (si no, son binarios).
     */
    public boolean isUtf8(byte[] bytes) {
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
