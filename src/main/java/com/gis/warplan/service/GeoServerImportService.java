package com.gis.warplan.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gis.warplan.domain.GisServer;
import com.gis.warplan.domain.GsStyle;
import com.gis.warplan.domain.MapLayer;
import com.gis.warplan.domain.MapLayerGroup;
import com.gis.warplan.repository.GsStyleRepository;
import com.gis.warplan.repository.MapLayerRepository;
import com.gis.warplan.service.geoserver.GeoServerRestClient;
import com.gis.warplan.service.geoserver.GeoServerRestClient.Connection;
import com.gis.warplan.web.BadRequestException;

@Service
public class GeoServerImportService {

    private final MapCatalogService catalog;
    private final MapLayerRepository layers;
    private final MapLayerGroupService groups;
    private final GeoServerConnectionService connections;
    private final GeoServerRestClient client;
    private final GsStyleRepository styles;
    private final AuditService audit;

    public GeoServerImportService(MapCatalogService catalog, MapLayerRepository layers, MapLayerGroupService groups,
            GeoServerConnectionService connections, GeoServerRestClient client, GsStyleRepository styles,
            AuditService audit) {
        this.catalog = catalog;
        this.layers = layers;
        this.groups = groups;
        this.connections = connections;
        this.client = client;
        this.styles = styles;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> discover(Long gisServerId) {
        GisServer server = catalog.getServer(gisServerId);
        Connection connection = connections.connectionFor(server);
        Set<String> knownLayers = layers.findByGisServerIdOrderBySortOrderAscIdAsc(gisServerId).stream()
                .map(MapLayer::getWmsLayers).collect(Collectors.toSet());
        Set<String> knownGroups = groups.list().stream()
                .filter(group -> group.getGisServerId().equals(gisServerId))
                .map(group -> group.getGsLayerGroupName() == null ? group.getCode() : group.getGsLayerGroupName())
                .collect(Collectors.toSet());

        List<Map<String, Object>> layerRows = new ArrayList<>();
        for (String name : client.listLayers(connection)) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", name);
            row.put("inApp", knownLayers.contains(name));
            layerRows.add(row);
        }
        String workspace = server.getWorkspaceDefault();
        List<Map<String, Object>> groupRows = new ArrayList<>();
        for (String name : client.listLayerGroups(connection, workspace)) {
            String qualified = name.contains(":") ? name
                    : (workspace == null || workspace.isBlank() ? name : workspace + ":" + name);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", name);
            row.put("qualifiedName", qualified);
            row.put("inApp", knownGroups.contains(name) || knownGroups.contains(qualified));
            groupRows.add(row);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("workspaces", client.listWorkspaces(connection));
        result.put("layers", layerRows);
        result.put("layerGroups", groupRows);
        result.put("styles", client.listStyles(connection));
        result.put("stores", workspace == null || workspace.isBlank() ? List.of()
                : client.listDatastores(connection, workspace));
        result.put("appGroups", groups.list().stream()
                .filter(group -> group.getGisServerId().equals(gisServerId))
                .map(group -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", group.getId());
                    row.put("code", group.getCode());
                    row.put("label", group.getLabel());
                    return row;
                }).toList());
        return result;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getLayerGroup(Long gisServerId, String name) {
        GisServer server = catalog.getServer(gisServerId);
        return client.getLayerGroup(connections.connectionFor(server), server.getWorkspaceDefault(), name);
    }

    @Transactional
    public Map<String, Object> importLayers(Long gisServerId, List<ImportLayer> requests) {
        if (requests == null || requests.isEmpty()) {
            throw new BadRequestException("Select at least one layer to import.");
        }
        catalog.getServer(gisServerId);
        int imported = 0;
        int skipped = 0;
        List<Long> ids = new ArrayList<>();
        Map<String, List<Long>> byGroup = new LinkedHashMap<>();
        for (ImportLayer request : requests) {
            if (request == null || request.wmsLayers() == null || request.wmsLayers().isBlank()) {
                continue;
            }
            String wms = request.wmsLayers().trim();
            var existing = layers.findByGisServerIdAndWmsLayers(gisServerId, wms);
            MapLayer layer;
            if (existing.isPresent()) {
                skipped++;
                layer = existing.get();
                if (request.groupCode() != null && !request.groupCode().isBlank()) {
                    layer.setGroupName(request.groupCode().trim());
                    layers.save(layer);
                }
            } else {
                String code = blank(request.code(), codeFrom(wms));
                String label = blank(request.label(), shortName(wms));
                String groupName = blank(request.groupCode(), null);
                layer = catalog.createLayer(gisServerId, uniqueCode(code), label, groupName, wms,
                        "image/png", true, false, 100, false, BigDecimal.ONE, null, null, null, null, true);
                imported++;
            }
            ids.add(layer.getId());
            if (request.groupCode() != null && !request.groupCode().isBlank()) {
                String key = request.groupCode().trim();
                byGroup.computeIfAbsent(key, ignored -> new ArrayList<>()).add(layer.getId());
            }
        }
        int grouped = 0;
        for (Map.Entry<String, List<Long>> entry : byGroup.entrySet()) {
            groups.assignLayers(gisServerId, entry.getKey(), entry.getValue(), true);
            grouped++;
        }
        audit.record("IMPORT", "MapLayer", null,
                "Imported " + imported + " layers (" + skipped + " skipped, " + grouped + " groups)", Map.of());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("imported", imported);
        result.put("skipped", skipped);
        result.put("groupsUpdated", grouped);
        result.put("layerIds", ids);
        return result;
    }

    @Transactional
    public Map<String, Object> importLayerGroups(Long gisServerId, List<ImportGroup> requests) {
        if (requests == null || requests.isEmpty()) {
            throw new BadRequestException("Select at least one layer group to import.");
        }
        GisServer server = catalog.getServer(gisServerId);
        Connection connection = connections.connectionFor(server);
        int imported = 0;
        int skipped = 0;
        List<Long> ids = new ArrayList<>();
        for (ImportGroup request : requests) {
            if (request == null || request.gsName() == null || request.gsName().isBlank()) {
                continue;
            }
            String gsName = request.gsName().trim();
            String shortName = gsName.contains(":") ? gsName.substring(gsName.indexOf(':') + 1) : gsName;
            String preferredLabel = blank(request.label(), shortName);
            String codeHint = blank(request.code(), codeFrom(shortName));
            var existing = groups.resolveExisting(gisServerId, codeHint, preferredLabel);
            if (existing.isPresent()) {
                skipped++;
                ids.add(existing.get().getId());
                continue;
            }
            Map<String, Object> detail = client.getLayerGroup(connection, server.getWorkspaceDefault(), shortName);
            @SuppressWarnings("unchecked")
            List<String> memberNames = (List<String>) detail.getOrDefault("layers", List.of());
            List<Long> memberIds = new ArrayList<>();
            int order = 10;
            for (String memberName : memberNames) {
                String wms = memberName.contains(":") ? memberName
                        : (server.getWorkspaceDefault() == null ? memberName
                                : server.getWorkspaceDefault() + ":" + memberName);
                final int memberOrder = order;
                MapLayer layer = layers.findByGisServerIdAndWmsLayers(gisServerId, wms).orElseGet(() -> catalog
                        .createLayer(gisServerId, uniqueCode(codeFrom(wms)), shortName(wms), preferredLabel, wms,
                                "image/png", true, false, memberOrder, false, BigDecimal.ONE, null, null, null, null,
                                true));
                memberIds.add(layer.getId());
                order += 10;
            }
            String qualified = gsName.contains(":") ? gsName
                    : (server.getWorkspaceDefault() == null || server.getWorkspaceDefault().isBlank() ? shortName
                            : server.getWorkspaceDefault() + ":" + shortName);
            // Catalog-only: the layer group already lives on GeoServer; do not PUT it back
            // (GeoServer can 500 with "Unable to move …xml to …xml" on same-name updates).
            MapLayerGroup group = groups.upsertFromPublish(gisServerId, codeHint, preferredLabel, qualified, memberIds,
                    false);
            imported++;
            ids.add(group.getId());
        }
        audit.record("IMPORT", "MapLayerGroup", null, "Imported " + imported + " layer groups", Map.of());
        return Map.of("imported", imported, "skipped", skipped, "groupIds", ids);
    }

    @Transactional
    public GsStyle uploadStyle(Long gisServerId, String name, String workspace, String sldXml) {
        if (name == null || name.isBlank()) {
            throw new BadRequestException("Style name is required.");
        }
        if (sldXml == null || sldXml.isBlank()) {
            throw new BadRequestException("SLD content is required.");
        }
        GisServer server = catalog.getServer(gisServerId);
        String ws = blank(workspace, server.getWorkspaceDefault(), "ne");
        client.putStyleSld(connections.connectionFor(server), ws, name.trim(), sldXml);
        GsStyle style = styles.findByGisServerIdAndName(gisServerId, name.trim()).orElseGet(GsStyle::new);
        style.setGisServerId(gisServerId);
        style.setName(name.trim());
        style.setWorkspace(ws);
        return styles.save(style);
    }

    @Transactional(readOnly = true)
    public List<GsStyle> listStyles(Long gisServerId) {
        return styles.findByGisServerIdOrderByNameAsc(gisServerId);
    }

    private String uniqueCode(String base) {
        String code = base;
        int i = 2;
        while (layers.findByCode(code).isPresent()) {
            code = base + "-" + i++;
        }
        return code;
    }

    private static String codeFrom(String wms) {
        String shortName = shortName(wms).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_\\-]+", "-");
        return shortName.isBlank() ? "layer" : shortName;
    }

    private static String shortName(String wms) {
        if (wms == null) {
            return "layer";
        }
        int idx = wms.indexOf(':');
        return idx >= 0 ? wms.substring(idx + 1) : wms;
    }

    private static String blank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    public record ImportLayer(String wmsLayers, String code, String label, String groupCode) {
    }

    public record ImportGroup(String gsName, String code, String label) {
    }
}
