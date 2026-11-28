package modules.query.domain.entities;

import java.util.Map;

import static modules.shared.application.Primitives.getBool;
import static modules.shared.application.Primitives.getInt;
import static modules.shared.application.Primitives.getTrimmedString;

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

    @SuppressWarnings("unchecked")
    private SqlCompletionContextEntity(Map<String, Object> primitives) {
        this.prefix = getTrimmedString(primitives.get("prefix"));
        this.rawPrefixLength = getInt(primitives.get("rawPrefixLength"));
        this.qualifier = getTrimmedString(primitives.get("qualifier"));
        this.tablesByAlias = primitives.get("tablesByAlias") instanceof Map<?, ?> map
            ? Map.copyOf((Map<String, String[]>) map)
            : Map.of();
        this.isTableExpected = getBool(primitives.get("isTableExpected"));
    }

    public static SqlCompletionContextEntity fromPrimitives(Map<String, Object> primitives) {
        return new SqlCompletionContextEntity(primitives);
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
