package modules.connections.infrastructure.components.httptunnel;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import modules.connections.domain.enums.DbEngineEnum;

/**
 * La URL a la que se manda cada peticion del tunel, segun el motor.
 */
public final class HttpTunnelUrlBuilder {

    // el ping de SQLyog (?app): lo usa tambien HttpTunnelClient
    static final String PING_PARAMETER = "app";

    private HttpTunnelUrlBuilder() {
    }

    public static HttpTunnelUrlBuilder getInstance() {
        return new HttpTunnelUrlBuilder();
    }

    /**
     * El tunel elige el motor por el ultimo segmento de la ruta: .../&lt;token&gt; es MySQL, .../&lt;token&gt;/pg PostgreSQL y
     * .../&lt;token&gt;/sqlite SQLite; .../mssql, .../redis y .../mongodb los de multidbs (contrato en el vault:
     * sqlyog-tunnel-multidbs). La URL guardada es la base; si ya lleva el segmento, se deja.
     * Se quita el parametro "app" de la query: es el ping de SQLyog (SQLyog lo añade solo, y en sus logs la URL sale con
     * "?app=..."); si la URL guardada lo lleva, el PHP contesta "TunnelVersion:13.21" a toda peticion, tambien a las
     * consultas. Tambien se quita el fragmento (#...), que no viaja.
     */
    public String getEngineUrl(String httpUrl, String dbEngine) {
        String engineSegment = switch (dbEngine) {
            case DbEngineEnum.PGSQL -> "pg";
            case DbEngineEnum.SQLITE -> DbEngineEnum.SQLITE;
            case DbEngineEnum.MSSQL -> DbEngineEnum.MSSQL;
            case DbEngineEnum.REDIS -> DbEngineEnum.REDIS;
            case DbEngineEnum.MONGODB -> DbEngineEnum.MONGODB;
            default -> "";
        };
        URI uri = URI.create(httpUrl.trim());
        String path = uri.getRawPath() == null ? "" : uri.getRawPath();
        if (!engineSegment.isEmpty() && !path.endsWith("/" + engineSegment)) {
            path = (path.endsWith("/") ? path : path + "/") + engineSegment;
        }
        String query = getQueryWithoutPing(uri.getRawQuery());
        return uri.getScheme() + "://" + uri.getRawAuthority() + path + (query.isEmpty() ? "" : "?" + query);
    }

    private String getQueryWithoutPing(String rawQuery) {
        if (rawQuery == null || rawQuery.isEmpty()) return "";
        List<String> parameters = new ArrayList<>();
        for (String parameter : rawQuery.split("&")) {
            String name = parameter.contains("=") ? parameter.substring(0, parameter.indexOf('=')) : parameter;
            if (parameter.isEmpty() || PING_PARAMETER.equals(name)) continue;
            parameters.add(parameter);
        }
        return String.join("&", parameters);
    }
}
