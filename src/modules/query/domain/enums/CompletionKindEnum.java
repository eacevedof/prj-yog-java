package modules.query.domain.enums;

/**
 * El tipo de un candidato del autocompletado (CompletionProvider): se ve junto al candidato.
 */
public final class CompletionKindEnum {

    public static final String KIND_COLUMN = "columna";
    public static final String KIND_TABLE = "tabla";
    public static final String KIND_DATABASE = "BD";
    public static final String KIND_KEYWORD = "palabra clave";

    private CompletionKindEnum() {
    }
}
