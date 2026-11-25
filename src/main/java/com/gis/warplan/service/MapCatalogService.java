package com.gis.warplan.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gis.warplan.domain.GisServer;
import com.gis.warplan.domain.MapLayer;
import com.gis.warplan.domain.MapViewDefaults;
import com.gis.warplan.repository.GisServerRepository;
import com.gis.warplan.repository.MapLayerRepository;
import com.gis.warplan.repository.MapViewDefaultsRepository;
import com.gis.warplan.web.BadRequestException;
import com.gis.warplan.web.ConflictException;
import com.gis.warplan.web.NotFoundException;

@Service
public class MapCatalogService {

    private final GisServerRepository servers;
    private final MapLayerRepository layers;
    private final MapViewDefaultsRepository views;
    private final AuditService audit;

    public MapCatalogService(GisServerRepository servers, MapLayerRepository layers, MapViewDefaultsRepository views,
            AuditService audit) {
        this.servers = servers;
        this.layers = layers;
        this.views = views;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public MapConfigSnapshot config() {
        GisServer server = servers.findFirstByDefaultServerTrueAndEnabledTrueOrderByIdAsc()
                .or(() -> servers.findFirstByEnabledTrueOrderByIdAsc())
                .orElse(null);
        MapViewDefaults view = views.findFirstByOrderByIdAsc().orElse(null);
        List<MapLayer> active = layers.findByActiveTrueOrderBySortOrderAscIdAsc();
        return new MapConfigSnapshot(server, active, view);
    }

    @Transactional(readOnly = true)
    public List<GisServer> listServers() {
        return servers.findAllByOrderByNameAsc();
    }

    @Transactional(readOnly = true)
    public GisServer getServer(Long id) {
        return servers.findById(id).orElseThrow(() -> new NotFoundException("GIS server not found."));
    }

    @Transactional
    public GisServer createServer(String code, String name, String wmsUrl, String wfsUrl, String restUrl,
            String workspaceDefault, Boolean enabled, Boolean defaultServer) {
        String trimmedCode = requireCode(code);
        if (servers.findByCode(trimmedCode).isPresent()) {
            throw new ConflictException("A GIS server with this code already exists.");
        }
        GisServer server = new GisServer();
        applyServer(server, trimmedCode, name, wmsUrl, wfsUrl, restUrl, workspaceDefault, enabled, defaultServer);
        GisServer saved = servers.save(server);
        audit.record("CREATE", "GisServer", saved.getId(), "GIS server " + saved.getCode() + " created", Map.of());
        return saved;
    }

    @Transactional
    public GisServer updateServer(Long id, String code, String name, String wmsUrl, String wfsUrl, String restUrl,
            String workspaceDefault, Boolean enabled, Boolean defaultServer) {
        GisServer server = getServer(id);
        String trimmedCode = requireCode(code);
        servers.findByCode(trimmedCode).filter(other -> !other.getId().equals(id)).ifPresent(other -> {
            throw new ConflictException("A GIS server with this code already exists.");
        });
        applyServer(server, trimmedCode, name, wmsUrl, wfsUrl, restUrl, workspaceDefault, enabled, defaultServer);
        GisServer saved = servers.save(server);
        audit.record("UPDATE", "GisServer", saved.getId(), "GIS server " + saved.getCode() + " updated", Map.of());
        return saved;
    }

    @Transactional
    public void deleteServer(Long id) {
        GisServer server = getServer(id);
        if (layers.countByGisServerId(id) > 0) {
            throw new ConflictException("Remove or reassign map layers before deleting this GIS server.");
        }
        servers.delete(server);
        audit.record("DELETE", "GisServer", id, "GIS server " + server.getCode() + " deleted", Map.of());
    }

    @Transactional(readOnly = true)
    public List<MapLayer> listLayers() {
        return layers.findAllByOrderBySortOrderAscIdAsc();
    }

    @Transactional(readOnly = true)
    public MapLayer getLayer(Long id) {
        return layers.findById(id).orElseThrow(() -> new NotFoundException("Map layer not found."));
    }

    @Transactional
    public MapLayer createLayer(Long gisServerId, String code, String label, String groupName, String wmsLayers,
            String format, Boolean transparent, Boolean baseLayer, Integer sortOrder, Boolean defaultVisible,
            BigDecimal defaultOpacity, Integer minZoom, Integer maxZoom, String cqlFilter, String attribution,
            Boolean active) {
        requireServer(gisServerId);
        String trimmedCode = requireCode(code);
        if (layers.findByCode(trimmedCode).isPresent()) {
            throw new ConflictException("A map layer with this code already exists.");
        }
        MapLayer layer = new MapLayer();
        applyLayer(layer, gisServerId, trimmedCode, label, groupName, wmsLayers, format, transparent, baseLayer,
                sortOrder, defaultVisible, defaultOpacity, minZoom, maxZoom, cqlFilter, attribution, active);
        MapLayer saved = layers.save(layer);
        audit.record("CREATE", "MapLayer", saved.getId(), "Map layer " + saved.getCode() + " created", Map.of());
        return saved;
    }

    @Transactional
    public MapLayer updateLayer(Long id, Long gisServerId, String code, String label, String groupName,
            String wmsLayers, String format, Boolean transparent, Boolean baseLayer, Integer sortOrder,
            Boolean defaultVisible, BigDecimal defaultOpacity, Integer minZoom, Integer maxZoom, String cqlFilter,
            String attribution, Boolean active) {
        MapLayer layer = getLayer(id);
        requireServer(gisServerId);
        String trimmedCode = requireCode(code);
        layers.findByCode(trimmedCode).filter(other -> !other.getId().equals(id)).ifPresent(other -> {
            throw new ConflictException("A map layer with this code already exists.");
        });
        applyLayer(layer, gisServerId, trimmedCode, label, groupName, wmsLayers, format, transparent, baseLayer,
                sortOrder, defaultVisible, defaultOpacity, minZoom, maxZoom, cqlFilter, attribution, active);
        MapLayer saved = layers.save(layer);
        audit.record("UPDATE", "MapLayer", saved.getId(), "Map layer " + saved.getCode() + " updated", Map.of());
        return saved;
    }

    @Transactional
    public void deleteLayer(Long id) {
        MapLayer layer = getLayer(id);
        layers.delete(layer);
        audit.record("DELETE", "MapLayer", id, "Map layer " + layer.getCode() + " deleted", Map.of());
    }

    @Transactional(readOnly = true)
    public MapViewDefaults getViewDefaults() {
        return views.findFirstByOrderByIdAsc().orElseThrow(() -> new NotFoundException("Map view defaults not found."));
    }

    @Transactional
    public MapViewDefaults updateViewDefaults(Double centerLon, Double centerLat, Integer zoom, Integer minZoom,
            Integer maxZoom, Long defaultGisServerId, String dataCrs, String viewCrs, Long elevationLayerId,
            String elevationAttribute, Boolean elevationPositiveUp) {
        MapViewDefaults view = views.findFirstByOrderByIdAsc().orElseGet(MapViewDefaults::new);
        if (defaultGisServerId != null) {
            requireServer(defaultGisServerId);
        }
        if (elevationLayerId != null) {
            getLayer(elevationLayerId);
        }
        view.setBasemapSource("wms");
        view.setCenterLon(centerLon == null ? view.getCenterLon() : centerLon);
        view.setCenterLat(centerLat == null ? view.getCenterLat() : centerLat);
        view.setZoom(zoom == null ? (view.getId() == null ? 5 : view.getZoom()) : zoom);
        view.setMinZoom(minZoom == null ? (view.getId() == null ? 3 : view.getMinZoom()) : minZoom);
        view.setMaxZoom(maxZoom == null ? (view.getId() == null ? 12 : view.getMaxZoom()) : maxZoom);
        view.setDefaultGisServerId(defaultGisServerId);
        view.setDataCrs(normalizeCrs(dataCrs, view.getDataCrs(), "EPSG:4326"));
        view.setViewCrs(normalizeCrs(viewCrs, view.getViewCrs(), "EPSG:3857"));
        view.setElevationLayerId(elevationLayerId);
        view.setElevationAttribute(blankToNull(elevationAttribute));
        if (elevationPositiveUp != null) {
            view.setElevationPositiveUp(elevationPositiveUp);
        }
        if (view.getId() == null && (centerLon == null || centerLat == null)) {
            throw new BadRequestException("Center longitude and latitude are required.");
        }
        MapViewDefaults saved = views.save(view);
        audit.record("UPDATE", "MapViewDefaults", saved.getId(), "Map view defaults updated", Map.of());
        return saved;
    }

    private void applyServer(GisServer server, String code, String name, String wmsUrl, String wfsUrl, String restUrl,
            String workspaceDefault, Boolean enabled, Boolean defaultServer) {
        if (name == null || name.isBlank()) {
            throw new BadRequestException("Name is required.");
        }
        if (wmsUrl == null || wmsUrl.isBlank()) {
            throw new BadRequestException("WMS URL is required.");
        }
        boolean makeDefault = Boolean.TRUE.equals(defaultServer) || servers.count() == 0;
        if (makeDefault) {
            clearDefaultServers(server.getId());
        } else if (Boolean.FALSE.equals(defaultServer) && server.isDefaultServer()) {
            throw new ConflictException("Pick another GIS server as the default instead of unsetting this one.");
        }
        server.setCode(code);
        server.setName(name.trim());
        server.setWmsUrl(wmsUrl.trim());
        server.setWfsUrl(blankToNull(wfsUrl));
        server.setRestUrl(blankToNull(restUrl));
        server.setWorkspaceDefault(blankToNull(workspaceDefault));
        server.setEnabled(enabled == null || enabled);
        if (makeDefault) {
            server.setDefaultServer(true);
        } else if (defaultServer != null) {
            server.setDefaultServer(defaultServer);
        }
    }

    private void applyLayer(MapLayer layer, Long gisServerId, String code, String label, String groupName,
            String wmsLayers, String format, Boolean transparent, Boolean baseLayer, Integer sortOrder,
            Boolean defaultVisible, BigDecimal defaultOpacity, Integer minZoom, Integer maxZoom, String cqlFilter,
            String attribution, Boolean active) {
        if (label == null || label.isBlank()) {
            throw new BadRequestException("Label is required.");
        }
        if (wmsLayers == null || wmsLayers.isBlank()) {
            throw new BadRequestException("WMS layers name is required.");
        }
        layer.setGisServerId(gisServerId);
        layer.setCode(code);
        layer.setLabel(label.trim());
        layer.setGroupName(blankToNull(groupName));
        layer.setWmsLayers(wmsLayers.trim());
        layer.setFormat(format == null || format.isBlank() ? "image/png" : format.trim());
        layer.setTransparent(Boolean.TRUE.equals(transparent));
        layer.setBaseLayer(Boolean.TRUE.equals(baseLayer));
        layer.setSortOrder(sortOrder == null ? 100 : sortOrder);
        layer.setDefaultVisible(defaultVisible == null || defaultVisible);
        layer.setDefaultOpacity(defaultOpacity == null ? BigDecimal.ONE : defaultOpacity);
        layer.setMinZoom(minZoom);
        layer.setMaxZoom(maxZoom);
        layer.setCqlFilter(blankToNull(cqlFilter));
        layer.setAttribution(blankToNull(attribution));
        layer.setActive(active == null || active);
    }

    private void clearDefaultServers(Long keepId) {
        for (GisServer other : servers.findAll()) {
            if (other.isDefaultServer() && (keepId == null || !other.getId().equals(keepId))) {
                other.setDefaultServer(false);
                servers.saveAndFlush(other);
            }
        }
    }

    private void requireServer(Long id) {
        if (id == null || !servers.existsById(id)) {
            throw new BadRequestException("GIS server " + id + " does not exist.");
        }
    }

    private static String normalizeCrs(String requested, String current, String fallback) {
        String value = requested == null || requested.isBlank() ? (current == null || current.isBlank() ? fallback : current) : requested.trim().toUpperCase(java.util.Locale.ROOT);
        if (!value.equals("EPSG:4326") && !value.equals("EPSG:3857")) {
            throw new BadRequestException("The map can use EPSG:4326 or EPSG:3857.");
        }
        return value;
    }

    private static String requireCode(String code) {
        if (code == null || code.isBlank()) {
            throw new BadRequestException("Code is required.");
        }
        return code.trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record MapConfigSnapshot(GisServer server, List<MapLayer> layers, MapViewDefaults view) {
    }
}
