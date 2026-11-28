package modules.files.domain.services;

import java.util.Locale;
import java.util.Set;

import modules.files.domain.enums.FilesConfigEnum;

/**
 * El nombre de tabla de un fichero: su nombre sin extension, con solo letras, digitos y _ (asi vale sin comillas en
 * el SQL), sin empezar por digito y sin chocar con otra tabla: clientes.csv y clientes.json son clientes y
 * clientes_json.
 */
public final class FileTableNameBuilder {

    private static final int MAX_TABLE_NAME_LENGTH = 64;

    private FileTableNameBuilder() {
    }

    public static FileTableNameBuilder getInstance() {
        return new FileTableNameBuilder();
    }

    /**
     * @param usedLowerNames las tablas que ya hay, en minusculas (SQLite no distingue)
     */
    public String getTableName(String fileName, Set<String> usedLowerNames) {
        int dotIndex = fileName.lastIndexOf('.');
        String baseName = dotIndex <= 0 ? fileName : fileName.substring(0, dotIndex);
        String extension = dotIndex <= 0 ? "" : fileName.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
        String tableName = getSafeName(baseName);
        if (usedLowerNames.contains(tableName.toLowerCase(Locale.ROOT)) && !extension.isEmpty()) {
            tableName = tableName + FilesConfigEnum.DUPLICATE_SUFFIX + extension;
        }
        String candidateName = tableName;
        int counter = 2;
        while (usedLowerNames.contains(candidateName.toLowerCase(Locale.ROOT))) {
            candidateName = tableName + FilesConfigEnum.DUPLICATE_SUFFIX + counter++;
        }
        return candidateName;
    }

    private String getSafeName(String baseName) {
        String safeName = baseName.replaceAll("[^A-Za-z0-9_]", "_");
        if (safeName.isEmpty() || Character.isDigit(safeName.charAt(0))) safeName = "t_" + safeName;
        if (safeName.toLowerCase(Locale.ROOT).startsWith("sqlite_")) safeName = "t_" + safeName;
        return safeName.length() > MAX_TABLE_NAME_LENGTH ? safeName.substring(0, MAX_TABLE_NAME_LENGTH) : safeName;
    }
}
