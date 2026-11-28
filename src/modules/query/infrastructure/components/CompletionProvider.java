package modules.query.infrastructure.components;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import modules.query.domain.enums.SqlKeywordsEnum;
import modules.query.domain.entities.SqlCompletionContextEntity;
import modules.query.domain.services.FuzzyMatcher;

/**
 * Los candidatos del autocompletado (features 14 y 41) para un contexto: con "x." las columnas de la tabla del alias x
 * (o las tablas si x es una BD); tras FROM/JOIN, tablas; si no, columnas de las tablas de la sentencia, tablas de la BD
 * activa y palabras clave. Ordenados por FuzzyMatcher y, a igualdad, columnas > tablas > BD > palabras clave.
 */
public final class CompletionProvider {

    public static final String KIND_COLUMN = "columna";
    public static final String KIND_TABLE = "tabla";
    public static final String KIND_DATABASE = "BD";
    public static final String KIND_KEYWORD = "palabra clave";

    private static final int MAX_CANDIDATES = 200;
    private static final List<String> KIND_ORDER = List.of(KIND_COLUMN, KIND_TABLE, KIND_DATABASE, KIND_KEYWORD);

    /**
     * Un candidato: el texto que se inserta, su tipo y de donde sale (la tabla de una columna).
     */
    public record Candidate(String text, String kind, String detail, int score) {
    }

    private CompletionProvider() {
    }

    public static CompletionProvider getInstance() {
        return new CompletionProvider();
    }

    public List<Candidate> getCandidates(SqlCompletionContextEntity context, String activeDatabase, SchemaCacheComponent schemaCache) {
        List<Candidate> candidates = new ArrayList<>();
        String qualifier = context.getQualifier();
        Map<String, String[]> tablesByAlias = context.getTablesByAlias();

        if (!qualifier.isEmpty()) {
            String[] table = tablesByAlias.get(qualifier.toLowerCase(Locale.ROOT));
            List<String> databaseNames = schemaCache.getDatabaseNames();
            if (table != null) {
                addColumns(candidates, getDatabase(table, activeDatabase), table[1], schemaCache);
            }
            else if (databaseNames != null && containsIgnoreCase(databaseNames, qualifier)) {
                addAll(candidates, schemaCache.getTableNames(qualifier), KIND_TABLE, qualifier);
            }
            else {
                addColumns(candidates, activeDatabase, qualifier, schemaCache);
            }
            return getRanked(candidates, context.getPrefix());
        }

        if (context.isTableExpected()) {
            addAll(candidates, schemaCache.getTableNames(activeDatabase), KIND_TABLE, activeDatabase);
            addAll(candidates, schemaCache.getDatabaseNames(), KIND_DATABASE, "");
            return getRanked(candidates, context.getPrefix());
        }

        Set<String> seenTables = new LinkedHashSet<>();
        for (String[] table : tablesByAlias.values()) {
            String database = getDatabase(table, activeDatabase);
            if (seenTables.add((database + "." + table[1]).toLowerCase(Locale.ROOT))) {
                addColumns(candidates, database, table[1], schemaCache);
            }
        }
        addAll(candidates, schemaCache.getTableNames(activeDatabase), KIND_TABLE, activeDatabase);
        for (String keyword : SqlKeywordsEnum.KEYWORDS) {
            candidates.add(new Candidate(keyword.toUpperCase(Locale.ROOT), KIND_KEYWORD, "", 0));
        }
        return getRanked(candidates, context.getPrefix());
    }

    private void addColumns(List<Candidate> candidates, String databaseName, String tableName, SchemaCacheComponent schemaCache) {
        addAll(candidates, schemaCache.getColumnNames(databaseName, tableName), KIND_COLUMN, tableName);
    }

    private void addAll(List<Candidate> candidates, List<String> names, String kind, String detail) {
        if (names == null) return;
        for (String name : names) {
            candidates.add(new Candidate(name, kind, detail, 0));
        }
    }

    /**
     * Sin lo que no casa, sin repetidos (misma columna en dos tablas: se queda la primera) y como mucho 200.
     */
    private List<Candidate> getRanked(List<Candidate> candidates, String prefix) {
        FuzzyMatcher fuzzyMatcher = FuzzyMatcher.getInstance();
        Set<String> seen = new LinkedHashSet<>();
        List<Candidate> ranked = new ArrayList<>();
        for (Candidate candidate : candidates) {
            int score = fuzzyMatcher.getScore(candidate.text(), prefix);
            if (score < 0 || !seen.add(candidate.kind() + ":" + candidate.text().toLowerCase(Locale.ROOT))) continue;
            ranked.add(new Candidate(candidate.text(), candidate.kind(), candidate.detail(), score));
        }
        ranked.sort(Comparator.comparingInt(Candidate::score).reversed()
            .thenComparingInt(candidate -> KIND_ORDER.indexOf(candidate.kind()))
            .thenComparingInt(candidate -> candidate.text().length())
            .thenComparing(candidate -> candidate.text().toLowerCase(Locale.ROOT)));
        return ranked.size() > MAX_CANDIDATES ? ranked.subList(0, MAX_CANDIDATES) : ranked;
    }

    private String getDatabase(String[] table, String activeDatabase) {
        return table[0].isEmpty() ? activeDatabase : table[0];
    }

    private boolean containsIgnoreCase(List<String> names, String name) {
        for (String candidate : names) {
            if (candidate.equalsIgnoreCase(name)) return true;
        }
        return false;
    }
}
