package modules.connections.infrastructure.components.httptunnel;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

import testcase.AbstractTestCase;

/**
 * La URL del tunel que se manda al PHP: segmento del motor y sin el "?app" del ping de SQLyog, que hace que el PHP
 * conteste "TunnelVersion:13.21" a toda peticion.
 */
final class HttpTunnelUrlTest extends AbstractTestCase {

    @Test
    void testEngineSegmentIsAdded() {
        assertEquals("https://tunnel.test/tok", HttpTunnelClient.getEngineUrl("https://tunnel.test/tok", "mysql"));
        assertEquals("https://tunnel.test/tok/pg", HttpTunnelClient.getEngineUrl("https://tunnel.test/tok", "pgsql"));
        assertEquals("https://tunnel.test/tok/pg", HttpTunnelClient.getEngineUrl("https://tunnel.test/tok/", "pgsql"));
        assertEquals("https://tunnel.test/tok/sqlite", HttpTunnelClient.getEngineUrl("https://tunnel.test/tok/sqlite", "sqlite"));
        assertEquals("https://tunnel.test/tok/mssql", HttpTunnelClient.getEngineUrl("https://tunnel.test/tok", "mssql"));
        assertEquals("https://tunnel.test/tok/redis", HttpTunnelClient.getEngineUrl("https://tunnel.test/tok/", "redis"));
        assertEquals("https://tunnel.test/tok/mongodb", HttpTunnelClient.getEngineUrl("https://tunnel.test/tok/mongodb", "mongodb"));
    }

    @Test
    void testPingParameterIsRemoved() {
        assertEquals("https://tunnel.test/tok", HttpTunnelClient.getEngineUrl("https://tunnel.test/tok?app=sqlyog", "mysql"));
        assertEquals("https://tunnel.test/tok", HttpTunnelClient.getEngineUrl(" https://tunnel.test/tok?app ", "mysql"));
        assertEquals("https://tunnel.test/tok/pg?x=1", HttpTunnelClient.getEngineUrl("https://tunnel.test/tok?app=1&x=1", "pgsql"));
        assertEquals("https://tunnel.test/tok?apple=1", HttpTunnelClient.getEngineUrl("https://tunnel.test/tok?apple=1#frag", "mysql"));
    }
}
