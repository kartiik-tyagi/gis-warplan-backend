package com.gis.warplan.service;

import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gis.warplan.domain.NodeType;
import com.gis.warplan.domain.PlayingOrbat;
import com.gis.warplan.domain.StandardOrbat;
import com.gis.warplan.repository.NodeTypeRepository;
import com.gis.warplan.repository.PlayingOrbatRepository;
import com.gis.warplan.repository.StandardOrbatRepository;
import com.gis.warplan.web.ConflictException;
import com.gis.warplan.web.NotFoundException;

@Service
public class NodeTypeService {

    private static final Logger log = LogManager.getLogger(NodeTypeService.class);

    private final NodeTypeRepository nodeTypes;
    private final StandardOrbatRepository standardOrbats;
    private final PlayingOrbatRepository playingOrbats;
    private final IconService icons;
    private final AccessService access;
    private final AuditService audit;

    public NodeTypeService(NodeTypeRepository nodeTypes, StandardOrbatRepository standardOrbats,
            PlayingOrbatRepository playingOrbats, IconService icons, AccessService access, AuditService audit) {
        this.nodeTypes = nodeTypes;
        this.standardOrbats = standardOrbats;
        this.playingOrbats = playingOrbats;
        this.icons = icons;
        this.access = access;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<NodeType> list() {
        return nodeTypes.findAll(Sort.by("id"));
    }

    @Transactional(readOnly = true)
    public NodeType get(Long id) {
        return nodeTypes.findById(id).orElseThrow(() -> new NotFoundException("Node type not found."));
    }

    @Transactional
    public NodeType create(String code, String name, String description, String iconPath,
            Map<String, Object> properties) {
        String normalized = code.trim().toUpperCase();
        if (nodeTypes.findByCode(normalized).isPresent()) {
            throw new ConflictException("A node type with this code already exists.");
        }
        checkIcon(iconPath);
        NodeType type = new NodeType();
        type.setCode(normalized);
        type.setName(name.trim());
        type.setDescription(blankToNull(description));
        type.setIconPath(blankToNull(iconPath));
        type.setProperties(properties);
        access.currentPrincipal().ifPresent(principal -> type.setCreatedBy(principal.userId()));
        NodeType saved = nodeTypes.save(type);
        log.info("node_type.created id={} code={}", saved.getId(), saved.getCode());
        audit.record("CREATE", "NodeType", saved.getId(), "Node type " + saved.getCode() + " created", Map.of());
        return saved;
    }

    @Transactional
    public NodeType update(Long id, String code, String name, String description, String iconPath,
            Map<String, Object> properties) {
        NodeType type = get(id);
        String normalized = code.trim().toUpperCase();
        nodeTypes.findByCode(normalized).filter(other -> !other.getId().equals(id)).ifPresent(other -> {
            throw new ConflictException("A node type with this code already exists.");
        });
        checkIcon(iconPath);
        type.setName(name.trim());
        type.setDescription(blankToNull(description));
        type.setIconPath(blankToNull(iconPath));
        if (seeded(type)) {
            // The catalogue owns the code, parents and attributes. The screen may change the name and the icon.
        } else {
            type.setCode(normalized);
            type.setProperties(properties);
        }
        access.currentPrincipal().ifPresent(principal -> type.setUpdatedBy(principal.userId()));
        NodeType saved = nodeTypes.save(type);
        log.info("node_type.updated id={} code={}", saved.getId(), saved.getCode());
        audit.record("UPDATE", "NodeType", saved.getId(), "Node type " + saved.getCode() + " updated", Map.of());
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        NodeType type = get(id);
        if (seeded(type)) {
            throw new ConflictException("A seeded node type cannot be deleted.");
        }
        if (inUse(id)) {
            throw new ConflictException("The node type is used by an ORBAT.");
        }
        nodeTypes.delete(type);
        log.info("node_type.deleted id={} code={}", id, type.getCode());
        audit.record("DELETE", "NodeType", id, "Node type " + type.getCode() + " deleted", Map.of());
    }

    private boolean inUse(Long id) {
        for (StandardOrbat orbat : standardOrbats.findAll()) {
            if (usesType(orbat.getOrbatJson(), id)) {
                return true;
            }
        }
        for (PlayingOrbat orbat : playingOrbats.findAll()) {
            if (usesType(orbat.getOrbatJson(), id)) {
                return true;
            }
        }
        return false;
    }

    private static boolean usesType(Map<String, Object> orbatJson, Long id) {
        return OrbatSupport.nodesOf(orbatJson).stream()
                .anyMatch(node -> id.equals(OrbatSupport.asLong(node.get("nodeTypeId"))));
    }

    private static boolean seeded(NodeType type) {
        return type.getProperties() != null && Boolean.TRUE.equals(type.getProperties().get("seeded"));
    }

    private void checkIcon(String iconPath) {
        if (iconPath != null && !iconPath.isBlank()) {
            icons.read(iconPath);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
