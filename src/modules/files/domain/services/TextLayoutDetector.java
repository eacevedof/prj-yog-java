package modules.files.domain.services;

import modules.files.domain.enums.FilesConfigEnum;

/**
 * Lo que no son datos pero hay que respetar al volver a escribir un fichero de texto: el BOM de UTF-8 y los saltos de
 * linea (\r\n de Windows o \n).
 */
public final class TextLayoutDetector {

    private TextLayoutDetector() {
    }

    public static TextLayoutDetector getInstance() {
        return new TextLayoutDetector();
    }

    public boolean hasBom(String text) {
        return text.startsWith(FilesConfigEnum.UTF8_BOM);
    }

    public String getBody(String text) {
        return hasBom(text) ? text.substring(FilesConfigEnum.UTF8_BOM.length()) : text;
    }

    /**
     * El del primer salto de linea; sin saltos, \n.
     */
    public String getLineSeparator(String text) {
        int newLineIndex = text.indexOf('\n');
        if (newLineIndex > 0 && text.charAt(newLineIndex - 1) == '\r') return FilesConfigEnum.LINE_SEPARATOR_CRLF;
        return FilesConfigEnum.LINE_SEPARATOR_LF;
    }
}
