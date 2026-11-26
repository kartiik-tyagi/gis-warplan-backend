package com.gis.warplan.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gis.warplan.domain.GisServer;
import com.gis.warplan.domain.MapLayer;
import com.gis.warplan.domain.MapLayerGroup;
import com.gis.warplan.domain.MapLayerGroupMember;
import com.gis.warplan.repository.MapLayerGroupMemberRepository;
import com.gis.warplan.repository.MapLayerGroupRepository;
import com.gis.warplan.repository.MapLayerRepository;
import com.gis.warplan.service.geoserver.GeoServerRestClient;
import com.gis.warplan.service.geoserver.GeoServerRestClient.Connection;
import com.gis.warplan.web.BadRequestException;
import com.gis.warplan.web.ConflictException;
import com.gis.warplan.web.NotFoundException;

@Service
public class MapLayerGroupService {

    private final MapLayerGroupRepository groups;
    private final MapLayerGroupMemberRepository members;
    private final MapLayerRepository layers;
    private final MapCatalogService catalog;
    private final GeoServerConnectionService connections;
    private final GeoServerRestClient client;
    private final AuditService audit;

    public MapLayerGroupService(MapLayerGroupRepository groups, MapLayerGroupMemberRepository members,
            MapLayerRepository layers, MapCatalogService catalog, GeoServerConnectionService connections,
            GeoServerRestClient client, AuditService audit) {
        this.groups = groups;
        this.members = members;
        this.layers = layers;
        this.catalog = catalog;
        this.connections = connections;
        this.client = client;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<MapLayerGroup> list() {
        return groups.findAllByOrderBySortOrderAscIdAsc();
    }

    @Transactional(readOnly = true)
    public List<MapLayerGroup> listActive() {
        return groups.findByActiveTrueOrderBySortOrderAscIdAsc();
    }

    @Transactional(readOnly = true)
    public MapLayerGroup get(Long id) {
        return groups.findById(id).orElseThrow(() -> new NotFoundException("Layer group not found."));
    }

    @Transactional(readOnly = true)
    public List<MapLayerGroupMember> membersOf(Long groupId) {
        return members.findByGroupIdOrderBySortOrderAscIdAsc(groupId);
    }

    @Transactional
    public MapLayerGroup create(Long gisServerId, String code, String label, String gsLayerGroupName, Integer sortOrder,
            Boolean defaultVisible, Boolean active, List<MemberInput> memberInputs, boolean syncToGeoServer) {
        catalog.getServer(gisServerId);
        String trimmed = requireCode(code);
        if (groups.findByCodeIgnoreCase(trimmed).isPresent()) {
            throw new ConflictException("A layer group with this code already exists.");
        }
        if (label != null && !label.isBlank()
                && groups.findByGisServerIdAndLabelIgnoreCase(gisServerId, label.trim()).isPresent()) {
            throw new ConflictException("A layer group with this name already exists for this server.");
        }
        MapLayerGroup group = new MapLayerGroup();
        apply(group, gisServerId, trimmed, label, gsLayerGroupName, sortOrder, defaultVisible, active);
        MapLayerGroup saved = groups.save(group);
        replaceMembers(saved, memberInputs);
        if (syncToGeoServer) {
            syncGroupToGeoServer(saved.getId());
        }
        audit.record("CREATE", "MapLayerGroup", saved.getId(), "Layer group " + saved.getCode() + " created", Map.of());
        return saved;
    }

    @Transactional
    public MapLayerGroup update(Long id, Long gisServerId, String code, String label, String gsLayerGroupName,
            Integer sortOrder, Boolean defaultVisible, Boolean active, List<MemberInput> memberInputs,
            boolean syncToGeoServer) {
        MapLayerGroup group = get(id);
        catalog.getServer(gisServerId);
        String trimmed = requireCode(code);
        groups.findByCodeIgnoreCase(trimmed).filter(other -> !other.getId().equals(id)).ifPresent(other -> {
            throw new ConflictException("A layer group with this code already exists.");
        });
        if (label != null && !label.isBlank()) {
            groups.findByGisServerIdAndLabelIgnoreCase(gisServerId, label.trim())
                    .filter(other -> !other.getId().equals(id))
                    .ifPresent(other -> {
                        throw new ConflictException("A layer group with this name already exists for this server.");
                    });
        }
        apply(group, gisServerId, trimmed, label, gsLayerGroupName, sortOrder, defaultVisible, active);
        MapLayerGroup saved = groups.save(group);
        if (memberInputs != null) {
            replaceMembers(saved, memberInputs);
        }
        if (syncToGeoServer) {
            syncGroupToGeoServer(saved.getId());
        }
        audit.record("UPDATE", "MapLayerGroup", saved.getId(), "Layer group " + saved.getCode() + " updated", Map.of());
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        MapLayerGroup group = get(id);
        members.deleteByGroupId(id);
        groups.delete(group);
        audit.record("DELETE", "MapLayerGroup", id, "Layer group " + group.getCode() + " deleted", Map.of());
    }

    @Transactional
    public MapLayerGroup upsertFromPublish(Long gisServerId, String groupCode, String label, String gsLayerGroupName,
            List<Long> mapLayerIds) {
        return upsertFromPublish(gisServerId, groupCode, label, gsLayerGroupName, mapLayerIds, true);
    }

    /**
     * @param syncToGeoServer when false, only updates Warplan catalog (used when importing a group
     *                        that already exists on GeoServer — rewriting it triggers GS catalog bugs).
     */
    @Transactional
    public MapLayerGroup upsertFromPublish(Long gisServerId, String groupCode, String label, String gsLayerGroupName,
            List<Long> mapLayerIds, boolean syncToGeoServer) {
        MapLayerGroup group = resolveExisting(gisServerId, groupCode, label).orElseGet(MapLayerGroup::new);
        boolean creating = group.getId() == null;
        String code = creating ? uniqueCode(slug(groupCode == null || groupCode.isBlank() ? label : groupCode))
                : group.getCode();
        String resolvedLabel = label == null || label.isBlank()
                ? (group.getLabel() == null || group.getLabel().isBlank() ? code : group.getLabel())
                : label.trim();
        apply(group, gisServerId, code, resolvedLabel, gsLayerGroupName,
                creating ? 100 : group.getSortOrder(), creating ? false : group.isDefaultVisible(), true);
        MapLayerGroup saved = groups.save(group);
        List<MemberInput> inputs = new ArrayList<>();
        int order = 10;
        for (Long layerId : mapLayerIds) {
            inputs.add(new MemberInput(layerId, order, null));
            order += 10;
        }
        replaceMembers(saved, inputs);
        for (Long layerId : mapLayerIds) {
            layers.findById(layerId).ifPresent(layer -> {
                layer.setGroupName(saved.getLabel());
                layers.save(layer);
            });
        }
        if (syncToGeoServer) {
            syncGroupToGeoServer(saved.getId());
        }
        return saved;
    }

    /**
     * Resolve an existing group by code or label (case-insensitive) for the server, or create one.
     * Merges {@code mapLayerIds} into the group without removing current members.
     * Prevents duplicate group names on the same GIS server.
     */
    @Transactional
    public MapLayerGroup assignLayers(Long gisServerId, String typedName, List<Long> mapLayerIds, boolean syncToGeoServer) {
        if (typedName == null || typedName.isBlank()) {
            throw new BadRequestException("Group name is required.");
        }
        if (mapLayerIds == null || mapLayerIds.isEmpty()) {
            throw new BadRequestException("At least one layer is required.");
        }
        String trimmed = typedName.trim();
        MapLayerGroup group = resolveExisting(gisServerId, trimmed, trimmed).orElseGet(MapLayerGroup::new);
        boolean creating = group.getId() == null;
        if (creating) {
            apply(group, gisServerId, uniqueCode(slug(trimmed)), trimmed, null, 100, false, true);
            group = groups.save(group);
        }
        List<MapLayerGroupMember> current = membersOf(group.getId());
        java.util.LinkedHashSet<Long> ordered = new java.util.LinkedHashSet<>();
        for (MapLayerGroupMember member : current) {
            ordered.add(member.getMapLayerId());
        }
        ordered.addAll(mapLayerIds);
        List<MemberInput> inputs = new ArrayList<>();
        int order = 10;
        for (Long layerId : ordered) {
            inputs.add(new MemberInput(layerId, order, null));
            order += 10;
        }
        replaceMembers(group, inputs);
        MapLayerGroup stored = group;
        for (Long layerId : ordered) {
            layers.findById(layerId).ifPresent(layer -> {
                layer.setGroupName(stored.getLabel());
                layers.save(layer);
            });
        }
        if (syncToGeoServer) {
            syncGroupToGeoServer(group.getId());
        }
        return group;
    }

    @Transactional(readOnly = true)
    public java.util.Optional<MapLayerGroup> resolveExisting(Long gisServerId, String codeOrLabel, String label) {
        if (codeOrLabel != null && !codeOrLabel.isBlank()) {
            var byCode = groups.findByCodeIgnoreCase(codeOrLabel.trim());
            if (byCode.isPresent() && byCode.get().getGisServerId().equals(gisServerId)) {
                return byCode;
            }
        }
        String labelValue = label != null && !label.isBlank() ? label.trim() : codeOrLabel;
        if (labelValue != null && !labelValue.isBlank()) {
            return groups.findByGisServerIdAndLabelIgnoreCase(gisServerId, labelValue);
        }
        return java.util.Optional.empty();
    }

    private String uniqueCode(String base) {
        String code = base == null || base.isBlank() ? "group" : base;
        int i = 2;
        String candidate = code;
        while (groups.findByCodeIgnoreCase(candidate).isPresent()) {
            candidate = code + "-" + i++;
        }
        return candidate;
    }

    private static String slug(String value) {
        String cleaned = value == null ? "group" : value.trim().toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9_\\-]+", "-")
                .replaceAll("^-+|-+$", "");
        return cleaned.isBlank() ? "group" : cleaned;
    }

