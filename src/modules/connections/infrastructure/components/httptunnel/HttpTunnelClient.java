package modules.connections.infrastructure.components.httptunnel;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import org.xml.sax.SAXException;

import modules.shared.domain.enums.SharedConfigEnum;

import modules.connections.domain.entities.ConnectionEntity;

/**
 * Cliente del tunel HTTP de SQLyog (protocolo "SQLyog Tunnel 13.21"), el que sirve el PHP de prj-yog: cada sentencia
 * es un POST con un XML (host, usuario, password, BD, charset, puerto y query, todo en base64) y la respuesta es otro
 * XML con un &lt;result&gt; por sentencia o result set. El tunel no guarda sesion: cada peticion abre su propia conexion
 * al MySQL, por eso la BD va en cada una.
 */
public final class HttpTunnelClient {

    public static final String TUNNEL_VERSION = "13.21";
    private static final String CHARSET = "utf8mb4";
    private static final String USER_AGENT = SharedConfigEnum.APP_NAME;
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final int MAX_ERROR_BODY_CHARS = 200;
    private static final String PING_PARAMETER = "app";

    private final URI tunnelUri;
    private final Duration requestTimeout;
    private final String host;
    private final int port;
    private final String userName;
    private final String userPassword;
    private final HttpClient httpClient;
    private final AtomicReference<CompletableFuture<?>> runningRequest = new AtomicReference<>();

    private HttpTunnelClient(ConnectionEntity connectionEntity) {
        this.tunnelUri = URI.create(getEngineUrl(connectionEntity.getHttpUrl(), connectionEntity.getDbEngine()));
        this.requestTimeout = Duration.ofSeconds(connectionEntity.getHttpTimeoutS());
        this.host = connectionEntity.getHost();
        this.port = connectionEntity.getPort();
        this.userName = connectionEntity.getUserName();
        this.userPassword = connectionEntity.getUserPassword();
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    }

    public static HttpTunnelClient getInstance(ConnectionEntity connectionEntity) {
        return new HttpTunnelClient(connectionEntity);
    }

