package modules.query.domain.enums;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Los juegos de caracteres de "Exportar como..." (feature 9).
 */
public final class ResultExportCharsetEnum {

    public static final String CHARSET_UTF8_BOM = "UTF-8 (BOM, Excel)";
    /**
     * Lo que se ve en el desplegable => el charset de Java. "UTF-8 (BOM, Excel)" es UTF-8 con la marca EF BB BF al
     * principio, para que Excel abra bien un CSV con acentos.
     */
    public static final Map<String, Charset> CHARSETS_BY_LABEL = Map.of(
        "UTF-8", StandardCharsets.UTF_8,
        CHARSET_UTF8_BOM, StandardCharsets.UTF_8,
        "ISO-8859-1", StandardCharsets.ISO_8859_1,
        "windows-1252", Charset.forName("windows-1252"),
        "UTF-16", StandardCharsets.UTF_16
    );
    public static final List<String> CHARSET_LABELS = List.of("UTF-8", CHARSET_UTF8_BOM, "ISO-8859-1", "windows-1252", "UTF-16");

    private ResultExportCharsetEnum() {
    }
}
