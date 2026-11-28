package modules.query.domain.services;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import modules.query.domain.entities.SqlStatementEntity;

/**
 * Las sentencias que en una conexion de produccion piden confirmacion (feature 44): UPDATE o DELETE sin WHERE, DROP y
 * TRUNCATE. Se miran sin comentarios ni literales, asi un 'where' dentro de una cadena o un `where` entre comillas
 * invertidas no cuenta como WHERE.
 */
public final class DestructiveSqlDetector {

    // un solo patron, de izquierda a derecha: lo que empieza antes gana ('--' dentro de una cadena no es comentario)
    private static final Pattern COMMENTS_AND_LITERALS_PATTERN = Pattern.compile(
        "'(?:[^'\\\\]|\\\\.|'')*'|\"(?:[^\"\\\\]|\\\\.|\"\")*\"|`[^`]*`|--[ \\t][^\\n]*|--$|#[^\\n]*|/\\*(?s:.*?)\\*/",
        Pattern.MULTILINE
    );
    private static final Pattern WHERE_PATTERN = Pattern.compile("\\bWHERE\\b");

    private DestructiveSqlDetector() {
    }

    public static DestructiveSqlDetector getInstance() {
        return new DestructiveSqlDetector();
    }

    public List<SqlStatementEntity> getDestructiveStatements(List<SqlStatementEntity> statements) {
        List<SqlStatementEntity> destructiveStatements = new ArrayList<>();
        for (SqlStatementEntity statement : statements) {
            if (isDestructive(statement.getSql())) {
                destructiveStatements.add(statement);
            }
        }
        return destructiveStatements;
    }

    public boolean isDestructive(String sql) {
        String normalizedSql = getNormalizedSql(sql);
        String firstWord = normalizedSql.split("\\s+", 2)[0];
        return switch (firstWord) {
            case "DROP", "TRUNCATE" -> true;
            case "UPDATE", "DELETE" -> !WHERE_PATTERN.matcher(normalizedSql).find();
            default -> false;
        };
    }

    private String getNormalizedSql(String sql) {
        // un literal queda como "x" (sigue habiendo algo); un comentario desaparece (no puede ser la primera palabra)
        String normalizedSql = COMMENTS_AND_LITERALS_PATTERN.matcher(sql).replaceAll(matchResult -> {
            char first = matchResult.group().charAt(0);
            return first == '\'' || first == '"' || first == '`' ? " x " : " ";
        });
        return normalizedSql.trim().toUpperCase(Locale.ROOT);
    }
}
