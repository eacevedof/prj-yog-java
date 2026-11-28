package modules.query.domain.entities;

import java.util.Map;

/**
 * Donde esta el cursor para el autocompletado: lo que se esta escribiendo (prefix), lo que va antes del punto si lo hay
 * (qualifier: alias, tabla o BD), las tablas que nombra la sentencia (alias en minusculas => [bd, tabla]) y si lo
 * esperado es una tabla (tras FROM, JOIN, INTO, UPDATE, TABLE).
 */
public final class SqlCompletionContextEntity {

    private final String prefix;
    private final int rawPrefixLength;
    private final String qualifier;
    private final Map<String, String[]> tablesByAlias;
    private final boolean isTableExpected;

    private SqlCompletionContextEntity(String prefix, int rawPrefixLength, String qualifier, Map<String, String[]> tablesByAlias, boolean isTableExpected) {
        this.prefix = prefix;
        this.rawPrefixLength = rawPrefixLength;
        this.qualifier = qualifier;
        this.tablesByAlias = Map.copyOf(tablesByAlias);
        this.isTableExpected = isTableExpected;
    }

    public static SqlCompletionContextEntity of(String prefix, int rawPrefixLength, String qualifier, Map<String, String[]> tablesByAlias, boolean isTableExpected) {
        return new SqlCompletionContextEntity(prefix, rawPrefixLength, qualifier, tablesByAlias, isTableExpected);
    }

    public String getPrefix() {
        return prefix;
    }

    /**
     * Lo que ocupa en el texto lo escrito (con sus comillas invertidas): lo que se sustituye al aceptar.
     */
    public int getRawPrefixLength() {
        return rawPrefixLength;
    }

    /**
     * "" si no hay punto delante.
     */
    public String getQualifier() {
        return qualifier;
    }

    /**
     * alias (y tambien el nombre de la tabla) en minusculas => [bd o "", tabla].
     */
    public Map<String, String[]> getTablesByAlias() {
        return tablesByAlias;
    }

    public boolean isTableExpected() {
        return isTableExpected;
    }
}
