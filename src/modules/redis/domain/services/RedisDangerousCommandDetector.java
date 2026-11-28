package modules.redis.domain.services;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Feature 44 en Redis: los comandos de la consola que borran datos, cambian la configuracion o paran el servidor. En
 * una conexion de produccion no se lanzan sin confirmar.
 */
public final class RedisDangerousCommandDetector {

    private static final Set<String> DANGEROUS_COMMANDS = Set.of(
        "FLUSHALL", "FLUSHDB", "DEL", "UNLINK", "SHUTDOWN", "SWAPDB", "MOVE", "MIGRATE", "RENAME", "RENAMENX",
        "RESTORE", "REPLICAOF", "SLAVEOF", "FAILOVER", "DEBUG", "BGREWRITEAOF", "BGSAVE", "SAVE"
    );

    /**
     * Comandos con subcomando: solo son peligrosos con esos subcomandos.
     */
    private static final Set<String> DANGEROUS_SUBCOMMANDS = Set.of(
        "CONFIG SET", "CONFIG RESETSTAT", "CONFIG REWRITE", "SCRIPT FLUSH", "SCRIPT KILL", "FUNCTION FLUSH",
        "FUNCTION DELETE", "FUNCTION RESTORE", "CLIENT KILL", "ACL SETUSER", "ACL DELUSER", "ACL LOAD", "CLUSTER RESET",
        "MODULE UNLOAD", "XGROUP DESTROY"
    );

    private RedisDangerousCommandDetector() {
    }

    public static RedisDangerousCommandDetector getInstance() {
        return new RedisDangerousCommandDetector();
    }

    public boolean isDangerous(List<String> arguments) {
        if (arguments.isEmpty()) return false;
        String command = arguments.getFirst().toUpperCase(Locale.ROOT);
        if (DANGEROUS_COMMANDS.contains(command)) return true;
        if (arguments.size() < 2) return false;
        return DANGEROUS_SUBCOMMANDS.contains(command + " " + arguments.get(1).toUpperCase(Locale.ROOT));
    }
}
