package com.gis.warplan.service.geoserver;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Thin GeoServer REST client. All calls use Basic Auth; passwords never leave the server.
 */
@Component
public class GeoServerRestClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(60);

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper mapper;

    public GeoServerRestClient(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public record Connection(String restUrl, String username, String password) {
        String authHeader() {
            String token = Base64.getEncoder()
                    .encodeToString((nullToEmpty(username) + ":" + nullToEmpty(password)).getBytes(StandardCharsets.UTF_8));
            return "Basic " + token;
        }

        String base() {
            String url = restUrl == null ? "" : restUrl.trim();
            while (url.endsWith("/")) {
                url = url.substring(0, url.length() - 1);
            }
            return url;
        }
    }

    public Map<String, Object> aboutVersion(Connection connection) {
        JsonNode node = getJson(connection, "/about/version");
        return mapper.convertValue(node, Map.class);
    }

    public List<String> listWorkspaces(Connection connection) {
        JsonNode root = getJson(connection, "/workspaces.json");
        return names(root.path("workspaces").path("workspace"), "name");
    }

    public void ensureWorkspace(Connection connection, String workspace) {
        if (workspace == null || workspace.isBlank()) {
            throw new GeoServerException("Workspace is required.", 400);
        }
        if (listWorkspaces(connection).stream().anyMatch(workspace::equalsIgnoreCase)) {
            return;
        }
        String body = "{\"workspace\":{\"name\":\"" + escape(workspace) + "\"}}";
        exchange(connection, "POST", "/workspaces", "application/json", body.getBytes(StandardCharsets.UTF_8), 201);
    }

    public List<String> listLayers(Connection connection) {
        JsonNode root = getJson(connection, "/layers.json");
        return names(root.path("layers").path("layer"), "name");
    }

    public List<String> listStyles(Connection connection) {
        JsonNode root = getJson(connection, "/styles.json");
        return names(root.path("styles").path("style"), "name");
    }

    public List<String> listDatastores(Connection connection, String workspace) {
        JsonNode root = getJson(connection, "/workspaces/" + enc(workspace) + "/datastores.json");
        return names(root.path("dataStores").path("dataStore"), "name");
    }

    public List<String> listLayerGroups(Connection connection) {
        JsonNode root = getJson(connection, "/layergroups.json");
        return names(root.path("layerGroups").path("layerGroup"), "name");
    }

    public List<String> listLayerGroups(Connection connection, String workspace) {
        if (workspace == null || workspace.isBlank()) {
            return listLayerGroups(connection);
        }
        JsonNode root = getJson(connection, "/workspaces/" + enc(workspace) + "/layergroups.json");
        return names(root.path("layerGroups").path("layerGroup"), "name");
    }

    public Map<String, Object> getLayerGroup(Connection connection, String workspace, String name) {
        String path = workspace == null || workspace.isBlank()
                ? "/layergroups/" + enc(name) + ".json"
                : "/workspaces/" + enc(workspace) + "/layergroups/" + enc(name) + ".json";
        JsonNode root = getJson(connection, path);
        JsonNode group = root.path("layerGroup");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", text(group, "name"));
        result.put("mode", text(group, "mode"));
        List<String> layers = new ArrayList<>();
        List<String> styles = new ArrayList<>();
        JsonNode published = group.path("publishables").path("published");
        if (published.isArray()) {
            for (JsonNode item : published) {
                layers.add(text(item, "name"));
            }
        } else if (published.isObject()) {
            layers.add(text(published, "name"));
        }
        JsonNode styleNode = group.path("styles").path("style");
        if (styleNode.isArray()) {
            for (JsonNode item : styleNode) {
                styles.add(item.isTextual() ? item.asText() : text(item, "name"));
            }
        } else if (styleNode.isObject() || styleNode.isTextual()) {
            styles.add(styleNode.isTextual() ? styleNode.asText() : text(styleNode, "name"));
        }
        result.put("layers", layers);
        result.put("styles", styles);
        return result;
    }

    public void putStyleSld(Connection connection, String workspace, String styleName, String sldXml) {
        ensureWorkspace(connection, workspace);
        String existsPath = "/workspaces/" + enc(workspace) + "/styles/" + enc(styleName) + ".json";
        boolean exists = exists(connection, existsPath);
        if (!exists) {
            String create = "{\"style\":{\"name\":\"" + escape(styleName) + "\",\"filename\":\"" + escape(styleName)
                    + ".sld\"}}";
            exchange(connection, "POST", "/workspaces/" + enc(workspace) + "/styles", "application/json",
                    create.getBytes(StandardCharsets.UTF_8), 201);
        }
        exchange(connection, "PUT", "/workspaces/" + enc(workspace) + "/styles/" + enc(styleName),
                "application/vnd.ogc.sld+xml", sldXml.getBytes(StandardCharsets.UTF_8), 200);
    }

    public void setLayerDefaultStyle(Connection connection, String workspace, String layerName, String styleName) {
        String qualified = layerName.contains(":") ? layerName : workspace + ":" + layerName;
        String shortName = qualified.contains(":") ? qualified.substring(qualified.indexOf(':') + 1) : qualified;
        String body = "{\"layer\":{\"defaultStyle\":{\"name\":\"" + escape(styleName) + "\"}}}";
        exchange(connection, "PUT", "/layers/" + enc(qualified) + ".json", "application/json",
                body.getBytes(StandardCharsets.UTF_8), 200);
        // Some GeoServer installs prefer workspace-scoped path
        if (!exists(connection, "/layers/" + enc(qualified) + ".json")) {
            exchange(connection, "PUT", "/workspaces/" + enc(workspace) + "/layers/" + enc(shortName) + ".json",
                    "application/json", body.getBytes(StandardCharsets.UTF_8), 200);
        }
    }

    public void uploadShapefileZip(Connection connection, String workspace, String storeName, Path zipFile,
            boolean configureAll) throws IOException {
        ensureWorkspace(connection, workspace);
        byte[] bytes = Files.readAllBytes(zipFile);
        String configure = configureAll ? "all" : "first";
        String path = "/workspaces/" + enc(workspace) + "/datastores/" + enc(storeName) + "/file.shp?configure="
                + configure;
        exchange(connection, "PUT", path, "application/zip", bytes, 201, 200);
    }

    public void uploadGeoJson(Connection connection, String workspace, String storeName, Path geoJsonFile)
            throws IOException {
        ensureWorkspace(connection, workspace);
        byte[] bytes = Files.readAllBytes(geoJsonFile);
        String path = "/workspaces/" + enc(workspace) + "/datastores/" + enc(storeName)
                + "/file.geojson?configure=first";
        exchange(connection, "PUT", path, "application/json", bytes, 201, 200);
    }

    public void uploadGeoTiff(Connection connection, String workspace, String storeName, Path tiffFile)
            throws IOException {
        ensureWorkspace(connection, workspace);
        byte[] bytes = Files.readAllBytes(tiffFile);
        String path = "/workspaces/" + enc(workspace) + "/coveragestores/" + enc(storeName)
                + "/file.geotiff?configure=first";
        exchange(connection, "PUT", path, "image/tiff", bytes, 201, 200);
    }

    public void ensurePostgisDatastore(Connection connection, String workspace, String storeName, String host, int port,
            String database, String schema, String user, String password) {
        ensureWorkspace(connection, workspace);
        if (listDatastores(connection, workspace).stream().anyMatch(storeName::equalsIgnoreCase)) {
            return;
        }
        String body = """
                {"dataStore":{"name":"%s","connectionParameters":{"entry":[
                {"@key":"host","$":"%s"},
                {"@key":"port","$":"%d"},
                {"@key":"database","$":"%s"},
                {"@key":"schema","$":"%s"},
                {"@key":"user","$":"%s"},
                {"@key":"passwd","$":"%s"},
                {"@key":"dbtype","$":"postgis"}
                ]}}}
                """.formatted(escape(storeName), escape(host), port, escape(database), escape(schema == null ? "public" : schema),
                escape(user), escape(password));
        exchange(connection, "POST", "/workspaces/" + enc(workspace) + "/datastores", "application/json",
                body.getBytes(StandardCharsets.UTF_8), 201);
    }

    public List<String> listAvailableFeatureTypes(Connection connection, String workspace, String storeName) {
        JsonNode root = getJson(connection, "/workspaces/" + enc(workspace) + "/datastores/" + enc(storeName)
                + "/featuretypes.json?list=available");
        List<String> out = new ArrayList<>();
        JsonNode list = root.path("list").path("string");
        if (list.isArray()) {
            list.forEach(n -> out.add(n.asText()));
        } else if (list.isTextual()) {
            out.add(list.asText());
        }
        return out;
    }

    public void publishFeatureType(Connection connection, String workspace, String storeName, String typeName,
            String srs) {
        String body = "{\"featureType\":{\"name\":\"" + escape(typeName) + "\",\"enabled\":true,\"srs\":\""
                + escape(srs == null || srs.isBlank() ? "EPSG:4326" : srs) + "\"}}";
        exchange(connection, "POST", "/workspaces/" + enc(workspace) + "/datastores/" + enc(storeName) + "/featuretypes",
                "application/json", body.getBytes(StandardCharsets.UTF_8), 201);
    }

    public void createOrUpdateLayerGroup(Connection connection, String workspace, String groupName, List<String> layers,
            List<String> styles) {
        ensureWorkspace(connection, workspace);
        StringBuilder layerXml = new StringBuilder();
        for (String layer : layers) {
            // Prefer unqualified names inside a workspace-scoped layer group.
            String local = layer.contains(":") ? layer.substring(layer.indexOf(':') + 1) : layer;
            layerXml.append("<layer>").append(escapeXml(local)).append("</layer>");
        }
        StringBuilder styleXml = new StringBuilder();
        for (int i = 0; i < layers.size(); i++) {
            String style = styles != null && i < styles.size() && styles.get(i) != null && !styles.get(i).isBlank()
                    ? styles.get(i)
                    : "";
            styleXml.append("<style>").append(escapeXml(style)).append("</style>");
        }
        String path = "/workspaces/" + enc(workspace) + "/layergroups";
        boolean exists = listLayerGroups(connection, workspace).stream().anyMatch(groupName::equalsIgnoreCase);
        if (exists) {
            // GeoServer catalog blows up with "Unable to move X.xml to X.xml" if <name> is sent on PUT.
            String updateXml = "<layerGroup><mode>SINGLE</mode><layers>" + layerXml + "</layers><styles>"
                    + styleXml + "</styles></layerGroup>";
            try {
                exchange(connection, "PUT", path + "/" + enc(groupName), "text/xml",
                        updateXml.getBytes(StandardCharsets.UTF_8), 200);
            } catch (GeoServerException ex) {
                String message = ex.getMessage() == null ? "" : ex.getMessage();
                if (ex.getStatus() == 500 && message.contains("Unable to move")) {
                    // No-op rename bug on some GeoServer versions; group already exists.
                    return;
                }
                throw ex;
            }
        } else {
            String createXml = "<layerGroup><name>" + escapeXml(groupName) + "</name><mode>SINGLE</mode><layers>"
                    + layerXml + "</layers><styles>" + styleXml + "</styles></layerGroup>";
            exchange(connection, "POST", path, "text/xml", createXml.getBytes(StandardCharsets.UTF_8), 201);
        }
    }

    public boolean layerExists(Connection connection, String qualifiedName) {
        return exists(connection, "/layers/" + enc(qualifiedName) + ".json");
    }

    private JsonNode getJson(Connection connection, String path) {
        HttpResponse<String> response = exchange(connection, "GET", path, null, null, 200);
        try {
            return mapper.readTree(response.body() == null ? "{}" : response.body());
        } catch (IOException ex) {
            throw new GeoServerException("GeoServer returned invalid JSON for " + path, 502);
        }
    }

    private boolean exists(Connection connection, String path) {
        try {
            exchange(connection, "GET", path, null, null, 200);
            return true;
        } catch (GeoServerException ex) {
            if (ex.getStatus() == 404) {
                return false;
            }
            throw ex;
        }
    }

    private HttpResponse<String> exchange(Connection connection, String method, String path, String contentType,
            byte[] body, int... okStatuses) {
        if (connection == null || connection.base().isBlank()) {
            throw new GeoServerException("GeoServer REST URL is not configured.", 400);
        }
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(connection.base() + path))
                    .timeout(TIMEOUT)
                    .header("Accept", "application/json")
                    .header("Authorization", connection.authHeader());
            if (body != null) {
                builder.method(method, HttpRequest.BodyPublishers.ofByteArray(body));
                if (contentType != null) {
                    builder.header("Content-Type", contentType);
                }
            } else {
                builder.method(method, HttpRequest.BodyPublishers.noBody());
            }
            HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            for (int ok : okStatuses) {
                if (status == ok) {
                    return response;
                }
            }
            String detail = response.body();
            if (detail != null && detail.length() > 300) {
                detail = detail.substring(0, 300);
            }
            throw new GeoServerException("GeoServer " + method + " " + path + " failed (" + status + "): "
                    + (detail == null ? "" : detail), status);
        } catch (GeoServerException ex) {
            throw ex;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new GeoServerException("GeoServer request interrupted.", 408);
        } catch (IOException ex) {
            throw new GeoServerException("Could not reach GeoServer: " + ex.getMessage(), 503);
        }
    }

    private static List<String> names(JsonNode node, String field) {
        List<String> out = new ArrayList<>();
        if (node.isArray()) {
            for (JsonNode item : node) {
                String name = text(item, field);
                if (!name.isBlank()) {
                    out.add(name);
                }
            }
        } else if (node.isObject()) {
            String name = text(node, field);
            if (!name.isBlank()) {
                out.add(name);
            }
        }
        return out;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? "" : value.asText("");
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String escapeXml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