    /**
     * El tunel elige el motor por el ultimo segmento de la ruta: .../&lt;token&gt; es MySQL, .../&lt;token&gt;/pg PostgreSQL y
     * .../&lt;token&gt;/sqlite SQLite; .../mssql, .../redis y .../mongodb los de multidbs (contrato en el vault:
     * sqlyog-tunnel-multidbs). La URL guardada es la base; si ya lleva el segmento, se deja.
     * Se quita el parametro "app" de la query: es el ping de SQLyog (SQLyog lo añade solo, y en sus logs la URL sale con
     * "?app=..."); si la URL guardada lo lleva, el PHP contesta "TunnelVersion:13.21" a toda peticion, tambien a las
     * consultas. Tambien se quita el fragmento (#...), que no viaja.
     */
    static String getEngineUrl(String httpUrl, String dbEngine) {
        String engineSegment = switch (dbEngine) {
            case "pgsql" -> "pg";
            case "sqlite" -> "sqlite";
            case "mssql" -> "mssql";
            case "redis" -> "redis";
            case "mongodb" -> "mongodb";
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

    private static String getQueryWithoutPing(String rawQuery) {
        if (rawQuery == null || rawQuery.isEmpty()) return "";
        List<String> parameters = new ArrayList<>();
        for (String parameter : rawQuery.split("&")) {
            String name = parameter.contains("=") ? parameter.substring(0, parameter.indexOf('=')) : parameter;
            if (parameter.isEmpty() || PING_PARAMETER.equals(name)) continue;
            parameters.add(parameter);
        }
        return String.join("&", parameters);
    }

    /**
     * El ping de SQLyog (?app): el tunel contesta "TunnelVersion:13.21". Sirve para saber que la URL y el token valen
     * antes de tocar la BD.
     * @throws SQLException si no contesta, contesta otra cosa o falta una extension PHP
     */
    public String getTunnelVersion() throws SQLException {
        String separator = tunnelUri.getRawQuery() == null ? "?" : "&";
        HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(tunnelUri + separator + "app"))
            .timeout(requestTimeout)
            .header("User-Agent", USER_AGENT)
            .GET()
            .build();
        String body = new String(send(httpRequest), StandardCharsets.UTF_8).trim();
        if (!body.startsWith("TunnelVersion:")) throw new SQLException(getUnexpectedBodyMessage(body));
        String version = body.substring("TunnelVersion:".length());
        if (version.contains("PHP_MODULE_NOT_INSTALLED")) throw new SQLException("tunnel: a PHP extension is missing (pdo, dom or pdo_mysql)");
        return version;
    }

    /**
     * Ejecuta el SQL en la BD dada (vacia = ninguna). isBatch: varias sentencias separadas por ";" que el tunel
     * ejecuta en orden en la misma conexion, devolviendo solo el resultado de la ultima; si una falla, para ahi
     * (y lo no confirmado se deshace al cerrar su conexion).
     * @throws SQLException con el numero y el mensaje del MySQL si la sentencia falla
     */
    public List<HttpTunnelResult> execute(String sql, String databaseName, boolean isBatch) throws SQLException {
        HttpRequest httpRequest = HttpRequest.newBuilder(tunnelUri)
            .timeout(requestTimeout)
            .header("User-Agent", USER_AGENT)
            .header("Content-Type", "text/xml; charset=utf-8")
            .POST(HttpRequest.BodyPublishers.ofString(getRequestXml(sql, databaseName, isBatch), StandardCharsets.UTF_8))
            .build();
        return getResults(send(httpRequest));
    }

    /**
     * Cancelar: corta la peticion en curso (el tunel la terminara por su cuenta en el servidor).
     */
    public void cancel() {
        CompletableFuture<?> future = runningRequest.get();
        if (future != null) future.cancel(true);
    }

    private byte[] send(HttpRequest httpRequest) throws SQLException {
        CompletableFuture<HttpResponse<byte[]>> future = httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofByteArray());
        runningRequest.set(future);
        try {
            HttpResponse<byte[]> httpResponse = future.get();
            if (httpResponse.statusCode() == 404) {
                throw new SQLException("tunnel: HTTP 404, check the URL and its access token");
            }
            if (httpResponse.statusCode() != 200) {
                throw new SQLException("tunnel: HTTP " + httpResponse.statusCode());
            }
            return httpResponse.body();
        }
        catch (CancellationException e) {
            throw new SQLException("tunnel: request cancelled", "70100", e);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SQLException("tunnel: request interrupted", "70100", e);
        }
        catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof java.net.http.HttpTimeoutException) {
                throw new SQLTimeoutException("tunnel: no answer in " + requestTimeout.toSeconds() + " s", cause);
            }
            throw new SQLException("tunnel: " + (cause instanceof IOException && cause.getMessage() == null
                ? cause.getClass().getSimpleName() : cause.getMessage()), cause);
        }
        finally {
            runningRequest.compareAndSet(future, null);
        }
    }

    /**
     * Igual que SQLyog: raiz &lt;xml&gt;, todo en base64 (e='1') para no tener que escapar nada.
     */
    private String getRequestXml(String sql, String databaseName, boolean isBatch) {
        return "<xml>"
            + getEncodedTag("host", host)
            + getEncodedTag("user", userName)
            + getEncodedTag("password", userPassword)
            + getEncodedTag("db", databaseName == null ? "" : databaseName)
            + getEncodedTag("charset", CHARSET)
            + getEncodedTag("port", String.valueOf(port))
            + "<query b='" + (isBatch ? "1" : "0") + "' e='1'>" + getBase64(sql) + "</query>"
            + "</xml>";
    }

    private String getEncodedTag(String tagName, String value) {
        return "<" + tagName + " e='1'>" + getBase64(value) + "</" + tagName + ">";
    }

    private String getBase64(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private List<HttpTunnelResult> getResults(byte[] body) throws SQLException {
        Document document = getDocument(body);
        Element root = document.getDocumentElement();
        if (!"xml".equals(root.getTagName())) {
            throw new SQLException(getUnexpectedBodyMessage(new String(body, StandardCharsets.UTF_8)));
        }
        List<HttpTunnelResult> results = new ArrayList<>();
        for (Element resultElement : getChildren(root, "result")) {
            throwIfError(resultElement);
            results.add(getResult(resultElement));
        }
        return results;
    }

    private void throwIfError(Element resultElement) throws SQLException {
        Element errorInfo = getChild(resultElement, "e_i");
        if (errorInfo == null) return;
        Element errorNumber = getChild(errorInfo, "e_n");
        if (errorNumber == null) return;
        int vendorCode;
        try {
            vendorCode = Integer.parseInt(errorNumber.getTextContent().trim());
        }
        catch (NumberFormatException e) {
            vendorCode = 0;
        }
        Element errorDescription = getChild(errorInfo, "e_d");
        throw new SQLException(errorDescription == null ? "tunnel error" : errorDescription.getTextContent(), null, vendorCode);
    }

    private HttpTunnelResult getResult(Element resultElement) {
        List<String> columnNames = new ArrayList<>();
        List<String> columnTypes = new ArrayList<>();
        List<String> columnTables = new ArrayList<>();
        Element fieldInfo = getChild(resultElement, "f_i");
        if (fieldInfo != null) {
            for (Element field : getChildren(fieldInfo, "f")) {
                columnNames.add(getChildText(field, "n"));
                columnTypes.add(getChildText(field, "ty").toLowerCase(java.util.Locale.ROOT));
                columnTables.add(getChildText(field, "t"));
            }
        }
        List<List<byte[]>> rows = new ArrayList<>();
        Element rowInfo = getChild(resultElement, "r_i");
        if (rowInfo != null) {
            for (Element row : getChildren(rowInfo, "r")) {
                List<byte[]> cells = new ArrayList<>(columnNames.size());
                for (Element cell : getChildren(row, "c")) {
                    cells.add(getCellBytes(cell));
                }
                rows.add(cells);
            }
        }
        long affectedRows;
        try {
            affectedRows = Long.parseLong(getChildText(resultElement, "a_r").trim());
        }
        catch (NumberFormatException e) {
            affectedRows = 0;
        }
        return new HttpTunnelResult(
            columnNames,
            columnTypes,
            columnTables,
            rows,
            !columnNames.isEmpty(),
            affectedRows,
            getChildText(resultElement, "i_i"),
            getChildText(resultElement, "s_v")
        );
    }

    /**
     * &lt;c l="0"&gt;(NULL)&lt;/c&gt; es NULL, &lt;c l="0"&gt;_&lt;/c&gt; la cadena vacia y el resto base64.
     */
    private byte[] getCellBytes(Element cell) {
        String content = cell.getTextContent();
        if ("0".equals(cell.getAttribute("l"))) {
            if ("(NULL)".equals(content)) return null;
            return new byte[0];
        }
        return Base64.getMimeDecoder().decode(content);
    }

    /**
     * Sin DOCTYPE ni entidades externas: la respuesta viene de la red.
     */
    private Document getDocument(byte[] body) throws SQLException {
        try {
            DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
            documentBuilderFactory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            documentBuilderFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            documentBuilderFactory.setExpandEntityReferences(false);
            DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
            documentBuilder.setErrorHandler(null);
            return documentBuilder.parse(new ByteArrayInputStream(body));
        }
        catch (ParserConfigurationException | SAXException | IOException e) {
            throw new SQLException(getUnexpectedBodyMessage(new String(body, StandardCharsets.UTF_8)), e);
        }
    }

    /**
     * Lo que no es XML del tunel (una pagina HTML de error, un proxy): se ensena el principio, sin etiquetas.
     */
    private String getUnexpectedBodyMessage(String body) {
        String text = body.replaceAll("<[^>]*>", " ").replaceAll("\\s+", " ").trim();
        if (text.length() > MAX_ERROR_BODY_CHARS) text = text.substring(0, MAX_ERROR_BODY_CHARS) + "...";
        return "tunnel: unexpected answer, is it the SQLyog tunnel? " + (text.isEmpty() ? "(empty)" : text);
    }

    private static List<Element> getChildren(Element parent, String tagName) {
        List<Element> children = new ArrayList<>();
        NodeList childNodes = parent.getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node child = childNodes.item(i);
            if (child instanceof Element element && tagName.equals(element.getTagName())) children.add(element);
        }
        return children;
    }

    private static Element getChild(Element parent, String tagName) {
        List<Element> children = getChildren(parent, tagName);
        return children.isEmpty() ? null : children.getFirst();
    }

    private static String getChildText(Element parent, String tagName) {
        Element child = getChild(parent, tagName);
        return child == null ? "" : child.getTextContent();
    }
}
