package com.drdo.jayant.service;

import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.drdo.jayant.domain.NodeType;
import com.drdo.jayant.domain.PlayingOrbat;
import com.drdo.jayant.domain.StandardOrbat;
import com.drdo.jayant.repository.NodeTypeRepository;
import com.drdo.jayant.repository.PlayingOrbatRepository;
import com.drdo.jayant.repository.StandardOrbatRepository;
import com.drdo.jayant.web.ConflictException;
import com.drdo.jayant.web.NotFoundException;

@Service
public class NodeTypeService {

    private final NodeTypeRepository nodeTypes;
    private final StandardOrbatRepository standardOrbats;
    private final PlayingOrbatRepository playingOrbats;
    private final IconService icons;
    private final AuditService audit;

    public NodeTypeService(NodeTypeRepository nodeTypes, StandardOrbatRepository standardOrbats,
            PlayingOrbatRepository playingOrbats, IconService icons, AuditService audit) {
        this.nodeTypes = nodeTypes;
        this.standardOrbats = standardOrbats;
        this.playingOrbats = playingOrbats;
        this.icons = icons;
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
    public NodeType create(String code, String name, String iconPath, Map<String, Object> properties) {
        String normalized = code.trim().toUpperCase();
        if (nodeTypes.findByCode(normalized).isPresent()) {
            throw new ConflictException("A node type with this code already exists.");
        }
        checkIcon(iconPath);
        NodeType type = new NodeType();
        type.setCode(normalized);
        type.setName(name.trim());
        type.setIconPath(blankToNull(iconPath));
        type.setProperties(properties);
        NodeType saved = nodeTypes.save(type);
        audit.record("CREATE", "NodeType", saved.getId(), "Node type " + saved.getCode() + " created", Map.of());
        return saved;
    }

    @Transactional
    public NodeType update(Long id, String code, String name, String iconPath, Map<String, Object> properties) {
        NodeType type = get(id);
        String normalized = code.trim().toUpperCase();
        nodeTypes.findByCode(normalized).filter(other -> !other.getId().equals(id)).ifPresent(other -> {
            throw new ConflictException("A node type with this code already exists.");
        });
        checkIcon(iconPath);
        type.setCode(normalized);
        type.setName(name.trim());
        type.setIconPath(blankToNull(iconPath));
        type.setProperties(properties);
        NodeType saved = nodeTypes.save(type);
        audit.record("UPDATE", "NodeType", saved.getId(), "Node type " + saved.getCode() + " updated", Map.of());
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        NodeType type = get(id);
        if (inUse(id)) {
            throw new ConflictException("The node type is used by an ORBAT.");
        }
        nodeTypes.delete(type);
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

    private void checkIcon(String iconPath) {
        if (iconPath != null && !iconPath.isBlank()) {
            icons.read(iconPath);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
