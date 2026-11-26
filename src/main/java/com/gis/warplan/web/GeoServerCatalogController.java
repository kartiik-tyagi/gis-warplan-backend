package com.gis.warplan.web;

import java.time.Instant;
import java.util.List;
import java.util.Map;

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
import com.gis.warplan.domain.GsStyle;
import com.gis.warplan.domain.MapLayer;
import com.gis.warplan.domain.MapLayerGroup;
import com.gis.warplan.domain.MapLayerGroupMember;
import com.gis.warplan.service.AccessService;
import com.gis.warplan.service.GeoServerImportService;
import com.gis.warplan.service.GeoServerImportService.ImportGroup;
import com.gis.warplan.service.GeoServerImportService.ImportLayer;
import com.gis.warplan.service.MapCatalogService;
import com.gis.warplan.service.MapLayerGroupService;
import com.gis.warplan.service.MapLayerGroupService.MemberInput;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/v1/map")
public class GeoServerCatalogController {

    private final AccessService access;
    private final GeoServerImportService imports;
    private final MapLayerGroupService groups;
    private final MapCatalogService catalog;

    public GeoServerCatalogController(AccessService access, GeoServerImportService imports, MapLayerGroupService groups,
            MapCatalogService catalog) {
        this.access = access;
        this.imports = imports;
        this.groups = groups;
        this.catalog = catalog;
    }

    @GetMapping("/gs/discover")
    public Map<String, Object> discover(@RequestParam("gisServerId") Long gisServerId) {
        access.requireFunctionality(FunctionalityCodes.MANAGE_MAP);
        return imports.discover(gisServerId);
    }

    @GetMapping("/gs/workspaces")
    public List<String> workspaces(@RequestParam("gisServerId") Long gisServerId) {
        access.requireFunctionality(FunctionalityCodes.MANAGE_MAP);
        return (List<String>) imports.discover(gisServerId).get("workspaces");
    }

    @GetMapping("/gs/layers")
    public Object layers(@RequestParam("gisServerId") Long gisServerId) {
        access.requireFunctionality(FunctionalityCodes.MANAGE_MAP);
        return imports.discover(gisServerId).get("layers");
    }

    @GetMapping("/gs/styles")
    public Object gsStyles(@RequestParam("gisServerId") Long gisServerId) {
        access.requireFunctionality(FunctionalityCodes.MANAGE_MAP);
        return imports.discover(gisServerId).get("styles");
    }

    @GetMapping("/gs/stores")
    public Object stores(@RequestParam("gisServerId") Long gisServerId) {
        access.requireFunctionality(FunctionalityCodes.MANAGE_MAP);
        return imports.discover(gisServerId).get("stores");
    }

    @GetMapping("/gs/layergroups")
    public Object layerGroups(@RequestParam("gisServerId") Long gisServerId) {
        access.requireFunctionality(FunctionalityCodes.MANAGE_MAP);
        return imports.discover(gisServerId).get("layerGroups");
    }

    @GetMapping("/gs/layergroups/{name}")
    public Map<String, Object> layerGroup(@RequestParam("gisServerId") Long gisServerId, @PathVariable String name) {
        access.requireFunctionality(FunctionalityCodes.MANAGE_MAP);
        return imports.getLayerGroup(gisServerId, name);
    }

    @PostMapping("/gs/import-layers")
    public Map<String, Object> importLayers(@Valid @RequestBody ImportLayersRequest request) {
        access.requireFunctionality(FunctionalityCodes.MANAGE_MAP);
        return imports.importLayers(request.gisServerId(), request.layers());
    }

    @PostMapping("/gs/import-layergroups")
    public Map<String, Object> importLayerGroups(@Valid @RequestBody ImportGroupsRequest request) {
        access.requireFunctionality(FunctionalityCodes.MANAGE_MAP);
        return imports.importLayerGroups(request.gisServerId(), request.groups());
    }

    @GetMapping("/layer-groups")
    public List<LayerGroupResponse> listGroups() {
        access.requireFunctionality(FunctionalityCodes.MANAGE_MAP);
        return groups.list().stream().map(this::toResponse).toList();
    }

