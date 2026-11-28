package modules.mongo.domain.services;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import modules.mongo.domain.enums.MongoShellCommandTypeEnum;
import modules.mongo.domain.entities.MongoShellCommandEntity;

/**
 * Feature 44 en MongoDB: lo que borra o pisa muchos documentos, colecciones o la BD. En una conexion de produccion no
 * se lanza sin confirmar.
 */
public final class MongoDangerousCommandDetector {

    private static final Set<String> DANGEROUS_METHODS = Set.of(
        "deletemany", "deleteone", "updatemany", "replaceone", "drop", "dropindex", "dropindexes", "renamecollection",
        "findoneanddelete", "findoneandreplace", "bulkwrite"
    );
    private static final Set<String> DANGEROUS_RUN_COMMANDS = Set.of(
        "drop", "dropdatabase", "delete", "update", "dropindexes", "renamecollection", "shutdown", "dropuser",
        "dropallusersfromdatabase", "droprole", "collmod", "compact"
    );
    private static final Pattern FIRST_KEY_PATTERN = Pattern.compile("^\\s*\\{\\s*[\"']?([A-Za-z_$][\\w$]*)[\"']?\\s*:");

    private MongoDangerousCommandDetector() {
    }

    public static MongoDangerousCommandDetector getInstance() {
        return new MongoDangerousCommandDetector();
    }

    public boolean isDangerous(MongoShellCommandEntity command) {
        return switch (command.type()) {
            case MongoShellCommandTypeEnum.DROP_DATABASE -> true;
            case MongoShellCommandTypeEnum.COLLECTION_METHOD -> DANGEROUS_METHODS.contains(command.method().toLowerCase(Locale.ROOT));
            case MongoShellCommandTypeEnum.RUN_COMMAND -> {
                Matcher firstKeyMatcher = FIRST_KEY_PATTERN.matcher(command.arguments().isEmpty() ? "" : command.arguments().getFirst());
                yield firstKeyMatcher.find() && DANGEROUS_RUN_COMMANDS.contains(firstKeyMatcher.group(1).toLowerCase(Locale.ROOT));
            }
            default -> false;
        };
    }
}
