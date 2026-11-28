package modules.query.infrastructure.components;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import modules.shared.infrastructure.components.theme.ThemeComponent;

import core.config.TranslatorResourceBundle;
import static core.config.Global.__;

import modules.query.domain.enums.ResultExportCharsetEnum;
import modules.query.domain.entities.ResultExportOptionsEntity;
import modules.query.domain.services.ResultExportFormatter;
import modules.query.infrastructure.controllers.ResultExportDialogController;

/**
 * "Exportar como..." de la rejilla (feature 9): abre el dialogo de opciones (como el Export As de SQLyog) y escribe el
 * fichero con lo que da ResultExportFormatter, en el juego de caracteres elegido.
 */
public final class ResultExportComponent {

    private static final String PATH_DIALOG = "/modules/query/infrastructure/views/result-export-dialog.fxml";
    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    private final ResultExportFormatter resultExportFormatter;

    private ResultExportComponent() {
        this.resultExportFormatter = ResultExportFormatter.getInstance();
    }

    public static ResultExportComponent getInstance() {
        return new ResultExportComponent();
    }

    /**
     * Abre el dialogo (no bloquea). preselectedFormat: el formato ya elegido en el menu (null = el ultimo usado);
     * markedRows: las filas marcadas en la columna 0 (vacio = no se ofrece "solo las marcadas");
     * createTableSqlLoader: lee el CREATE TABLE de la tabla de la rejilla (null = la rejilla no es de una tabla y no se
     * ofrece "Incluir CREATE TABLE").
     */
    public void openDialog(
        Window owner,
        String preselectedFormat,
        String tableName,
        List<String> columnNames,
        List<List<String>> allRows,
        List<List<String>> markedRows,
        Callable<String> createTableSqlLoader
    ) {
        FXMLLoader fxmlLoader = new FXMLLoader(ResultExportComponent.class.getResource(PATH_DIALOG), TranslatorResourceBundle.getInstance());
        Parent dialogRoot;
        try {
            dialogRoot = fxmlLoader.load();
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        ResultExportDialogController resultExportDialogController = fxmlLoader.getController();
        Stage dialogStage = new Stage();
        dialogStage.initOwner(owner);
        dialogStage.initModality(Modality.WINDOW_MODAL);
        dialogStage.setTitle(__("Exportar como"));
        Scene dialogScene = new Scene(dialogRoot);
        ThemeComponent.getInstance().applyTo(dialogScene);
        dialogStage.setScene(dialogScene);
        resultExportDialogController.setup(preselectedFormat, tableName, columnNames, allRows, markedRows, createTableSqlLoader);
        dialogStage.show();
    }

    /**
     * Escribe el fichero; el charset de las cabeceras XML/HTML ya va en options.
     * @throws IOException
     */
    public void write(
        Path filePath,
        String format,
        List<String> columnNames,
        List<List<String>> rows,
        ResultExportOptionsEntity options,
        String charsetLabel
    ) throws IOException {
        String content = resultExportFormatter.getFormatted(format, columnNames, rows, options);
        Charset charset = ResultExportCharsetEnum.CHARSETS_BY_LABEL.getOrDefault(charsetLabel, StandardCharsets.UTF_8);
        byte[] contentBytes = content.getBytes(charset);
        if (!ResultExportCharsetEnum.CHARSET_UTF8_BOM.equals(charsetLabel)) {
            Files.write(filePath, contentBytes);
            return;
        }
        byte[] withBom = new byte[UTF8_BOM.length + contentBytes.length];
        System.arraycopy(UTF8_BOM, 0, withBom, 0, UTF8_BOM.length);
        System.arraycopy(contentBytes, 0, withBom, UTF8_BOM.length, contentBytes.length);
        Files.write(filePath, withBom);
    }

    /**
     * El nombre que va en la cabecera de XML/HTML/Excel: UTF-8 tambien con BOM.
     */
    public String getDeclaredCharset(String charsetLabel) {
        Charset charset = ResultExportCharsetEnum.CHARSETS_BY_LABEL.getOrDefault(charsetLabel, StandardCharsets.UTF_8);
        return charset.name();
    }
}