    @PostMapping("/layer-groups")
    public ResponseEntity<LayerGroupResponse> createGroup(@Valid @RequestBody LayerGroupRequest request) {
        access.requireFunctionality(FunctionalityCodes.MANAGE_MAP);
        MapLayerGroup group = groups.create(request.gisServerId(), request.code(), request.label(),
                request.gsLayerGroupName(), request.sortOrder(), request.defaultVisible(), request.active(),
                toMembers(request.members()), Boolean.TRUE.equals(request.syncToGeoServer()));
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(group));
    }

    @PutMapping("/layer-groups/{id}")
    public LayerGroupResponse updateGroup(@PathVariable Long id, @Valid @RequestBody LayerGroupRequest request) {
        access.requireFunctionality(FunctionalityCodes.MANAGE_MAP);
        MapLayerGroup group = groups.update(id, request.gisServerId(), request.code(), request.label(),
                request.gsLayerGroupName(), request.sortOrder(), request.defaultVisible(), request.active(),
                toMembers(request.members()), Boolean.TRUE.equals(request.syncToGeoServer()));
        return toResponse(group);
    }

    @DeleteMapping("/layer-groups/{id}")
    public ResponseEntity<Void> deleteGroup(@PathVariable Long id) {
        access.requireFunctionality(FunctionalityCodes.MANAGE_MAP);
        groups.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/layer-groups/{id}/sync")
    public LayerGroupResponse syncGroup(@PathVariable Long id) {
        access.requireFunctionality(FunctionalityCodes.MANAGE_MAP);
        groups.syncGroupToGeoServer(id);
        return toResponse(groups.get(id));
    }

    @PostMapping("/styles")
    public ResponseEntity<StyleResponse> uploadStyle(@Valid @RequestBody StyleUploadRequest request) {
        access.requireFunctionality(FunctionalityCodes.MANAGE_MAP);
        GsStyle style = imports.uploadStyle(request.gisServerId(), request.name(), request.workspace(), request.sldXml());
        return ResponseEntity.status(HttpStatus.CREATED).body(StyleResponse.of(style));
    }

    @GetMapping("/styles")
    public List<StyleResponse> listStyles(@RequestParam("gisServerId") Long gisServerId) {
        access.requireFunctionality(FunctionalityCodes.MANAGE_MAP);
        return imports.listStyles(gisServerId).stream().map(StyleResponse::of).toList();
    }

    private LayerGroupResponse toResponse(MapLayerGroup group) {
        List<MapLayerGroupMember> memberRows = groups.membersOf(group.getId());
        List<LayerGroupMemberResponse> memberResponses = memberRows.stream().map(member -> {
            MapLayer layer = catalog.getLayer(member.getMapLayerId());
            return new LayerGroupMemberResponse(member.getId(), member.getMapLayerId(), layer.getCode(), layer.getLabel(),
                    layer.getWmsLayers(), member.getSortOrder(), member.getStyleName());
        }).toList();
        return new LayerGroupResponse(group.getId(), group.getGisServerId(), group.getCode(), group.getLabel(),
                group.getGsLayerGroupName(), group.getSortOrder(), group.isDefaultVisible(), group.isActive(),
                group.getCreatedAt(), group.getUpdatedAt(), memberResponses);
    }

    private static List<MemberInput> toMembers(List<MemberRequest> members) {
        if (members == null) {
            return List.of();
        }
        return members.stream().map(m -> new MemberInput(m.mapLayerId(), m.sortOrder(), m.styleName())).toList();
    }

    public record ImportLayersRequest(@NotNull Long gisServerId, List<ImportLayer> layers) {
    }

    public record ImportGroupsRequest(@NotNull Long gisServerId, List<ImportGroup> groups) {
    }

    public record MemberRequest(@NotNull Long mapLayerId, Integer sortOrder, String styleName) {
    }

    public record LayerGroupRequest(@NotNull Long gisServerId, @NotBlank @Size(max = 64) String code,
            @NotBlank @Size(max = 120) String label, @Size(max = 255) String gsLayerGroupName, Integer sortOrder,
            Boolean defaultVisible, Boolean active, Boolean syncToGeoServer, List<MemberRequest> members) {
    }

    public record LayerGroupMemberResponse(Long id, Long mapLayerId, String layerCode, String layerLabel,
            String wmsLayers, int sortOrder, String styleName) {
    }

    public record LayerGroupResponse(Long id, Long gisServerId, String code, String label, String gsLayerGroupName,
            int sortOrder, boolean defaultVisible, boolean active, Instant createdAt, Instant updatedAt,
            List<LayerGroupMemberResponse> members) {
    }

    public record StyleUploadRequest(@NotNull Long gisServerId, @NotBlank String name, String workspace,
            @NotBlank String sldXml) {
    }

    public record StyleResponse(Long id, Long gisServerId, String name, String workspace, Instant createdAt) {
        static StyleResponse of(GsStyle style) {
            return new StyleResponse(style.getId(), style.getGisServerId(), style.getName(), style.getWorkspace(),
                    style.getCreatedAt());
        }
    }
}
