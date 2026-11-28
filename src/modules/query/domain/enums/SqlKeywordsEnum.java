package modules.query.domain.enums;

import java.util.List;
import java.util.Set;

/**
 * Las palabras clave de MySQL que se resaltan en el editor y que ofrece el autocompletado. CLAUSE_KEYWORDS: las que
 * no pueden ser un alias (tras "FROM tabla" viene un alias o una de estas).
 */
public final class SqlKeywordsEnum {

    public static final List<String> KEYWORDS = List.of(
        "add", "after", "all", "alter", "and", "as", "asc", "auto_increment", "begin", "between", "by", "call", "case",
        "change", "character", "charset", "check", "collate", "column", "commit", "constraint", "create", "cross",
        "database", "databases", "declare", "default", "delete", "delimiter", "desc", "describe", "distinct", "do",
        "drop", "duplicate", "else", "end", "engine", "event", "exists", "explain", "false", "for", "foreign", "from",
        "full", "function", "grant", "group", "having", "if", "ignore", "in", "index", "inner", "insert", "interval",
        "into", "is", "join", "key", "kill", "left", "like", "limit", "lock", "modify", "not", "null", "offset", "on",
        "or", "order", "outer", "partition", "primary", "procedure", "references", "regexp", "rename", "replace",
        "return", "returns", "revoke", "right", "rollback", "schema", "select", "set", "show", "start", "status",
        "table", "tables", "temporary", "then", "transaction", "trigger", "true", "truncate", "union", "unique",
        "unlock", "unsigned", "update", "use", "using", "values", "variables", "view", "when", "where", "while", "with"
    );

    public static final Set<String> CLAUSE_KEYWORDS = Set.of(
        "where", "on", "using", "join", "inner", "left", "right", "cross", "full", "outer", "natural", "straight_join",
        "group", "order", "having", "limit", "set", "values", "value", "select", "union", "for", "lock", "window",
        "partition", "as", "into", "from", "update", "delete", "insert", "and", "or", "procedure", "ignore"
    );

    private SqlKeywordsEnum() {
    }
}