    @Transactional
    public void syncGroupToGeoServer(Long groupId) {
        MapLayerGroup group = get(groupId);
        GisServer server = catalog.getServer(group.getGisServerId());
        List<MapLayerGroupMember> memberRows = membersOf(groupId);
        if (memberRows.isEmpty()) {
            return;
        }
        List<String> layerNames = new ArrayList<>();
        List<String> styles = new ArrayList<>();
        for (MapLayerGroupMember member : memberRows) {
            MapLayer layer = layers.findById(member.getMapLayerId())
                    .orElseThrow(() -> new BadRequestException("Group member layer missing: " + member.getMapLayerId()));
            layerNames.add(layer.getWmsLayers());
            styles.add(member.getStyleName() == null ? "" : member.getStyleName());
        }
        String workspace = server.getWorkspaceDefault() == null || server.getWorkspaceDefault().isBlank()
                ? "ne"
                : server.getWorkspaceDefault();
        String gsName = group.getGsLayerGroupName() == null || group.getGsLayerGroupName().isBlank()
                ? group.getCode()
                : stripWorkspace(group.getGsLayerGroupName());
        Connection connection = connections.connectionFor(server);
        client.createOrUpdateLayerGroup(connection, workspace, gsName, layerNames, styles);
        group.setGsLayerGroupName(workspace + ":" + gsName);
        groups.save(group);
    }

