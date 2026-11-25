package com.gis.warplan.service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gis.warplan.domain.GisServer;
import com.gis.warplan.domain.MapLayer;
import com.gis.warplan.domain.MapViewDefaults;
import com.gis.warplan.repository.GisServerRepository;
import com.gis.warplan.repository.MapLayerRepository;

/**
 * Land height and ocean depth from the elevation layer chosen in map settings.
 * The layer name is never assumed: an empty choice means there is no reading.
 */
@Service
public class MapElevationService {

    private static final Logger log = LogManager.getLogger(MapElevationService.class);
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();

    private final MapCatalogService catalog;
    private final MapLayerRepository layers;
    private final GisServerRepository servers;
    private final ObjectMapper json;

    public MapElevationService(MapCatalogService catalog, MapLayerRepository layers, GisServerRepository servers,
            ObjectMapper json) {
        this.catalog = catalog;
        this.layers = layers;
        this.servers = servers;
        this.json = json;
    }

    public record Sample(Double meters, String kind) {
    }

    @Transactional(readOnly = true)
    public Sample read(double latitude, double longitude) {
        if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
            log.warn("map.elevation.rejected lat={} lon={}", latitude, longitude);
            return new Sample(null, null);
        }
        MapViewDefaults view;
        try {
            view = catalog.getViewDefaults();
        } catch (RuntimeException exception) {
            log.warn("map.elevation.unavailable reason={}", exception.getMessage());
            return new Sample(null, null);
        }
        if (view.getElevationLayerId() == null) {
            return new Sample(null, null);
        }
        MapLayer layer = layers.findById(view.getElevationLayerId()).orElse(null);
        GisServer server = layer == null ? null : servers.findById(layer.getGisServerId()).orElse(null);
        if (layer == null || !layer.isActive() || server == null || !server.isEnabled() || server.getWmsUrl() == null) {
            log.warn("map.elevation.layer_unavailable id={}", view.getElevationLayerId());
            return new Sample(null, null);
        }
        Double value = query(server.getWmsUrl(), layer.getWmsLayers(), latitude, longitude, view.getElevationAttribute());
        if (value == null) {
            return new Sample(null, null);
        }
        if (view.isElevationPositiveUp()) {
            if (value < 0) {
                return new Sample(Math.abs(value), "depth");
            }
            return new Sample(value, "height");
        }
        if (value > 0) {
            return new Sample(value, "depth");
        }
        return new Sample(Math.abs(value), "height");
    }

    private Double query(String wmsUrl, String layerName, double latitude, double longitude, String attribute) {
        double pad = 0.02;
        String bbox = (longitude - pad) + "," + (latitude - pad) + "," + (longitude + pad) + "," + (latitude + pad);
        String url = wmsUrl + (wmsUrl.contains("?") ? "&" : "?")
                + "SERVICE=WMS&VERSION=1.1.1&REQUEST=GetFeatureInfo"
                + "&LAYERS=" + encode(layerName)
                + "&QUERY_LAYERS=" + encode(layerName)
                + "&STYLES=&SRS=EPSG:4326&BBOX=" + encode(bbox)
                + "&WIDTH=101&HEIGHT=101&X=50&Y=50&FEATURE_COUNT=1&INFO_FORMAT=" + encode("application/json");
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(6)).GET().build();
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("map.elevation.upstream status={}", response.statusCode());
                return null;
            }
            return numberFrom(response.body(), attribute);
        } catch (Exception exception) {
            log.warn("map.elevation.upstream_failed reason={}", exception.getMessage());
            return null;
        }
    }

    private Double numberFrom(String body, String attribute) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            JsonNode root = json.readTree(body);
            JsonNode properties = root.path("features").path(0).path("properties");
            if (!properties.isObject()) {
                return null;
            }
            if (attribute != null && !attribute.isBlank()) {
                return finite(properties.get(attribute.trim()));
            }
            Double found = null;
            var fields = properties.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                Double value = finite(field.getValue());
                if (value != null) {
                    found = value;
                    break;
                }
            }
            return found;
        } catch (Exception exception) {
            log.warn("map.elevation.unreadable reason={}", exception.getMessage());
            return null;
        }
    }

    private static Double finite(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            double value = node.doubleValue();
            return Double.isFinite(value) ? value : null;
        }
        if (node.isTextual()) {
            try {
                double value = Double.parseDouble(node.asText().trim());
                return Double.isFinite(value) ? value : null;
            } catch (NumberFormatException exception) {
                return null;
            }
        }
        return null;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
