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
import org.springframework.web.bind.annotation.RestController;

import com.gis.warplan.domain.FunctionalityCodes;
import com.gis.warplan.domain.NodeType;
import com.gis.warplan.service.AccessService;
import com.gis.warplan.service.NodeTypeService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Node types are read by any signed-in user (the map needs their icons); changes need MANAGE_NODE_TYPES. */
@RestController
@RequestMapping("/api/v1/node-types")
public class NodeTypeController {

    private final NodeTypeService nodeTypes;
    private final AccessService access;

    public NodeTypeController(NodeTypeService nodeTypes, AccessService access) {
        this.nodeTypes = nodeTypes;
        this.access = access;
    }

    @GetMapping
    public List<NodeTypeResponse> list() {
        access.requirePrincipal();
        return nodeTypes.list().stream().map(NodeTypeResponse::of).toList();
    }

    @GetMapping("/{id}")
    public NodeTypeResponse get(@PathVariable Long id) {
        access.requirePrincipal();
        return NodeTypeResponse.of(nodeTypes.get(id));
    }

    @PostMapping
    public ResponseEntity<NodeTypeResponse> create(@Valid @RequestBody NodeTypeRequest request) {
        access.requireFunctionality(FunctionalityCodes.MANAGE_NODE_TYPES);
        NodeType type = nodeTypes.create(request.code(), request.name(), request.description(), request.iconPath(),
                request.properties());
        return ResponseEntity.status(HttpStatus.CREATED).body(NodeTypeResponse.of(type));
    }

    @PutMapping("/{id}")
    public NodeTypeResponse update(@PathVariable Long id, @Valid @RequestBody NodeTypeRequest request) {
        access.requireFunctionality(FunctionalityCodes.MANAGE_NODE_TYPES);
        return NodeTypeResponse.of(nodeTypes.update(id, request.code(), request.name(), request.description(),
                request.iconPath(), request.properties()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        access.requireFunctionality(FunctionalityCodes.MANAGE_NODE_TYPES);
        nodeTypes.delete(id);
        return ResponseEntity.noContent().build();
    }

    public record NodeTypeRequest(
            @NotBlank @Size(max = 60) @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_]*$") String code,
            @NotBlank @Size(max = 120) String name,
            @Size(max = 500) String description,
            @Size(max = 300) String iconPath,
            Map<String, Object> properties) {
    }

    public record NodeTypeResponse(Long id, String code, String name, String description, String iconPath,
            Map<String, Object> properties, Instant createdAt, Instant updatedAt, Long createdBy, Long updatedBy) {

        static NodeTypeResponse of(NodeType type) {
            return new NodeTypeResponse(type.getId(), type.getCode(), type.getName(), type.getDescription(),
                    type.getIconPath(), type.getProperties(), type.getCreatedAt(), type.getUpdatedAt(),
                    type.getCreatedBy(), type.getUpdatedBy());
        }
    }
}
