package com.gis.warplan.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

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
        return nodeTypes.findAll(Sort.by(Sort.Order.asc("name").ignoreCase()));
    }

    @Transactional(readOnly = true)
    public NodeType get(Long id) {
        return nodeTypes.findById(id).orElseThrow(() -> new NotFoundException("Node type not found."));
    }

    @Transactional
    public NodeType create(String code, String name, String description, String iconPath,
            Map<String, Object> properties) {
        String normalized = code.trim().toUpperCase(Locale.ROOT);
        if (nodeTypes.findByCode(normalized).isPresent()) {
            throw new ConflictException("A node type with this code already exists.");
        }
        Map<String, Object> stored = NodeTypeRules.normalize(properties, normalized, knownCodes(null));
        checkIcon(iconPath);
        NodeType type = new NodeType();
        type.setCode(normalized);
        type.setName(name.trim());
        type.setDescription(blankToNull(description));
        type.setIconPath(blankToNull(iconPath));
        type.setProperties(stored);
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
        String normalized = code.trim().toUpperCase(Locale.ROOT);
        nodeTypes.findByCode(normalized).filter(other -> !other.getId().equals(id)).ifPresent(other -> {
            throw new ConflictException("A node type with this code already exists.");
        });
        boolean catalogueOwned = seeded(type);
        Map<String, Object> stored = catalogueOwned ? null : NodeTypeRules.normalize(properties, normalized, knownCodes(id));
        checkIcon(iconPath);
        type.setName(name.trim());
        type.setDescription(blankToNull(description));
        type.setIconPath(blankToNull(iconPath));
        if (!catalogueOwned) {
            String previous = type.getCode();
            type.setCode(normalized);
            type.setProperties(stored);
            if (!previous.equals(normalized)) {
                retargetParentCode(previous, normalized);
            }
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
        List<String> holders = parentHolders(type.getCode(), id);
        if (!holders.isEmpty()) {
            throw new ConflictException(holderMessage(holders));
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

    private Set<String> knownCodes(Long exceptId) {
        Set<String> codes = new HashSet<>();
        for (NodeType type : nodeTypes.findAll()) {
            if (exceptId != null && exceptId.equals(type.getId())) {
                continue;
            }
            if (type.getCode() != null) {
                codes.add(type.getCode().toUpperCase(Locale.ROOT));
            }
        }
        return codes;
    }

    /** Other types keep working after a code rename. Seeded catalogues are unchanged because their codes cannot change. */
    private void retargetParentCode(String previous, String normalized) {
        for (NodeType other : nodeTypes.findAll()) {
            Map<String, Object> properties = other.getProperties();
            if (properties == null || !(properties.get("parents") instanceof List<?> parents)) {
                continue;
            }
            boolean hit = false;
            List<String> next = new ArrayList<>();
            for (Object item : parents) {
                String code = String.valueOf(item).trim().toUpperCase(Locale.ROOT);
                if (code.equals(previous)) {
                    hit = true;
                    code = normalized;
                }
                if (!code.isEmpty() && !next.contains(code)) {
                    next.add(code);
                }
            }
            if (!hit) {
                continue;
            }
            Map<String, Object> copy = new LinkedHashMap<>(properties);
            if (next.isEmpty()) {
                copy.remove("parents");
            } else {
                copy.put("parents", next);
            }
            other.setProperties(copy);
            nodeTypes.save(other);
        }
    }

    private List<String> parentHolders(String code, Long exceptId) {
        List<String> names = new ArrayList<>();
        for (NodeType other : nodeTypes.findAll()) {
            if (other.getId().equals(exceptId)) {
                continue;
            }
            Map<String, Object> properties = other.getProperties();
            if (properties != null && properties.get("parents") instanceof List<?> parents
                    && parents.stream().anyMatch(item -> code.equalsIgnoreCase(String.valueOf(item)))) {
                names.add(other.getName());
            }
        }
        return names;
    }

    private static String holderMessage(List<String> holders) {
        if (holders.size() == 1) {
            return holders.get(0) + " still lists this type as an allowed parent. Edit that node type first.";
        }
        return String.join(", ", holders) + " still list this type as an allowed parent. Edit those node types first.";
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
