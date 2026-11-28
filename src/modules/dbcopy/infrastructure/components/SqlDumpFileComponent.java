package modules.dbcopy.infrastructure.components;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * El fichero .sql de un volcado: se escribe en UTF-8 segun se lee la BD (no se junta en memoria), con buffer. Las
 * IOException suben como UncheckedIOException para que el servicio las trate como un error de escritura.
 */
public final class SqlDumpFileComponent implements AutoCloseable {

    private final BufferedWriter writer;

    private SqlDumpFileComponent(BufferedWriter writer) {
        this.writer = writer;
    }

    /**
     * @throws UncheckedIOException si no se puede crear el fichero
     */
    public static SqlDumpFileComponent open(Path filePath) {
        try {
            Path parentDirectory = filePath.toAbsolutePath().getParent();
            if (parentDirectory != null) Files.createDirectories(parentDirectory);
            return new SqlDumpFileComponent(Files.newBufferedWriter(filePath, StandardCharsets.UTF_8));
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * @throws UncheckedIOException
     */
    public void write(String text) {
        try {
            writer.write(text);
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void close() {
        try {
            writer.close();
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
