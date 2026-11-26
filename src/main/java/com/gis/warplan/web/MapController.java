package com.gis.warplan.web;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.gis.warplan.domain.FunctionalityCodes;
import com.gis.warplan.domain.GisServer;
import com.gis.warplan.domain.MapLayer;
import com.gis.warplan.domain.MapViewDefaults;
import com.gis.warplan.service.AccessService;
import com.gis.warplan.service.MapCatalogService;
import com.gis.warplan.service.MapElevationService;
import com.gis.warplan.service.MapCatalogService.MapConfigSnapshot;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Signed-in users can read the map catalog used to build the picture.
 * Changes need MANAGE_MAP. REST credentials are never returned.
 */
@RestController
@RequestMapping("/api/v1/map")
public class MapController {

    private final MapCatalogService catalog;
    private final MapElevationService elevation;
    private final AccessService access;
    private final com.gis.warplan.service.GeoServerConnectionService geoConnections;

    public MapController(MapCatalogService catalog, MapElevationService elevation, AccessService access,
            com.gis.warplan.service.GeoServerConnectionService geoConnections) {
        this.catalog = catalog;
        this.elevation = elevation;
        this.access = access;
        this.geoConnections = geoConnections;
    }

    @GetMapping("/config")
    public MapConfigResponse config() {
        access.requirePrincipal();
        return MapConfigResponse.of(catalog.config());
    }

    @GetMapping("/servers")
    public List<GisServerResponse> listServers() {
        access.requireFunctionality(FunctionalityCodes.MANAGE_MAP);
        return catalog.listServers().stream().map(GisServerResponse::of).toList();
    }

    @PostMapping("/servers")
    public ResponseEntity<GisServerResponse> createServer(@Valid @RequestBody GisServerRequest request) {
        access.requireFunctionality(FunctionalityCodes.MANAGE_MAP);
        GisServer server = catalog.createServer(request.code(), request.name(), request.wmsUrl(), request.wfsUrl(),
                request.restUrl(), request.workspaceDefault(), request.enabled(), request.defaultServer(),
                request.restUsername(), request.restPassword());
        return ResponseEntity.status(HttpStatus.CREATED).body(GisServerResponse.of(server));
    }

    @PutMapping("/servers/{id}")
    public GisServerResponse updateServer(@PathVariable Long id, @Valid @RequestBody GisServerRequest request) {
        access.requireFunctionality(FunctionalityCodes.MANAGE_MAP);
        return GisServerResponse.of(catalog.updateServer(id, request.code(), request.name(), request.wmsUrl(),
                request.wfsUrl(), request.restUrl(), request.workspaceDefault(), request.enabled(),
                request.defaultServer(), request.restUsername(), request.restPassword()));
    }

    @PostMapping("/servers/{id}/test-connection")
    public java.util.Map<String, Object> testConnection(@PathVariable Long id) {
        access.requireFunctionality(FunctionalityCodes.MANAGE_MAP);
        return geoConnections.testConnection(id);
    }

