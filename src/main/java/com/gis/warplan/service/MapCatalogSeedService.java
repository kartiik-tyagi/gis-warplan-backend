package com.gis.warplan.service;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gis.warplan.domain.GisServer;
import com.gis.warplan.domain.MapLayer;
import com.gis.warplan.domain.MapViewDefaults;
import com.gis.warplan.repository.GisServerRepository;
import com.gis.warplan.repository.MapLayerRepository;
import com.gis.warplan.repository.MapViewDefaultsRepository;

/**
 * Inserts the starter GeoServer catalog only when no servers exist yet.
 * Existing servers/layers/view rows are never overwritten.
 */
@Service
public class MapCatalogSeedService {

    private static final Logger log = LogManager.getLogger(MapCatalogSeedService.class);

    private final GisServerRepository servers;
    private final MapLayerRepository layers;
    private final MapViewDefaultsRepository views;
    private final ObjectMapper objectMapper;

    public MapCatalogSeedService(GisServerRepository servers, MapLayerRepository layers,
            MapViewDefaultsRepository views, ObjectMapper objectMapper) {
        this.servers = servers;
        this.layers = layers;
        this.views = views;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void seedIfEmpty() {
        if (servers.count() > 0) {
            return;
        }
        CatalogSeed seed = load();
        GisServer server = new GisServer();
        server.setCode(seed.server().code());
        server.setName(seed.server().name());
        server.setWmsUrl(seed.server().wmsUrl());
        server.setWfsUrl(seed.server().wfsUrl());
        server.setRestUrl(seed.server().restUrl());
        server.setWorkspaceDefault(seed.server().workspaceDefault());
        server.setEnabled(seed.server().enabled());
        server.setDefaultServer(seed.server().defaultServer());
        server = servers.save(server);

        for (LayerSeed layerSeed : seed.layers()) {
            if (layers.findByCode(layerSeed.code()).isPresent()) {
                continue;
            }
            MapLayer layer = new MapLayer();
            layer.setGisServerId(server.getId());
            layer.setCode(layerSeed.code());
            layer.setLabel(layerSeed.label());
            layer.setGroupName(layerSeed.groupName());
            layer.setWmsLayers(layerSeed.wmsLayers());
            layer.setFormat(layerSeed.format() == null || layerSeed.format().isBlank() ? "image/png" : layerSeed.format());
            layer.setTransparent(layerSeed.transparent());
            layer.setBaseLayer(layerSeed.baseLayer());
            layer.setSortOrder(layerSeed.sortOrder());
            layer.setDefaultVisible(layerSeed.defaultVisible());
            layer.setDefaultOpacity(BigDecimal.valueOf(layerSeed.defaultOpacity() == null ? 1.0 : layerSeed.defaultOpacity()));
            layer.setAttribution(layerSeed.attribution());
            layer.setActive(true);
            layers.save(layer);
        }

        if (views.findFirstByOrderByIdAsc().isEmpty()) {
            MapViewDefaults view = new MapViewDefaults();
            view.setBasemapSource("wms");
            view.setCenterLon(seed.view().centerLon());
            view.setCenterLat(seed.view().centerLat());
            view.setZoom(seed.view().zoom());
            view.setMinZoom(seed.view().minZoom());
            view.setMaxZoom(seed.view().maxZoom());
            view.setDefaultGisServerId(server.getId());
            views.save(view);
        }
        log.info("Seeded map catalog server={} layers={}", server.getCode(), seed.layers().size());
    }

    private CatalogSeed load() {
        try (InputStream in = new ClassPathResource("seed/map-catalog.json").getInputStream()) {
            return objectMapper.readValue(in, CatalogSeed.class);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to load seed/map-catalog.json", ex);
        }
    }

    public record CatalogSeed(ServerSeed server, ViewSeed view, List<LayerSeed> layers) {
    }

    public record ServerSeed(String code, String name, String wmsUrl, String wfsUrl, String restUrl,
            String workspaceDefault, boolean enabled, boolean defaultServer) {
    }

    public record ViewSeed(double centerLon, double centerLat, int zoom, int minZoom, int maxZoom) {
    }

    public record LayerSeed(String code, String label, String groupName, String wmsLayers, String format,
            boolean transparent, boolean baseLayer, int sortOrder, boolean defaultVisible, Double defaultOpacity,
            String attribution) {
    }
}
