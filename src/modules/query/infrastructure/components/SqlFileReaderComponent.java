package modules.query.infrastructure.components;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Un fichero .sql leido por trozos en UTF-8 (con o sin BOM). Las IOException suben como UncheckedIOException.
 */
public final class SqlFileReaderComponent implements AutoCloseable {

    private static final int CHUNK_CHARS = 1024 * 1024;
    private static final char UTF8_BOM_CHAR = 0xFEFF;

    private final BufferedReader reader;
    private final long sizeBytes;
    private final char[] buffer = new char[CHUNK_CHARS];
    private boolean isFirstChunk = true;

    private SqlFileReaderComponent(BufferedReader reader, long sizeBytes) {
        this.reader = reader;
        this.sizeBytes = sizeBytes;
    }

    /**
     * @throws UncheckedIOException si el fichero no existe o no se puede leer
     */
    public static SqlFileReaderComponent open(Path filePath) {
        try {
            return new SqlFileReaderComponent(Files.newBufferedReader(filePath, StandardCharsets.UTF_8), Files.size(filePath));
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    /**
     * El siguiente trozo; null al acabar el fichero.
     * @throws UncheckedIOException
     */
    public String readChunk() {
        try {
            int readChars = reader.read(buffer, 0, buffer.length);
            if (readChars == -1) return null;
            String chunk = new String(buffer, 0, readChars);
            if (isFirstChunk && chunk.charAt(0) == UTF8_BOM_CHAR) chunk = chunk.substring(1);
            isFirstChunk = false;
            return chunk;
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void close() {
        try {
            reader.close();
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