    private void replaceMembers(MapLayerGroup group, List<MemberInput> memberInputs) {
        members.deleteByGroupId(group.getId());
        if (memberInputs == null) {
            return;
        }
        int fallbackOrder = 10;
        for (MemberInput input : memberInputs) {
            if (input == null || input.mapLayerId() == null) {
                continue;
            }
            MapLayer layer = layers.findById(input.mapLayerId())
                    .orElseThrow(() -> new BadRequestException("Map layer " + input.mapLayerId() + " does not exist."));
            if (!layer.getGisServerId().equals(group.getGisServerId())) {
                throw new BadRequestException("Layer " + layer.getCode() + " belongs to a different GIS server.");
            }
            MapLayerGroupMember member = new MapLayerGroupMember();
            member.setGroupId(group.getId());
            member.setMapLayerId(input.mapLayerId());
            member.setSortOrder(input.sortOrder() == null ? fallbackOrder : input.sortOrder());
            member.setStyleName(blankToNull(input.styleName()));
            members.save(member);
            fallbackOrder += 10;
        }
    }

    private void apply(MapLayerGroup group, Long gisServerId, String code, String label, String gsLayerGroupName,
            Integer sortOrder, Boolean defaultVisible, Boolean active) {
        if (label == null || label.isBlank()) {
            throw new BadRequestException("Label is required.");
        }
        group.setGisServerId(gisServerId);
        group.setCode(code);
        group.setLabel(label.trim());
        group.setGsLayerGroupName(blankToNull(gsLayerGroupName));
        group.setSortOrder(sortOrder == null ? 100 : sortOrder);
        group.setDefaultVisible(Boolean.TRUE.equals(defaultVisible));
        group.setActive(active == null || active);
    }

    private static String stripWorkspace(String name) {
        int idx = name.indexOf(':');
        return idx >= 0 ? name.substring(idx + 1) : name;
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

    public record MemberInput(Long mapLayerId, Integer sortOrder, String styleName) {
    }

    public record GroupSnapshot(MapLayerGroup group, List<MapLayerGroupMember> members, List<MapLayer> layers) {
    }

    @Transactional(readOnly = true)
    public List<GroupSnapshot> activeSnapshots() {
        List<GroupSnapshot> out = new ArrayList<>();
        for (MapLayerGroup group : listActive()) {
            List<MapLayerGroupMember> memberRows = membersOf(group.getId());
            List<MapLayer> layerRows = new ArrayList<>();
            for (MapLayerGroupMember member : memberRows) {
                layers.findById(member.getMapLayerId()).ifPresent(layerRows::add);
            }
            out.add(new GroupSnapshot(group, memberRows, layerRows));
        }
        return out;
    }
}