    @DeleteMapping("/servers/{id}")
    public ResponseEntity<Void> deleteServer(@PathVariable Long id) {
        access.requireFunctionality(FunctionalityCodes.MANAGE_MAP);
        catalog.deleteServer(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/layers")
    public List<MapLayerResponse> listLayers() {
        access.requireFunctionality(FunctionalityCodes.MANAGE_MAP);
        return catalog.listLayers().stream().map(MapLayerResponse::of).toList();
    }

    @PostMapping("/layers")
    public ResponseEntity<MapLayerResponse> createLayer(@Valid @RequestBody MapLayerRequest request) {
        access.requireFunctionality(FunctionalityCodes.MANAGE_MAP);
        MapLayer layer = catalog.createLayer(request.gisServerId(), request.code(), request.label(),
                request.groupName(), request.wmsLayers(), request.format(), request.transparent(), request.baseLayer(),
                request.sortOrder(), request.defaultVisible(), request.defaultOpacity(), request.minZoom(),
                request.maxZoom(), request.cqlFilter(), request.attribution(), request.active());
        return ResponseEntity.status(HttpStatus.CREATED).body(MapLayerResponse.of(layer));
    }

    @PutMapping("/layers/{id}")
    public MapLayerResponse updateLayer(@PathVariable Long id, @Valid @RequestBody MapLayerRequest request) {
        access.requireFunctionality(FunctionalityCodes.MANAGE_MAP);
        return MapLayerResponse.of(catalog.updateLayer(id, request.gisServerId(), request.code(), request.label(),
                request.groupName(), request.wmsLayers(), request.format(), request.transparent(), request.baseLayer(),
                request.sortOrder(), request.defaultVisible(), request.defaultOpacity(), request.minZoom(),
                request.maxZoom(), request.cqlFilter(), request.attribution(), request.active()));
    }

    @DeleteMapping("/layers/{id}")
    public ResponseEntity<Void> deleteLayer(@PathVariable Long id) {
        access.requireFunctionality(FunctionalityCodes.MANAGE_MAP);
        catalog.deleteLayer(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/elevation")
    public ElevationResponse elevation(@RequestParam("latitude") double latitude, @RequestParam("longitude") double longitude) {
        access.requireFunctionality(FunctionalityCodes.MAP_VIEW);
        MapElevationService.Sample sample = elevation.read(latitude, longitude);
        return new ElevationResponse(sample.meters(), sample.kind());
    }

    @GetMapping("/view-defaults")
    public MapViewDefaultsResponse getViewDefaults() {
        access.requireFunctionality(FunctionalityCodes.MANAGE_MAP);
        return MapViewDefaultsResponse.of(catalog.getViewDefaults());
    }

    @PutMapping("/view-defaults")
    public MapViewDefaultsResponse updateViewDefaults(@Valid @RequestBody MapViewDefaultsRequest request) {
        access.requireFunctionality(FunctionalityCodes.MANAGE_MAP);
        return MapViewDefaultsResponse.of(catalog.updateViewDefaults(request.centerLon(), request.centerLat(),
                request.zoom(), request.minZoom(), request.maxZoom(), request.defaultGisServerId(), request.dataCrs(),
                request.viewCrs(), request.elevationLayerId(), request.elevationAttribute(), request.elevationPositiveUp()));
    }

    public record GisServerRequest(
            @NotBlank @Size(max = 64) String code,
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Size(max = 500) String wmsUrl,
            @Size(max = 500) String wfsUrl,
            @Size(max = 500) String restUrl,
            @Size(max = 120) String workspaceDefault,
            Boolean enabled,
            Boolean defaultServer,
            @Size(max = 120) String restUsername,
            @Size(max = 500) String restPassword) {
    }

    public record MapLayerRequest(
            @NotNull Long gisServerId,
            @NotBlank @Size(max = 64) String code,
            @NotBlank @Size(max = 120) String label,
            @Size(max = 80) String groupName,
            @NotBlank @Size(max = 255) String wmsLayers,
            @Size(max = 64) String format,
            Boolean transparent,
            Boolean baseLayer,
            Integer sortOrder,
            Boolean defaultVisible,
            BigDecimal defaultOpacity,
            Integer minZoom,
            Integer maxZoom,
            @Size(max = 500) String cqlFilter,
            @Size(max = 255) String attribution,
            Boolean active) {
    }

    public record MapViewDefaultsRequest(
            Double centerLon,
            Double centerLat,
            Integer zoom,
            Integer minZoom,
            Integer maxZoom,
            Long defaultGisServerId,
            @Size(max = 32) String dataCrs,
            @Size(max = 32) String viewCrs,
            Long elevationLayerId,
            @Size(max = 80) String elevationAttribute,
            Boolean elevationPositiveUp) {
    }

    public record ElevationResponse(Double meters, String kind) {
    }

    public record GroupMemberResponse(Long id, String code, String label, String wmsLayers, int sortOrder,
            String styleName, boolean defaultVisible, BigDecimal defaultOpacity, boolean transparent, boolean baseLayer,
            Integer minZoom, Integer maxZoom) {
        static GroupMemberResponse of(MapCatalogService.GroupMemberConfig member) {
            return new GroupMemberResponse(member.id(), member.code(), member.label(), member.wmsLayers(),
                    member.sortOrder(), member.styleName(), member.defaultVisible(), member.defaultOpacity(),
                    member.transparent(), member.baseLayer(), member.minZoom(), member.maxZoom());
        }
    }

    public record GroupResponse(Long id, String code, String label, String wmsLayers, int sortOrder,
            boolean defaultVisible, List<GroupMemberResponse> members) {
        static GroupResponse of(MapCatalogService.GroupConfig group) {
            return new GroupResponse(group.id(), group.code(), group.label(), group.wmsLayers(), group.sortOrder(),
                    group.defaultVisible(),
                    group.members().stream().map(GroupMemberResponse::of).toList());
        }
    }

    public record MapConfigResponse(GisServerPublic server, List<MapLayerResponse> layers, MapViewDefaultsResponse view,
            List<GroupResponse> groups) {
        static MapConfigResponse of(MapConfigSnapshot snapshot) {
            return new MapConfigResponse(
                    snapshot.server() == null ? null : GisServerPublic.of(snapshot.server()),
                    snapshot.layers().stream().map(MapLayerResponse::of).toList(),
                    snapshot.view() == null ? null : MapViewDefaultsResponse.of(snapshot.view()),
                    snapshot.groups() == null ? List.of()
                            : snapshot.groups().stream().map(GroupResponse::of).toList());
        }
    }

    public record GisServerPublic(Long id, String code, String name, String wmsUrl, String wfsUrl,
            String workspaceDefault, boolean enabled, boolean defaultServer) {
        static GisServerPublic of(GisServer server) {
            return new GisServerPublic(server.getId(), server.getCode(), server.getName(), server.getWmsUrl(),
                    server.getWfsUrl(), server.getWorkspaceDefault(), server.isEnabled(), server.isDefaultServer());
        }
    }

    public record GisServerResponse(Long id, String code, String name, String wmsUrl, String wfsUrl, String restUrl,
            String restUsername, boolean restConfigured, String workspaceDefault, boolean enabled, boolean defaultServer,
            Instant createdAt, Instant updatedAt) {
        static GisServerResponse of(GisServer server) {
            return new GisServerResponse(server.getId(), server.getCode(), server.getName(), server.getWmsUrl(),
                    server.getWfsUrl(), server.getRestUrl(), server.getRestUsername(),
                    MapCatalogService.restConfigured(server), server.getWorkspaceDefault(), server.isEnabled(),
                    server.isDefaultServer(), server.getCreatedAt(), server.getUpdatedAt());
        }
    }

    public record MapLayerResponse(Long id, Long gisServerId, String code, String label, String groupName,
            String wmsLayers, String format, boolean transparent, boolean baseLayer, int sortOrder,
            boolean defaultVisible, BigDecimal defaultOpacity, Integer minZoom, Integer maxZoom, String cqlFilter,
            String attribution, boolean active, Instant createdAt, Instant updatedAt) {
        static MapLayerResponse of(MapLayer layer) {
            return new MapLayerResponse(layer.getId(), layer.getGisServerId(), layer.getCode(), layer.getLabel(),
                    layer.getGroupName(), layer.getWmsLayers(), layer.getFormat(), layer.isTransparent(),
                    layer.isBaseLayer(), layer.getSortOrder(), layer.isDefaultVisible(), layer.getDefaultOpacity(),
                    layer.getMinZoom(), layer.getMaxZoom(), layer.getCqlFilter(), layer.getAttribution(),
                    layer.isActive(), layer.getCreatedAt(), layer.getUpdatedAt());
        }
    }

    public record MapViewDefaultsResponse(Long id, double centerLon, double centerLat, int zoom, int minZoom,
            int maxZoom, Long defaultGisServerId, String dataCrs, String viewCrs, Long elevationLayerId,
            String elevationAttribute, boolean elevationPositiveUp, Instant createdAt, Instant updatedAt) {
        static MapViewDefaultsResponse of(MapViewDefaults view) {
            return new MapViewDefaultsResponse(view.getId(), view.getCenterLon(), view.getCenterLat(), view.getZoom(),
                    view.getMinZoom(), view.getMaxZoom(), view.getDefaultGisServerId(),
                    view.getDataCrs() == null || view.getDataCrs().isBlank() ? "EPSG:4326" : view.getDataCrs(),
                    view.getViewCrs() == null || view.getViewCrs().isBlank() ? "EPSG:3857" : view.getViewCrs(),
                    view.getElevationLayerId(), view.getElevationAttribute(), view.isElevationPositiveUp(),
                    view.getCreatedAt(), view.getUpdatedAt());
        }
    }
}
