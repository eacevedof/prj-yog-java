package modules.connections.infrastructure.components;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

import core.boot.BootPathsEnum;

import modules.connections.domain.entities.ConnectionEntity;

/**
 * El tunel SSH de una conexion (feature 12): abre la sesion SSH y reenvia un puerto local libre (127.0.0.1:N) al host y
 * puerto de la BD vistos desde el servidor SSH. JDBC se conecta luego a 127.0.0.1:N.
 * Claves de host: la primera vez se aceptan y se guardan en <JAVAYOG_HOME>/known_hosts; si luego cambian, la conexion
 * falla (posible suplantacion), como hace ssh con StrictHostKeyChecking=accept-new.
 */
public final class SshTunnelComponent {

    public static final String LOCAL_HOST = "127.0.0.1";
    private static final int CONNECT_TIMEOUT_MS = 10000;
    private static final int SERVER_ALIVE_INTERVAL_MS = 30000;

    /**
     * Un tunel abierto: la sesion SSH (para cerrarla) y el puerto local por el que entra JDBC.
     */
    public record OpenTunnel(Session session, int localPort) {
    }

    private SshTunnelComponent() {
    }

    public static SshTunnelComponent getInstance() {
        return new SshTunnelComponent();
    }

    /**
     * @throws JSchException host inalcanzable, credenciales, clave de host cambiada o reenvio rechazado
     */
    public OpenTunnel open(ConnectionEntity connectionEntity) throws JSchException {
        JSch jsch = new JSch();
        jsch.setKnownHosts(getKnownHostsPath());
        if (!connectionEntity.getSshPrivateKeyPath().isEmpty()) {
            String passphrase = connectionEntity.getSshPassphrase();
            jsch.addIdentity(connectionEntity.getSshPrivateKeyPath(), passphrase.isEmpty() ? null : passphrase);
        }

        Session session = jsch.getSession(connectionEntity.getSshUser(), connectionEntity.getSshHost(), connectionEntity.getSshPort());
        if (!connectionEntity.getSshPassword().isEmpty()) {
            session.setPassword(connectionEntity.getSshPassword());
        }
        session.setConfig("StrictHostKeyChecking", "accept-new");
        session.setConfig("PreferredAuthentications", "publickey,keyboard-interactive,password");
        session.setServerAliveInterval(SERVER_ALIVE_INTERVAL_MS);
        session.connect(CONNECT_TIMEOUT_MS);
        try {
            int localPort = session.setPortForwardingL(LOCAL_HOST, 0, connectionEntity.getHost(), connectionEntity.getPort());
            return new OpenTunnel(session, localPort);
        }
        catch (JSchException e) {
            session.disconnect();
            throw e;
        }
    }

    public void close(OpenTunnel openTunnel) {
        if (openTunnel != null && openTunnel.session().isConnected()) {
            openTunnel.session().disconnect();
        }
    }

    /**
     * El fichero se crea vacio si no existe: JSch lo necesita para poder apuntar las claves nuevas.
     */
    private String getKnownHostsPath() {
        Path knownHostsPath = Path.of(BootPathsEnum.getPathAppHome(), "known_hosts");
        try {
            Files.createDirectories(knownHostsPath.getParent());
            if (Files.notExists(knownHostsPath)) {
                Files.createFile(knownHostsPath);
            }
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return knownHostsPath.toString();
    }
}
