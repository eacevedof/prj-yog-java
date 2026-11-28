package modules.dbcopy.domain.services;

import java.util.ArrayList;
import java.util.List;

import modules.shared.domain.enums.SharedConfigEnum;
import modules.shared.domain.services.SqlIdentifierQuoter;

import modules.dbcopy.domain.enums.DbCopyConfigEnum;
import modules.dbcopy.domain.enums.DbCopyObjectTypeEnum;

/**
 * El texto de un volcado .sql de MySQL (el "SQL Dump" de SQLyog, como mysqldump): cabecera con los SET que dejan cargar
 * el fichero en cualquier orden, CREATE DATABASE y USE opcionales, una seccion comentada por objeto y el pie que
 * devuelve la sesion a como estaba. Las rutinas, triggers y eventos van entre "DELIMITER ;;" (su cuerpo lleva ;).
 */
public final class SqlDumpScriptBuilder {

    private static final String ROUTINE_DELIMITER = ";;";

    private final SqlIdentifierQuoter sqlIdentifierQuoter = SqlIdentifierQuoter.getInstance();

    private SqlDumpScriptBuilder() {
    }

    public static SqlDumpScriptBuilder getInstance() {
        return new SqlDumpScriptBuilder();
    }

    /**
     * Comentarios con la BD y la fecha, ya con su etiqueta en el idioma de la app (versionInfo vacio = sin la linea de
     * version) y los SET de mysqldump: utf8mb4, zona UTC (los TIMESTAMP viajan como texto), sin modo estricto y, si se
     * pide, sin claves foraneas.
     */
    public String getHeader(String databaseText, String dateText, String versionInfo, boolean isForeignKeyChecksOff) {
        List<String> lines = new ArrayList<>(List.of(
            "-- " + SharedConfigEnum.APP_NAME + " SQL Dump",
            getCommentLine(databaseText),
            getCommentLine(dateText)
        ));
        if (!versionInfo.isEmpty()) lines.add(getCommentLine(versionInfo));
        lines.addAll(List.of(
            "",
            "/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;",
            "/*!40101 SET NAMES utf8mb4 */;",
            "/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;",
            "/*!40103 SET TIME_ZONE='+00:00' */;",
            "/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;",
            "/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;"
        ));
        if (isForeignKeyChecksOff) lines.add("/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;");
        return String.join(DbCopyConfigEnum.NEW_LINE, lines) + DbCopyConfigEnum.NEW_LINE + DbCopyConfigEnum.NEW_LINE;
    }

    public String getFooter(boolean isForeignKeyChecksOff) {
        List<String> lines = new ArrayList<>();
        if (isForeignKeyChecksOff) lines.add("/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;");
        lines.addAll(List.of(
            "/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;",
            "/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;",
            "/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;",
            "/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;"
        ));
        return DbCopyConfigEnum.NEW_LINE + String.join(DbCopyConfigEnum.NEW_LINE, lines) + DbCopyConfigEnum.NEW_LINE;
    }

    public String getUseSql(String databaseName) {
        return "USE " + sqlIdentifierQuoter.getQuoted(databaseName) + ";" + DbCopyConfigEnum.NEW_LINE + DbCopyConfigEnum.NEW_LINE;
    }

    /**
     * "-- Tabla `clientes`" con una linea en blanco antes.
     */
    public String getSectionComment(String label, String objectName) {
        return DbCopyConfigEnum.NEW_LINE + getCommentLine(label + " " + sqlIdentifierQuoter.getQuoted(objectName)) + DbCopyConfigEnum.NEW_LINE + DbCopyConfigEnum.NEW_LINE;
    }

    /**
     * "-- Datos de `clientes`" con una linea en blanco antes: abre los INSERT de una tabla. La etiqueta llega ya traducida.
     */
    public String getDataComment(String label, String tableName) {
        return DbCopyConfigEnum.NEW_LINE + getCommentLine(label + " " + sqlIdentifierQuoter.getQuoted(tableName)) + DbCopyConfigEnum.NEW_LINE;
    }

    /**
     * "-- ERROR `vista`: mensaje": un objeto que no se pudo volcar.
     */
    public String getErrorComment(String objectName, String message) {
        return getCommentLine("ERROR " + sqlIdentifierQuoter.getQuoted(objectName) + ": " + message) + DbCopyConfigEnum.NEW_LINE;
    }

    /**
     * Un comentario "--" acaba en el salto de linea: un nombre del servidor con \n (MySQL lo admite entre comillas)
     * dejaria lo que sigue como SQL al cargar el fichero (como el CVE-2016-5483 de mysqldump). Los saltos van a espacios.
     */
    private String getCommentLine(String text) {
        return "-- " + text.replace('\r', ' ').replace('\n', ' ');
    }

    /**
     * Una sentencia terminada en ";"; las rutinas, triggers y eventos entre DELIMITER ;; (su cuerpo lleva ;).
     */
    public String getStatement(String objectType, String sql) {
        String trimmedSql = sql.strip();
        if (!isRoutineLike(objectType)) return trimmedSql + ";" + DbCopyConfigEnum.NEW_LINE;
        return "DELIMITER " + ROUTINE_DELIMITER + DbCopyConfigEnum.NEW_LINE + trimmedSql + ROUTINE_DELIMITER + DbCopyConfigEnum.NEW_LINE + "DELIMITER ;" + DbCopyConfigEnum.NEW_LINE;
    }

    private boolean isRoutineLike(String objectType) {
        return DbCopyObjectTypeEnum.PROCEDURE.equals(objectType)
            || DbCopyObjectTypeEnum.FUNCTION.equals(objectType)
            || DbCopyObjectTypeEnum.TRIGGER.equals(objectType)
            || DbCopyObjectTypeEnum.EVENT.equals(objectType);
    }
}
